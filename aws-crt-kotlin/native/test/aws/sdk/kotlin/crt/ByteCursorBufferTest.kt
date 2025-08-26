/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.crt

import aws.sdk.kotlin.crt.io.ByteCursorBuffer
import aws.sdk.kotlin.crt.util.asAwsByteCursor
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import kotlin.test.Test
import kotlin.test.assertEquals

class ByteCursorBufferTest : CrtTest() {
    @Test
    fun testCopyTo() {
        val str = "Hello World"
        val dest = ByteArray(str.length)

        memScoped {
            str.encodeToByteArray().usePinned {
                val cursor = it.asAwsByteCursor()
                val ptr = cursor.getPointer(this)
                val buffer = ByteCursorBuffer(ptr)
                assertEquals(11, buffer.len)
                val read = buffer.copyTo(dest)
                assertEquals(11, read)
            }

            assertEquals("Hello World", dest.decodeToString())
        }
    }
}
