/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.crt.util.hashing

import aws.sdk.kotlin.crt.Allocator
import aws.sdk.kotlin.crt.WithCrt
import aws.sdk.kotlin.crt.awsAssertOpSuccess
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.cValue
import kotlinx.cinterop.convert
import kotlinx.cinterop.pointed
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import libcrt.aws_byte_buf
import libcrt.aws_byte_cursor_from_array
import libcrt.aws_hash_destroy
import libcrt.aws_hash_finalize
import libcrt.aws_hash_update
import libcrt.aws_md5_new

/**
 * MD5 hash function implemented using bindings to CRT
 */
public class Md5 :
    WithCrt(),
    HashFunction {
    private var md5 = checkNotNull(aws_md5_new(Allocator.Default)) { "aws_md5_new" }

    override fun update(input: ByteArray, offset: Int, length: Int) {
        require(offset >= 0) { "offset must not be negative" }
        require(length >= 0) { "length must not be negative" }
        require(offset + length <= input.size) {
            "offset + length must not exceed input size: $offset + $length > ${input.size}"
        }

        if (input.isEmpty() || length == 0) {
            return
        }

        val inputCursor = input.usePinned {
            aws_byte_cursor_from_array(it.addressOf(offset), length.convert())
        }

        awsAssertOpSuccess(aws_hash_update(md5, inputCursor)) {
            "aws_hash_update"
        }
    }

    override fun digest(): ByteArray {
        val output = ByteArray(md5.pointed.digest_size.toInt())

        val byteBuf = output.usePinned {
            cValue<aws_byte_buf> {
                capacity = output.size.convert()
                len = 0U
                buffer = it.addressOf(0).reinterpret()
            }
        }

        awsAssertOpSuccess(aws_hash_finalize(md5, byteBuf, 0U)) { "aws_hash_finalize" }

        return output.also { reset() }
    }

    override fun reset() {
        aws_hash_destroy(md5)
        md5 = checkNotNull(aws_md5_new(Allocator.Default)) { "aws_md5_new" }
    }
}
