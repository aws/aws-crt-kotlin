/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.crt.util.hashing

import aws.sdk.kotlin.crt.WithCrt
import kotlinx.cinterop.*
import libcrt.aws_checksums_crc32
import libcrt.aws_checksums_crc32c
import platform.posix.uint32_t
import platform.posix.uint8_tVar

/**
 * A typealias to CRT's aws_checksums_<crc32/crc32c> functions
 */
internal typealias AwsChecksumsCrcFunction = (
    input: CValuesRef<uint8_tVar>?,
    length: Int,
    previousCrc32: uint32_t,
) -> uint32_t

internal class Crc(val checksumFn: AwsChecksumsCrcFunction) : HashFunction {
    private var crc = 0U

    override fun update(input: ByteArray, offset: Int, length: Int) {
        require(offset >= 0) { "offset must not be negative" }
        require(length >= 0) { "length must not be negative" }
        require(offset + length <= input.size) {
            "offset + length must not exceed input size: $offset + $length > ${input.size}"
        }

        if (input.isEmpty() || length == 0) {
            return
        }

        val offsetInput = input.usePinned {
            it.addressOf(offset)
        }
        crc = checksumFn(offsetInput.reinterpret(), length.convert(), crc)
    }

    override fun digest(): ByteArray = byteArrayOf(
        ((crc shr 24) and 0xffu).toByte(),
        ((crc shr 16) and 0xffu).toByte(),
        ((crc shr 8) and 0xffu).toByte(),
        (crc and 0xffu).toByte(),
    ).also { reset() }

    override fun reset() {
        crc = 0U
    }
}

/**
 * A CRC32 [HashFunction] implemented using bindings to CRT.
 */
public class Crc32 :
    WithCrt(),
    HashFunction {
    private val crc32 = Crc(::aws_checksums_crc32)
    override fun update(input: ByteArray, offset: Int, length: Int) {
        crc32.update(input, offset, length)
    }
    override fun digest(): ByteArray = crc32.digest()
    override fun reset() {
        crc32.reset()
    }
}

/**
 * A CRC32C [HashFunction] implemented using bindings to CRT.
 */
public class Crc32c :
    WithCrt(),
    HashFunction {
    private val crc32c = Crc(::aws_checksums_crc32c)
    override fun update(input: ByteArray, offset: Int, length: Int) {
        crc32c.update(input, offset, length)
    }
    override fun digest(): ByteArray = crc32c.digest()
    override fun reset() {
        crc32c.reset()
    }
}
