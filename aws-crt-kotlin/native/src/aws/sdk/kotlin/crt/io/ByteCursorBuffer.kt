/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.crt.io

import kotlinx.cinterop.*
import libcrt.aws_byte_cursor
import platform.posix.memcpy

/**
 * A [Buffer] instance backed by an aws_byte_cursor
 */
internal class ByteCursorBuffer(
    private val cursor: CPointer<aws_byte_cursor>,
) : Buffer {

    override val len: Int
        get() = cursor.pointed.len.convert()

    override fun copyTo(dest: ByteArray, offset: Int): Int {
        val ptr = cursor.pointed.ptr
        requireNotNull(ptr) { "cursor buffer cannot be null" }
        return copyTo(dest, ptr, len, offset)
    }

    override fun readAll(): ByteArray {
        val buffer = ByteArray(len)
        copyTo(buffer)
        return buffer
    }
}
private fun copyTo(dest: ByteArray, src: CValuesRef<*>, len: Int, offset: Int): Int {
    require(offset >= 0) { "offset must be >= 0" }
    require((dest.size - offset) >= len) { "destination ByteArray is too small" }

    val read = minOf(len, dest.size - offset)
    dest.usePinned {
        memcpy(it.addressOf(offset), src, read.convert())
    }

    return read
}
