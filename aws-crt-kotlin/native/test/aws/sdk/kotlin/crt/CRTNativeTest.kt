/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt

import kotlinx.cinterop.ExperimentalForeignApi
import libcrt.AWS_ERROR_HTTP_INVALID_PATH
import libcrt.AWS_ERROR_HTTP_STREAM_HAS_COMPLETED
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CRTNativeTest {
    @Test
    fun testInit() {
        CRT.initRuntime()
    }

    @Test
    fun testErrorString() {
        assertEquals("Success.", CRT.errorString(0))
        assertEquals("Out of memory.", CRT.errorString(1))
    }

    @Test
    fun testErrorName() {
        assertEquals("AWS_ERROR_SUCCESS", CRT.errorName(0))
        assertEquals("AWS_ERROR_OOM", CRT.errorName(1))
    }

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun testIsHttpErrorRetryable() {
        assertFalse(CRT.isHttpErrorRetryable(AWS_ERROR_HTTP_INVALID_PATH.toInt()))
        assertTrue(CRT.isHttpErrorRetryable(AWS_ERROR_HTTP_STREAM_HAS_COMPLETED.toInt()))
    }

    @Test
    fun testNoTracingMemoryUsage() {
        // Unconfigured memory tracing means this should always return zero
        assertEquals(0, CRT.nativeMemory())
    }

    // FIXME Add a test for traced memory usage
}
