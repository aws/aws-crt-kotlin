/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.util

import aws.sdk.kotlin.crt.CrtRuntimeException
import kotlinx.cinterop.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/**
 * Drives [NativeLifecycle] with a fake native constructor, to reach the creation-failure paths real CRT constructors
 * can't be made to take on demand
 */
@OptIn(ExperimentalForeignApi::class)
class NativeLifecycleTest {
    private val fakeObject = nativeHeap.alloc<ByteVar>()

    @AfterTest
    fun freeFakeObject() {
        nativeHeap.free(fakeObject)
    }

    private fun checkRefs(block: () -> Unit) {
        val refs = ShutdownRefTracker.start()
        try {
            block()
            assertEquals(0, refs.count, "shutdown StableRefs outstanding")
        } finally {
            ShutdownRefTracker.stop()
        }
    }

    private fun failingLifecycle(create: (ShutdownCompleteCallback, COpaquePointer) -> CPointer<ByteVar>?) = NativeLifecycle(
        "fake_new",
        release = { fail("release called for an object that failed to create") },
        create,
    )

    // aws_http_connection_manager_new does this when it fails after storing the shutdown callback
    @Test
    fun callbackThenNullDisposesOnce() = checkRefs {
        assertFailsWith<CrtRuntimeException> {
            failingLifecycle { cb, userData ->
                cb(userData)
                null
            }
        }
    }

    @Test
    fun nullWithoutCallbackDisposes() = checkRefs {
        assertFailsWith<CrtRuntimeException> {
            failingLifecycle { _, _ -> null }
        }
    }

    @Test
    fun callbackThenThrowDisposesOnce() = checkRefs {
        assertFailsWith<IllegalStateException> {
            failingLifecycle { cb, userData ->
                cb(userData)
                error("boom")
            }
        }
    }

    @Test
    fun throwWithoutCallbackDisposes() = checkRefs {
        assertFailsWith<IllegalStateException> {
            failingLifecycle { _, _ -> error("boom") }
        }
    }

    @Test
    fun closeReleasesOnce() = checkRefs {
        lateinit var callback: ShutdownCompleteCallback
        lateinit var callbackUserData: COpaquePointer
        var releases = 0

        // Mirrors HostResolver and ClientBootstrap, whose release can invoke the shutdown callback synchronously
        val lifecycle = NativeLifecycle(
            "fake_new",
            release = {
                releases++
                callback(callbackUserData)
            },
        ) { cb, userData ->
            callback = cb
            callbackUserData = userData
            fakeObject.ptr
        }

        assertTrue(lifecycle.close())
        assertFalse(lifecycle.close())
        assertEquals(1, releases)

        runBlocking {
            withTimeout(5.seconds) { lifecycle.awaitShutdown() }
        }
    }
}
