/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.http

import aws.sdk.kotlin.crt.CrtTest
import aws.sdk.kotlin.crt.io.*
import aws.sdk.kotlin.crt.use
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import kotlin.test.assertNotNull

const val DEFAULT_CONNECT_TIMEOUT_MS: Int = 30_000

/**
 * (WIP) re-usable test fixture for HTTP request/response testing.
 */
abstract class HttpClientTest : CrtTest() {
    /**
     * Run the given block with a ClientBootstrap instance
     */
    suspend inline fun <reified T> withDefaultBootstrap(block: (ClientBootstrap) -> T): T {
        val elg = EventLoopGroup()
        val hr = HostResolver(elg)
        val clientBootstrap = ClientBootstrap(elg, hr)
        try {
            return block(clientBootstrap)
        } finally {
            clientBootstrap.close()
            hr.close()
            elg.close()

            clientBootstrap.waitForShutdown()
            hr.waitForShutdown()
            elg.waitForShutdown()
        }
    }

    /**
     * Connects to the given URL. On success no exception should occur
     * @throws TimeoutCancellationException if the connection fails to connect in the time allotted by [connTimeoutMs]
     */
    suspend fun connect(
        url: String,
        clientBootstrap: ClientBootstrap,
        tlsContext: TlsContext,
        socketOptions: SocketOptions? = null,
        connTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
        block: suspend (HttpClientConnection) -> Unit = {},
    ) {
        val uri = Uri.parse(url)
        val httpConnOpts = HttpClientConnectionManagerOptions.build {
            this.uri = uri
            this.clientBootstrap = clientBootstrap
            this.tlsContext = tlsContext
            this.socketOptions = socketOptions ?: SocketOptions()
        }
        val connMgr = HttpClientConnectionManager(httpConnOpts)
        connMgr.use { pool ->
            withTimeout(connTimeoutMs.toLong()) {
                pool.acquireConnection().use {
                    block(it)
                }
            }
        }
        connMgr.waitForShutdown()
    }

    /**
     * Simplified request/response handling. Round trip an http request to the given [url] with the
     * provided http [verb] and optional [body] as a string.
     */
    suspend fun roundTrip(url: String, verb: String = "GET", body: String? = null): HttpTestResponse {
        val responseHandler = HttpTestResponseHandler()
        var response: HttpTestResponse? = null
        withDefaultBootstrap { bootstrap ->
            TlsContext().use { tlsContext ->
                connect(url, bootstrap, tlsContext) { conn ->
                    val uri = Uri.parse(url)
                    val bodyBytes = body?.encodeToByteArray()

                    val request = HttpRequest.build {
                        method = verb
                        encodedPath = uri.path
                        headers.append("Host", uri.hostAndPort)
                        if (bodyBytes != null) {
                            headers.append("Content-Length", bodyBytes.size.toString())
                            this.body = HttpRequestBodyStream.fromByteArray(bodyBytes)
                        }
                    }

                    val stream = conn.makeRequest(request, responseHandler)
                    // NOTE: do not close the stream outside of the on_complete callback
                    // see: https://github.com/awslabs/aws-crt-java/issues/262
                    stream.activate()
                    response = responseHandler.waitForResponse()
                }
            }
        }

        return assertNotNull(response, "[$url]: http response was never set")
    }
}

data class HttpTestResponse(val statusCode: Int, val headers: Headers, val body: ByteArray? = null) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as HttpTestResponse

        if (statusCode != other.statusCode) return false
        if (headers != other.headers) return false
        if (body != null) {
            if (other.body == null) return false
            if (!body.contentEquals(other.body)) return false
        } else if (other.body != null) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = statusCode
        result = 31 * result + headers.hashCode()
        result = 31 * result + (body?.contentHashCode() ?: 0)
        return result
    }
}

private class HttpTestResponseHandler : HttpStreamResponseHandler {
    private val streamDone = Channel<Int>(1)
    private var statusCode: Int = 0
    private val headers = HeadersBuilder()
    private var body: ByteArray? = null

    override fun onResponseHeaders(
        stream: HttpStream,
        responseStatusCode: Int,
        blockType: Int,
        nextHeaders: List<HttpHeader>?,
    ) {
        // FIXME - getting the headers or body out of these callbacks in k/n is...difficult
        // k/n concurrency rules causes an immutability exception - we'd have to use something like DetachedObjectGraph to update the values
        statusCode = responseStatusCode
        nextHeaders?.forEach {
            headers.append(it.name, it.value)
        }
    }

    override fun onResponseBody(stream: HttpStream, bodyBytesIn: Buffer): Int {
        val incoming = bodyBytesIn.readAll()

        // not really concerned with how efficient this is right now...
        body = if (body == null) {
            incoming
        } else {
            body!! + incoming
        }

        return incoming.size
    }

    override fun onResponseComplete(stream: HttpStream, errorCode: Int) {
        stream.close()
        check(streamDone.trySend(errorCode).isSuccess)
        streamDone.close()
    }

    suspend fun waitForResponse(): HttpTestResponse {
        val errorCode = streamDone.receive()
        if (errorCode != 0) {
            throw HttpException(errorCode)
        }

        return HttpTestResponse(statusCode, headers.build(), body)
    }
}
