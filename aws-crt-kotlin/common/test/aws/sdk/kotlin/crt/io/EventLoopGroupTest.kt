/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.io

import aws.sdk.kotlin.crt.CrtTest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class EventLoopGroupTest : CrtTest() {
    @Test
    fun createDestroy() = runTest {
        val elg = EventLoopGroup()
        elg.close()
    }
}
