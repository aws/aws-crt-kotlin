/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.http

import aws.sdk.kotlin.crt.CrtTest
import aws.sdk.kotlin.crt.io.*
import aws.sdk.kotlin.crt.use
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import software.amazon.awssdk.crt.http.HttpException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

/**
 * Tests TLS error handling using a local MockWebServer instead of external services (e.g., badssl.com).
 * Verifies that the CRT correctly rejects connections to servers with expired or self-signed certificates.
 */
class HttpClientConnectionTlsTest : CrtTest() {
    companion object {
        // Constant value derived from CRT source:
        // https://github.com/awslabs/aws-c-io/blob/b0df344ff036d144c177002fb78985f29d9d14a2/include/aws/io/io.h#L102
        const val AWS_IO_TLS_ERROR_NEGOTIATION_FAILURE = 1029
    }

    private lateinit var mockServer: MockWebServer

    @AfterEach
    fun teardown() {
        mockServer.close()
    }

    private fun startServer(cert: HeldCertificate) {
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(cert)
            .build()

        mockServer = MockWebServer()
        mockServer.useHttps(serverCertificates.sslSocketFactory())
        mockServer.enqueue(MockResponse.Builder().code(200).build())
        mockServer.start()
    }

    private suspend fun connectToMockServer() {
        val uri = Uri.parse("https://localhost:${mockServer.port}")
        val elg = EventLoopGroup()
        val hr = HostResolver(elg)
        val clientBootstrap = ClientBootstrap(elg, hr)
        val tlsContext = TlsContext()
        try {
            val opts = HttpClientConnectionManagerOptions.build {
                this.uri = uri
                this.clientBootstrap = clientBootstrap
                this.tlsContext = tlsContext
                this.socketOptions = SocketOptions()
            }
            HttpClientConnectionManager(opts).use { pool ->
                withTimeout(30.seconds) {
                    pool.acquireConnection().use { }
                }
            }
        } finally {
            tlsContext.close()
            clientBootstrap.close()
            hr.close()
            elg.close()
        }
    }

    @Test
    fun testExpiredCertificateRejected() = runBlocking {
        val expiredCert = HeldCertificate.Builder()
            .commonName("localhost")
            .addSubjectAlternativeName("localhost")
            .validityInterval(0L, 1L) // expired in 1970
            .build()

        startServer(expiredCert)

        val ex = assertFailsWith<HttpException> { connectToMockServer() }
        assertEquals(AWS_IO_TLS_ERROR_NEGOTIATION_FAILURE, ex.errorCode)
    }

    @Test
    fun testSelfSignedCertificateRejected() = runBlocking {
        val selfSignedCert = HeldCertificate.Builder()
            .commonName("localhost")
            .addSubjectAlternativeName("localhost")
            .build()

        startServer(selfSignedCert)

        val ex = assertFailsWith<HttpException> { connectToMockServer() }
        assertEquals(AWS_IO_TLS_ERROR_NEGOTIATION_FAILURE, ex.errorCode)
    }
}
