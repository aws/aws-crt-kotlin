/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.http

import aws.sdk.kotlin.crt.Closeable

/**
 * An HttpStream represents a single HTTP Request/Response pair within an HttpClientConnection.
 */
public interface HttpStream : Closeable {
    /**
     * Retrieve the HTTP response status code. Available ONLY after the first set of response
     * headers have been received. See [HttpStreamResponseHandler]
     */
    public val responseStatusCode: Int

    /**
     * Increment the stream's flow-control window to keep data flowing.
     *
     * If the connection was created with manual window management enabled, the flow-control window
     * of each stream will shrink as body data is received. If a stream's flow-control window reaches 0,
     * no further data will be received.
     *
     * @param size How many bytes to increment the sliding window by
     */
    public fun incrementWindow(size: Int)

    /**
     * Activate the client stream and start processing the request
     */
    public fun activate()

    /**
     * Send a chunk of data. You must call activate() before using this function.
     * Note: Only supported for HTTP/1.1 streams. Will throw for HTTP/2 streams.
     * @param chunkData the chunk of data to send. this should be already formatted in the chunked transfer encoding.
     * @param isFinalChunk represents if the chunk of data is the final chunk. if set to true, this will terminate the request stream.
     */
    public suspend fun writeChunk(chunkData: ByteArray, isFinalChunk: Boolean)
}
