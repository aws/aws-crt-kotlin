/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.io

import aws.sdk.kotlin.crt.CrtTest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ClientBootstrapTest : CrtTest() {
    @Test
    fun createDestroy() = runTest {
        val elg = EventLoopGroup()
        val hr = HostResolver(elg)
        val bootstrap = ClientBootstrap(elg, hr)
        bootstrap.close()
        hr.close()
        elg.close()
    }
}
