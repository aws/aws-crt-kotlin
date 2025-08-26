/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.util

import aws.sdk.kotlin.crt.CrtTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DigestTest : CrtTest() {
    @Test
    fun testSha256() {
        val buffer = "I don't know half of you half as well as I should like; and I like less than half of you half as well as you deserve.".encodeToByteArray()
        val raw = Digest.sha256(buffer)

        val expected = "96d3145b660d51343b4f49684cbd4a11bf19c11d388bf0f16d396537637e0dd7"
        val actual = Digest.hex(raw)
        assertEquals(expected, actual)
    }
}
