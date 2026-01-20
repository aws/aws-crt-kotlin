/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.util

/**
 * Utility object for various hash functions
 */
public expect object Digest {

    /**
     * Calculate the SHA-256 hash of the input [buffer]
     */
    public fun sha256(buffer: ByteArray): ByteArray
}

/**
 * Get a hex string representation of [buffer]
 */
@OptIn(ExperimentalUnsignedTypes::class)
public fun Digest.hex(buffer: ByteArray): String = buffer.asUByteArray().joinToString("") { it.toString(16).padStart(2, '0') }

/**
 * Get a hex string representation of this byte array
 */
public fun ByteArray.encodeToHex(): String = Digest.hex(this)
