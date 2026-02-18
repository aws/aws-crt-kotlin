/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.http

/**
 * An HttpStream represents a single HTTP/1.1 Request/Response pair within an HttpClientConnection
 */
public interface HttpStream : HttpStreamBase {
    /**
     * Send a chunk of data. You must call activate() before using this function.
     * @param chunkData the chunk of data to send. this should be already formatted in the chunked transfer encoding.
     * @param isFinalChunk represents if the chunk of data is the final chunk. if set to true, this will terminate the request stream.
     */
    public suspend fun writeChunk(chunkData: ByteArray, isFinalChunk: Boolean)
}
