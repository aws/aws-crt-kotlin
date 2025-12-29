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
import kotlin.test.*
import kotlin.time.measureTime

class HttpClientConnectionTest : CrtTest() {
    @Test
    fun testDefaults(): Unit = runBlocking {
        val uri = Uri.parse("https://aws-crt-test-stuff.s3.amazonaws.com")
        val socketOpts = SocketOptions()
        val elg = EventLoopGroup()
        val hr = HostResolver(elg)
        val clientBootstrap = ClientBootstrap(elg, hr)
        val tlsContext = TlsContext()

        val httpConnOpts = HttpClientConnectionManagerOptions.build {
            this.uri = uri
            this.clientBootstrap = clientBootstrap
            this.tlsContext = tlsContext
            this.socketOptions = socketOpts
        }

        val connPool = HttpClientConnectionManager(httpConnOpts)
        try {
            withTimeout(30_000) {
                println("acquiring connection")
                val conn = connPool.acquireConnection()
                println("conn acquired")
                conn.close()
            }
        } finally {
            println("closing pool")
            connPool.close()
            println("closing tls")
            tlsContext.close()
            println("closing bootstrap")
            clientBootstrap.close()
            println("closing hr")
            hr.close()
            println("closing elg")
            elg.close()
        }
        println("exiting test")
    }

    @Test
    fun testHttpConnection(): Unit = runBlocking {
        // S3
        assertConnect("https://aws-crt-test-stuff.s3.amazonaws.com")
        assertConnect("http://aws-crt-test-stuff.s3.amazonaws.com")
        assertConnect("http://aws-crt-test-stuff.s3.amazonaws.com:80")
        assertConnect("http://aws-crt-test-stuff.s3.amazonaws.com:443")
        assertConnect("https://aws-crt-test-stuff.s3.amazonaws.com:443")

        // KMS
        assertConnect("https://kms.us-east-1.amazonaws.com:443")
        assertConnect("https://kms-fips.us-east-1.amazonaws.com:443")
        assertConnect("https://kms.us-west-2.amazonaws.com:443")
        assertConnect("https://kms-fips.us-west-2.amazonaws.com:443")

        // bad ssl
        // assertConnect("https://rsa2048.badssl.com/")
        assertConnect("http://http.badssl.com/")
        assertConnectFails("https://expired.badssl.com/", "tls certificate is malformed or not correctly formatted")
        assertConnectFails("https://self-signed.badssl.com/", "tls certificate is malformed or not correctly formatted")
    }

    /**
     * Assert that an attempt to connect to the given [url] is successful
     */
    private suspend fun assertConnect(url: String) {
        try {
            println("assertConnect for $url")
            connectAllCiphers(url)
        } catch (ex: Exception) {
            fail("[$url]: ${ex.message}")
        }
    }

    /**
     * Assert that an attempt to connect to the given [url] fails with the given [exceptionMessage]
     */
    private suspend fun assertConnectFails(url: String, exceptionMessage: String) {
        val ex = assertFails { connectAllCiphers(url) }
        assertTrue(ex.message!!.contains(exceptionMessage), "Exception message \"${ex.message}\" does not contain expected \"$exceptionMessage\"")
    }

    /**
     * Connects to the given URL. On success no exception should occur
     * @throws TimeoutCancellationException if the connection fails to connect in the time allotted by [connTimeoutMs]
     */
    private suspend fun connect(
        url: String,
        clientBootstrap: ClientBootstrap,
        tlsContext: TlsContext,
        connTimeoutMs: Int = 30_000,
    ) {
        val uri = Uri.parse(url)
        val httpConnOpts = HttpClientConnectionManagerOptions.build {
            this.uri = uri
            this.clientBootstrap = clientBootstrap
            this.tlsContext = tlsContext
            this.socketOptions = SocketOptions()
        }
        HttpClientConnectionManager(httpConnOpts).use { pool ->
            withTimeout(connTimeoutMs.toLong()) {
                pool.acquireConnection().use { }
            }
        }
    }

    /**
     * Connect to the URL with all TLS ciphers supported. Throws an exception if the connection attempt fails for
     * any reason
     */
    private suspend fun connectAllCiphers(url: String) {
        withDefaultBootstrap { clientBootstrap ->
            TlsCipherPreference.values()
                .filter { TlsContextOptions.isCipherPreferenceSupported(it) }
                .forEach { pref ->
                    TlsContext.build {
                        tlsCipherPreference = pref
                        println("connecting to $url with $pref")
                    }.use { tlsContext ->
                        val elapsed = measureTime {
                            connect(url, clientBootstrap, tlsContext)
                        }
                        println("connect took $elapsed: for $url; cipher: $pref")
                    }
                }
        }
    }

    /**
     * Run the given block with a ClientBootstrap instance
     */
    private suspend inline fun <reified T> withDefaultBootstrap(block: (ClientBootstrap) -> T): T {
        val elg = EventLoopGroup()
        val hr = HostResolver(elg)
        val clientBootstrap = ClientBootstrap(elg, hr)
        try {
            return block(clientBootstrap)
        } finally {
            clientBootstrap.close()
            hr.close()
            elg.close()
        }
    }
}
