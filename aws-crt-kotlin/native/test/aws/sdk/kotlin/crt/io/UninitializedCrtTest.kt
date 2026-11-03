/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.io

import aws.sdk.kotlin.crt.CRT
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies that functions which may be called before any `WithCrt` instance exists initialize the CRT themselves.
 *
 * CRT initialization is process-wide and one-way, so each test must run alone in a fresh process. The build excludes
 * this class from the regular native test tasks and runs each test via its own dedicated test run instead.
 */
class UninitializedCrtTest {
    @Test
    fun testIsCipherSupported() {
        assertFalse(CRT.isInitialized, "CRT must not be initialized before this test runs")
        assertTrue(isCipherSupported(TlsCipherPreference.SYSTEM_DEFAULT))
        assertTrue(CRT.isInitialized)
    }

    @Test
    fun testIsAlpnSupported() {
        assertFalse(CRT.isInitialized, "CRT must not be initialized before this test runs")
        isAlpnSupported() // result is platform-dependent, only care that it doesn't crash
        assertTrue(CRT.isInitialized)
    }
}
