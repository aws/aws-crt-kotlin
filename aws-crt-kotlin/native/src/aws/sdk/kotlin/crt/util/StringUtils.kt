/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.crt.util

import aws.sdk.kotlin.crt.Allocator
import aws.sdk.kotlin.crt.CrtRuntimeException
import kotlinx.cinterop.*
import libcrt.*

/**
 * Decode an aws_string as to a kotlin [String] assuming UTF-8 bytes
 * This does NOT free the aws_string instance
 */
public fun CPointer<aws_string>.toKString(): String? {
    val bytes = aws_string_c_str(this)
    return bytes?.toKString()
}

/**
 * Decode an aws_string as to a kotlin [String] assuming UTF-8 bytes
 * This does NOT free the aws_string instance
 */
public fun CPointerVar<aws_string>.toKString(): String? {
    val bytes = aws_string_c_str(this.value)
    return bytes?.toKString()
}

/**
 * Get a byte cursor from the current aws_string instance
 */
public fun CPointer<aws_string>.asAwsByteCursor(): CValue<aws_byte_cursor> = aws_byte_cursor_from_string(this)

/**
 * Free the aws_string instance
 */
public fun CPointer<aws_string>.free() {
    aws_string_destroy(this)
}

/**
 * Decode the kotlin [String] as an aws_string instance
 * Caller is responsible for freeing
 */
public fun String.toAwsString(): CPointer<aws_string> = aws_string_new_from_c_str(Allocator.Default, this) ?: throw CrtRuntimeException("aws_string_new_from_c_string($this)")

// NOTE - we can't use aws_byte_cursor_from_c_str() (which takes a Kotlin string). The way Kotlin
// manages memory through this bridge is incompatible. I'm fairly certain it's because they encode the String to
// a null-terminated ByteArray, pin it, and pass the address of the starting element. This is a temporary that
// is no longer valid after the call though.

/**
 * Initialize an aws_byte_cursor from a (pinned) Kotlin [ByteArray].
 * NOTE: the cursor is only valid while the array is pinned
 */
public fun Pinned<ByteArray>.asAwsByteCursor(): CValue<aws_byte_cursor> {
    val arr = get()
    val addr = if (arr.isNotEmpty()) addressOf(0) else null
    return cValue<aws_byte_cursor> {
        len = arr.size.convert()
        ptr = addr?.reinterpret()
    }
}

/**
 * Initialize an aws_byte_cursor instance from an existing cursor
 */
public inline fun aws_byte_cursor.initFromCursor(cur: CValue<aws_byte_cursor>) {
    val dest = this
    cur.useContents {
        dest.len = len
        dest.ptr = ptr
    }
}

/**
 * Interpret this byte cursor as a Kotlin string
 */
public inline fun aws_byte_cursor.toKString(): String = ptr?.readBytes(len.convert())?.decodeToString() ?: ""

/**
 * Interpret this byte cursor as a Kotlin string
 */
public inline fun CValue<aws_byte_cursor>.toKString(): String = useContents { toKString() }

/**
 * Run the given [block] with the string encoded as an aws_byte_cursor. Useful for one off calls that take
 * a byte cursor
 */
public inline fun <reified T> withAwsByteCursor(str: String, block: (cursor: CValue<aws_byte_cursor>) -> T): T {
    val bytes = str.encodeToByteArray()
    return bytes.usePinned { pinned ->
        val cursor = pinned.asAwsByteCursor()
        block(cursor)
    }
}
