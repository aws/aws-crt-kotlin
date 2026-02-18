/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.http

import aws.sdk.kotlin.crt.CrtTest
import aws.sdk.kotlin.crt.io.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.Protocol
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Http2StreamManagerTest : CrtTest() {

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
    fun testAcquireStream() = runBlocking {
        mockServer.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("Hello from stream manager")
                .build()
        )

        val uri = Uri.parse("https://localhost:${mockServer.port}")

        val elg = EventLoopGroup()
        val hr = HostResolver(elg)
        val clientBootstrap = ClientBootstrap(elg, hr)

        val tlsContext = TlsContext(TlsContextOptions.build {
            alpn = "h2;http/1.1"
            verifyPeer = false
        })

        try {
            val streamManager = Http2StreamManager(Http2StreamManagerOptions.build {
                connectionManagerOptions = HttpClientConnectionManagerOptions.build {
                    this.uri = uri
                    this.clientBootstrap = clientBootstrap
                    this.socketOptions = SocketOptions()
                    this.tlsContext = tlsContext
                    this.maxConnections = 2
                }
                idealConcurrentStreamsPerConnection = 100
            })

            try {
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

                val stream = streamManager.acquireStream(request, handler)
                stream.activate()

                val statusCode = responseFuture.get()
                val body = bodyFuture.get()

                assertEquals(200, statusCode)
                assertEquals("Hello from stream manager", body)

                val recordedRequest = mockServer.takeRequest()
                assertEquals("GET", recordedRequest.method)
                assertEquals(recordedRequest.requestLine.contains("GET / HTTP/2"), true)
            } finally {
                streamManager.close()
                streamManager.waitForShutdown()
            }
        } finally {
            tlsContext.close()
            clientBootstrap.close()
            hr.close()
            elg.close()
        }
    }

    @Test
    fun testConcurrentStreams() = runBlocking {
        val numRequests = 10

        repeat(numRequests) {
            mockServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body("Response $it")
                    .build()
            )
        }

        val uri = Uri.parse("https://localhost:${mockServer.port}")

        val elg = EventLoopGroup()
        val hr = HostResolver(elg)
        val clientBootstrap = ClientBootstrap(elg, hr)

        val tlsContext = TlsContext(TlsContextOptions.build {
            alpn = "h2;http/1.1"
            verifyPeer = false
        })

        try {
            val streamManager = Http2StreamManager(Http2StreamManagerOptions.build {
                connectionManagerOptions = HttpClientConnectionManagerOptions.build {
                    this.uri = uri
                    this.clientBootstrap = clientBootstrap
                    this.socketOptions = SocketOptions()
                    this.tlsContext = tlsContext
                    this.maxConnections = 1
                }
                idealConcurrentStreamsPerConnection = 100
            })

            try {
                val requests = (0 until numRequests).map { idx ->
                    async {
                        val request = Http2Request.build {
                            method = "GET"
                            encodedPath = "/request-$idx"
                            headers {
                                append(":method", "GET")
                                append(":path", "/request-$idx")
                                append(":scheme", "https")
                                append(":authority", "localhost:${mockServer.port}")
                            }
                        }

                        val responseFuture = CompletableFuture<Int>()

                        val handler = object : HttpStreamResponseHandler {
                            override fun onResponseHeaders(
                                stream: HttpStreamBase,
                                responseStatusCode: Int,
                                blockType: Int,
                                nextHeaders: List<HttpHeader>?,
                            ) {
                                responseFuture.complete(responseStatusCode)
                            }

                            override fun onResponseComplete(stream: HttpStreamBase, errorCode: Int) {
                                stream.close()
                            }
                        }

                        val stream = streamManager.acquireStream(request, handler)
                        stream.activate()
                        responseFuture.get()
                    }
                }

                val results = requests.awaitAll()
                assertTrue(results.all { it == 200 }, "All requests should succeed")
                assertEquals(numRequests, results.size)
            } finally {
                streamManager.close()
                streamManager.waitForShutdown()
            }
        } finally {
            tlsContext.close()
            clientBootstrap.close()
            hr.close()
            elg.close()
        }
    }
}
