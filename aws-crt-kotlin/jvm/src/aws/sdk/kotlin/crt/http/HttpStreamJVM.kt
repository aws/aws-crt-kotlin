/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.http

import kotlinx.coroutines.future.await
import software.amazon.awssdk.crt.http.HttpStream as HttpStreamJni

/**
 * Wrapper around CRT Java's HttpStream that implements the KMP interface
 */
internal class HttpStreamJVM(private val jniStream: HttpStreamJni) : HttpStream {
    override val responseStatusCode: Int
        get() = jniStream.responseStatusCode

    override fun incrementWindow(size: Int) = jniStream.incrementWindow(size)

    override fun activate() = jniStream.activate()

    override fun close() = jniStream.close()

    override suspend fun writeChunk(chunkData: ByteArray, isFinalChunk: Boolean) {
        jniStream.writeChunk(chunkData, isFinalChunk).await()
    }
}
