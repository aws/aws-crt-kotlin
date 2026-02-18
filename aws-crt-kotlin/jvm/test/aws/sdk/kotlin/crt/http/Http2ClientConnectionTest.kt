/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.http

import aws.sdk.kotlin.crt.CrtTest
import aws.sdk.kotlin.crt.io.*
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Protocol
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Http2ClientConnectionTest : CrtTest() {
    private lateinit var mockServer: MockWebServer
    private lateinit var serverCert: HeldCertificate

    @BeforeEach
    fun setup() {
        serverCert = HeldCertificate.Builder()
            .commonName("localhost")
            .build()

        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(serverCert)
            .build()

        mockServer = MockWebServer()
        mockServer.useHttps(serverCertificates.sslSocketFactory())
        mockServer.protocols = listOf(Protocol.HTTP_2, Protocol.HTTP_1_1)
        mockServer.start()
    }

    @AfterEach
    fun teardown() {
        mockServer.close()
    }

    @Test
    fun testHttp2Request() = runBlocking {
        mockServer.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("Hello HTTP/2")
                .build(),
        )

        val uri = Uri.parse("https://localhost:${mockServer.port}")

        val elg = EventLoopGroup()
        val hr = HostResolver(elg)
        val clientBootstrap = ClientBootstrap(elg, hr)

        val tlsContext = TlsContext(
            TlsContextOptions.build {
                alpn = "h2;http/1.1"
                verifyPeer = false
            },
        )

        try {
            val connOptions = HttpClientConnectionManagerOptions.build {
                this.uri = uri
                this.clientBootstrap = clientBootstrap
                this.socketOptions = SocketOptions()
                this.tlsContext = tlsContext
                this.maxConnections = 1
            }

            val connManager = HttpClientConnectionManager(connOptions)
            try {
                val conn = connManager.acquireConnection()

                // Confirm we got an HTTP/2 connection
                assertTrue(conn is Http2ClientConnection, "Expected HTTP/2 connection but got ${conn::class.simpleName}")
                assertEquals(HttpVersion.HTTP_2, conn.version, "Connection version should be HTTP/2")

                val request = Http2Request.build {
                    method = "GET"
                    encodedPath = "/"
                    headers {
                        append(":method", "GET")
                        append(":path", "/")
                        append(":scheme", "https")
                        append(":authority", "localhost:${mockServer.port}")
                    }
                }

                val responseFuture = CompletableFuture<Int>()
                val bodyFuture = CompletableFuture<String>()
                val bodyBuilder = StringBuilder()

                val handler = object : HttpStreamResponseHandler {
                    override fun onResponseHeaders(
                        stream: HttpStreamBase,
                        responseStatusCode: Int,
                        blockType: Int,
                        nextHeaders: List<HttpHeader>?,
                    ) {
                        responseFuture.complete(responseStatusCode)
                    }

                    override fun onResponseBody(stream: HttpStreamBase, bodyBytesIn: Buffer): Int {
                        bodyBuilder.append(String(bodyBytesIn.readAll()))
                        return bodyBytesIn.len
                    }

                    override fun onResponseComplete(stream: HttpStreamBase, errorCode: Int) {
                        bodyFuture.complete(bodyBuilder.toString())
                        stream.close()
                    }
                }

                val stream = conn.makeRequest(request, handler)
                stream.activate()

                val statusCode = responseFuture.get()
                val body = bodyFuture.get()

                assertEquals(200, statusCode, "Expected 200 status code")
                assertEquals("Hello HTTP/2", body, "Expected response body")

                // Verify the server received the request
                val recordedRequest = mockServer.takeRequest()
                assertEquals("GET", recordedRequest.method)
                assertEquals(recordedRequest.requestLine.contains("GET / HTTP/2"), true)

                connManager.releaseConnection(conn)
            } finally {
                connManager.close()
            }
        } finally {
            tlsContext.close()
            clientBootstrap.close()
            hr.close()
            elg.close()
        }
    }
}
