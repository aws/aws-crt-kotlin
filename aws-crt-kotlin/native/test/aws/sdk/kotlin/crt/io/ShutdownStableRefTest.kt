/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.io

import aws.sdk.kotlin.crt.AsyncShutdown
import aws.sdk.kotlin.crt.Closeable
import aws.sdk.kotlin.crt.http.HttpClientConnectionManager
import aws.sdk.kotlin.crt.http.HttpClientConnectionManagerOptions
import aws.sdk.kotlin.crt.util.ShutdownRefTracker
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val ITERATIONS = 100

/**
 * Regression tests for https://github.com/aws/aws-crt-kotlin/issues/319. The shutdown callback may run on a CRT thread
 * after `close()` returns, so the StableRef it dereferences must stay alive until the callback disposes it.
 *
 * Each test counts the refs created during it with [ShutdownRefTracker]: a leaked ref leaves the count above 0, and a
 * ref disposed twice takes it below.
 */
class ShutdownStableRefTest {
    // runBlocking rather than runTest: shutdown is signaled from a CRT thread, and virtual time would fire the timeouts
    // immediately
    private fun checkRefs(block: suspend () -> Unit) = runBlocking {
        val refs = ShutdownRefTracker.start()
        try {
            block()
            // Owned children, and wrappers nobody awaited, may still be shutting down. On timeout, fall through so the
            // assertion reports how many refs leaked.
            withTimeoutOrNull(5.seconds) {
                while (refs.count > 0) delay(10.milliseconds)
            }
            assertEquals(0, refs.count, "shutdown StableRefs outstanding")
        } finally {
            ShutdownRefTracker.stop()
        }
    }

    private fun <T> closeThenAwait(create: () -> T) where T : Closeable, T : AsyncShutdown = checkRefs {
        repeat(ITERATIONS) {
            val resource = create()
            resource.close()
            withTimeout(5.seconds) { resource.waitForShutdown() }
        }
    }

    @Test
    fun eventLoopGroupCloseThenAwait() = closeThenAwait { EventLoopGroup(1) }

    @Test
    fun hostResolverCloseThenAwait() = closeThenAwait { HostResolver() }

    @Test
    fun clientBootstrapCloseThenAwait() = closeThenAwait { ClientBootstrap() }

    @Test
    fun connectionManagerCloseThenAwait() = withBootstrap { bootstrap ->
        closeThenAwait { HttpClientConnectionManager(managerOptions(bootstrap, "http://localhost:8080")) }
    }

    // The wrapper (and so the channel) is unreachable once close() returns, so only the StableRef keeps the channel
    // alive for the shutdown callback. Disposing it early lets GC free the channel before the callback dereferences it.
    @OptIn(NativeRuntimeApi::class)
    private fun closeUnreferenced(create: () -> Closeable) = checkRefs {
        repeat(ITERATIONS) {
            create().close()
            GC.collect()
        }
    }

    @Test
    fun eventLoopGroupCloseUnreferenced() = closeUnreferenced { EventLoopGroup(1) }

    @Test
    fun hostResolverCloseUnreferenced() = closeUnreferenced { HostResolver() }

    @Test
    fun clientBootstrapCloseUnreferenced() = closeUnreferenced { ClientBootstrap() }

    @Test
    fun connectionManagerCloseUnreferenced() = withBootstrap { bootstrap ->
        closeUnreferenced { HttpClientConnectionManager(managerOptions(bootstrap, "http://localhost:8080")) }
    }

    // NativeLifecycleTest checks the release itself happens once; this checks owned children aren't closed twice
    @Test
    fun doubleCloseIsNoOp() = checkRefs {
        val elg = EventLoopGroup(1)
        val hr = HostResolver(elg)
        val bootstrap = ClientBootstrap(elg, hr)

        closeTwiceThenAwait(bootstrap)
        closeTwiceThenAwait(hr)
        closeTwiceThenAwait(elg)
    }

    private suspend fun <T> closeTwiceThenAwait(resource: T) where T : Closeable, T : AsyncShutdown {
        resource.close()
        resource.close()
        withTimeout(5.seconds) { resource.waitForShutdown() }
    }

    // https without a TlsContext throws inside NativeLifecycle's create block, before the native constructor runs
    @Test
    fun failedCreateDisposesRefOnce() = withBootstrap { bootstrap ->
        checkRefs {
            assertFailsWith<IllegalArgumentException> {
                HttpClientConnectionManager(managerOptions(bootstrap, "https://localhost:8443"))
            }
        }
    }

    private fun withBootstrap(block: (ClientBootstrap) -> Unit) {
        val elg = EventLoopGroup(1)
        val hr = HostResolver(elg)
        val bootstrap = ClientBootstrap(elg, hr)
        try {
            block(bootstrap)
        } finally {
            bootstrap.close()
            hr.close()
            elg.close()
            runBlocking {
                withTimeout(5.seconds) {
                    bootstrap.waitForShutdown()
                    hr.waitForShutdown()
                    elg.waitForShutdown()
                }
            }
        }
    }

    private fun managerOptions(bootstrap: ClientBootstrap, url: String) = HttpClientConnectionManagerOptions.build {
        uri = Uri.parse(url)
        clientBootstrap = bootstrap
        socketOptions = SocketOptions()
    }
}
