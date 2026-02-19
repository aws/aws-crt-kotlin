/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.http

import aws.sdk.kotlin.crt.CrtTest
import aws.sdk.kotlin.crt.io.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * E2E test for HTTP/2 connections using httpbin.org.
 */
class Http2Test : CrtTest() {

    @Test
    fun testHttp2GetRequest() = runBlocking {
        val uri = Uri.parse("https://httpbin.org/get")

        val elg = EventLoopGroup()
        val hr = HostResolver(elg)
        val clientBootstrap = ClientBootstrap(elg, hr)

        val tlsContext = TlsContext(
            TlsContextOptions.build {
                alpn = "h2;http/1.1"
                verifyPeer = true
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

                // Verify we got an HTTP/2 connection
                assertEquals(HttpVersion.HTTP_2, conn.version, "Expected HTTP/2 connection")

                val request = HttpRequest.build {
                    method = "GET"
                    encodedPath = "/get"
                    headers {
                        append(":method", "GET")
                        append(":path", "/get")
                        append(":scheme", "https")
                        append(":authority", "httpbin.org")
                        append("user-agent", "aws-crt-kotlin-test")
                    }
                }

                var responseStatus = 0
                val bodyBuilder = StringBuilder()
                val responseHeaders = mutableListOf<HttpHeader>()
                var streamCompleted = false

                val handler = object : HttpStreamResponseHandler {
                    override fun onResponseHeaders(
                        stream: HttpStream,
                        responseStatusCode: Int,
                        blockType: Int,
                        nextHeaders: List<HttpHeader>?,
                    ) {
                        responseStatus = responseStatusCode
                        nextHeaders?.let { responseHeaders.addAll(it) }
                    }

                    override fun onResponseBody(stream: HttpStream, bodyBytesIn: Buffer): Int {
                        bodyBuilder.append(bodyBytesIn.readAll().decodeToString())
                        return bodyBytesIn.len
                    }

                    override fun onResponseComplete(stream: HttpStream, errorCode: Int) {
                        streamCompleted = true
                        stream.close()
                    }
                }

                val stream = conn.makeRequest(request, handler)
                stream.activate()

                // Wait for completion
                var attempts = 0
                while (!streamCompleted && attempts < 100) {
                    delay(100)
                    attempts++
                }

                assertEquals(200, responseStatus, "Expected 200 status code")
                assertTrue(streamCompleted, "Stream should have completed")
                assertTrue(bodyBuilder.isNotEmpty(), "Response body should not be empty")

                // Verify HTTP/2 pseudo-headers in response
                val statusHeader = responseHeaders.find { it.name == ":status" }
                assertEquals("200", statusHeader?.value, "Expected :status pseudo-header with value 200")

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
