/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.util

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest

class OsVersionTest {
    @Test
    fun testOsInfo() = runTest {
        val version = osVersionFromKernel()
        assertNotNull(version)
    }
}
