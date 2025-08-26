/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.crt.util.hashing

import aws.sdk.kotlin.crt.Allocator
import aws.sdk.kotlin.crt.WithCrt
import aws.sdk.kotlin.crt.awsAssertOpSuccess
import kotlinx.cinterop.*
import libcrt.*

/**
 * A typealias to CRT's aws_<checksum_algorithm>_new functions (i.e. aws_sha1_new, aws_sha256_new))
 */
internal typealias InitializeHashFn = (
    allocator: CValuesRef<aws_allocator>?,
) -> CPointer<aws_hash>?

/**
 * SHA-1 hash function implemented using bindings to CRT
 */
public class Sha1 :
    WithCrt(),
    HashFunction {
    private val sha1 = Sha(::aws_sha1_new)
    override fun update(input: ByteArray, offset: Int, length: Int) {
        sha1.update(input, offset, length)
    }
    override fun digest(): ByteArray = sha1.digest()
    override fun reset() {
        sha1.reset()
    }
}

/**
 * SHA-256 hash function implemented using bindings to CRT
 */
public class Sha256 :
    WithCrt(),
    HashFunction {
    private val sha256 = Sha(::aws_sha256_new)
    override fun update(input: ByteArray, offset: Int, length: Int) {
        sha256.update(input, offset, length)
    }
    override fun digest(): ByteArray = sha256.digest()
    override fun reset() {
        sha256.reset()
    }
}

internal class Sha(val initializeFn: InitializeHashFn) : HashFunction {
    private var hash: CPointer<aws_hash>

    init {
        hash = initializeHash()
    }

    // aws_hash_update
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
        awsAssertOpSuccess(aws_hash_update(hash, inputCursor)) {
            "aws_hash_update"
        }
    }

    // aws_hash_finalize
    override fun digest(): ByteArray {
        val output = ByteArray(hash.pointed.digest_size.toInt())

        val byteBuf = output.usePinned {
            cValue<aws_byte_buf> {
                capacity = output.size.convert()
                len = 0U
                buffer = it.addressOf(0).reinterpret()
            }
        }

        awsAssertOpSuccess(aws_hash_finalize(hash, byteBuf, 0U)) { "aws_hash_finalize" }

        return output.also { reset() }
    }

    // aws_hash_destroy
    override fun reset() {
        aws_hash_destroy(hash)
        hash = initializeHash()
    }

    private fun initializeHash() = checkNotNull(initializeFn(Allocator.Default.allocator)) {
        "failed to initialize hash"
    }
}
