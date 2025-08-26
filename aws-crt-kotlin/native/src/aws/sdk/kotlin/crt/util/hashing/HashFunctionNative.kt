/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.crt.util.hashing

/**
 * A function which calculates the hash of given data
 */
public interface HashFunction {
    /**
     * Update the hash content with [length] bytes from [input] starting from [offset]
     */
    public fun update(input: ByteArray, offset: Int, length: Int)

    /**
     * Digest the hash content, returning [ByteArray] and resetting the hash
     */
    public fun digest(): ByteArray

    /**
     * Reset the content of the hash
     */
    public fun reset()
}
