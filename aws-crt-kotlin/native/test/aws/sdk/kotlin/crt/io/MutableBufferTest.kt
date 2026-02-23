/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.crt.io

import aws.sdk.kotlin.crt.Allocator
import aws.sdk.kotlin.crt.CrtTest
import kotlinx.cinterop.*
import libcrt.aws_byte_buf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class MutableBufferTest : CrtTest() {
    @Test
    fun testSimpleWrite() {
        val capacity = 10
        val dest = ByteArray(capacity)
        val buffer = MutableBuffer.of(dest)
        assertEquals(capacity, buffer.writeRemaining)

        val data = "Hello!"
        buffer.write(data.encodeToByteArray())
        assertEquals(capacity - data.length, buffer.writeRemaining)

        assertContentEquals(data.encodeToByteArray(), dest.copyOfRange(0, data.length))

        buffer.close()
    }

    @Test
    fun testWriteFillingBuffer() {
        val capacity = 5
        val dest = ByteArray(capacity)
        val buffer = MutableBuffer.of(dest)
        assertEquals(capacity, buffer.writeRemaining)

        val data = "Hello, this data won't fit!"
        assertEquals(5, buffer.write(data.encodeToByteArray()))
        buffer.close()
    }

    @Test
    fun testMultipleWrites() {
        memScoped {
            val buffer = ByteArray(34)
            buffer.usePinned { pinned ->
                val byteBuf = alloc<aws_byte_buf>()
                byteBuf.allocator = Allocator.Default.allocator
                byteBuf.buffer = pinned.addressOf(0).reinterpret()
                byteBuf.capacity = buffer.size.convert()
                byteBuf.len = 0.convert()

                val mutBuf = MutableBuffer(byteBuf.ptr)

                assertEquals(buffer.size, mutBuf.writeRemaining)

                val src = "a tay is a hammer;".encodeToByteArray()
                val written1 = mutBuf.write(src)
                assertEquals(src.size, written1)
                assertEquals(16, mutBuf.writeRemaining)

                val src2 = " a lep is a ball".encodeToByteArray()
                val written2 = mutBuf.write(src2)
                assertEquals(src2.size, written2)
                // should be filled
                assertEquals(0, mutBuf.writeRemaining)

                // additional writes should now fail
                val src3 = "bonus points if you know what I'm talking about".encodeToByteArray()
                val written3 = mutBuf.write(src3)
                assertEquals(0, written3)
            }

            val actual = buffer.decodeToString()
            assertEquals("a tay is a hammer; a lep is a ball", actual)
        }
    }

    @Test
    fun testEmptyByteArray() {
        val dest = ByteArray(0)
        val buffer = MutableBuffer.of(dest)

        assertEquals(0, buffer.writeRemaining)

        val written = buffer.write(byteArrayOf(1, 2, 3))
        assertEquals(0, written)

        buffer.close()
    }

    @Test
    fun testEmptyWrite() {
        val dest = ByteArray(16)
        val buffer = MutableBuffer.of(dest)

        val written = buffer.write(byteArrayOf())
        assertEquals(0, written)

        buffer.close()
    }
}
