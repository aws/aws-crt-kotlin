/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.crt

import aws.sdk.kotlin.crt.util.*
import kotlinx.cinterop.*
import libcrt.aws_byte_cursor
import kotlin.test.Test
import kotlin.test.assertEquals

class StringTests : CrtTest() {
    @Test
    fun testRoundTrip() {
        // kotlin -> aws and back
        val expected = "Neato burrito"
        val awsStr = expected.toAwsString()
        val actual = awsStr.toKString()
        awsStr.free()
        assertEquals(expected, actual)
    }

    @Test
    fun testAwsStringToCursor() {
        val expected = "Neato burrito"
        val awsStr = expected.toAwsString()
        val actual = awsStr.asAwsByteCursor()
        try {
            val asBytes = expected.encodeToByteArray()
            actual.useContents {
                val cursor = this
                assertEquals(asBytes.size, cursor.len.toInt())
                for (i in asBytes.indices) {
                    val byte: Byte = cursor.ptr!![i].toByte()
                    assertEquals(asBytes[i], byte)
                }
            }
        } finally {
            awsStr.free()
        }
    }

    @Test
    fun testByteCursorToKotlinString() {
        val expected = "Neato burrito"
        val awsStr = expected.toAwsString()
        val cursor = awsStr.asAwsByteCursor()
        try {
            val actual = cursor.toKString()
            assertEquals(expected, actual)
        } finally {
            awsStr.free()
        }
    }

    @Test
    fun testPinnedByteArrayToCursor() {
        val expected = "Neato burrito"
        val asBytes = expected.encodeToByteArray()
        asBytes.usePinned {
            val actual = it.asAwsByteCursor()
            assertCursorEquals(expected, actual)
        }
    }

    @Test
    fun testInitByteCursorFromCursor() {
        val expected = "Neato burrito"
        val awsStr = expected.toAwsString()
        val cursor = awsStr.asAwsByteCursor()
        val actual = cValue<aws_byte_cursor> { initFromCursor(cursor) }
        try {
            assertCursorEquals(expected, actual)
        } finally {
            awsStr.free()
        }
    }

    private fun assertCursorEquals(expected: ByteArray, cursor: CValue<aws_byte_cursor>) {
        cursor.useContents {
            val actual = this
            assertEquals(expected.size, actual.len.toInt())
            for (i in expected.indices) {
                val byte: Byte = actual.ptr!![i].toByte()
                assertEquals(expected[i], byte, "expected <${expected[i]}>, actual: <$byte>; index: $i")
            }
        }
    }

    private fun assertCursorEquals(expected: String, cursor: CValue<aws_byte_cursor>) = assertCursorEquals(expected.encodeToByteArray(), cursor)
}
