/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.io

import aws.sdk.kotlin.crt.CrtTest
import aws.sdk.kotlin.crt.use
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class HostResolverTest : CrtTest() {
    @Test
    fun createDestroy() = runTest {
        EventLoopGroup().use { elg ->
            val hr = HostResolver(elg)
            hr.close()
        }
    }
}
