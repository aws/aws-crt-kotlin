/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.http

import aws.sdk.kotlin.crt.Closeable

/**
 * An HttpStream represents a single Http Request/Response pair within a HttpClientConnection, and wraps the native resources
 * from the aws-c-http library.
 *
 * Can be used to update the Window size, or to abort the stream early in the middle of sending/receiving Http Bodies.
 */
public interface HttpStream : Closeable {

    /**
     * Retrieve the Http response status code. Available ONLY after the first set of response
     * headers have been received. See [HttpStreamResponseHandler]
     */
    public val responseStatusCode: Int

    /**
     * Increment the stream's flow-control window to keep data flowing.
     *
     * If the connection was created with [HttpClientConnectionManagerOptions.manualWindowManagement] set true, the
     * flow-control window of each stream will shrink as body data is received (headers, padding, and other metadata
     * do not affect the window). The connection's `initialWindowSize` determines the starting size of each stream's
     * window. If a stream's flow-control window reaches 0, no further data will be received.
     *
     * If `manualWindowManagement` is false, this call will have no effect. The connection maintains its
     * flow-control windows such that no back-pressure is applied and data arrives as fast as possible.
     *
     * @param size How many bytes to increment the sliding window by.
     */
    public fun incrementWindow(size: Int)

    /**
     * Activate the client stream and start processing the request
     */
    public fun activate()

    /**
     * Send a chunk of data. You must call activate() before using this function.
     * @param chunkData the chunk of data to send. this should be already formatted in the chunked transfer encoding.
     * @param isFinalChunk represents if the chunk of data is the final chunk. if set to true, this will terminate the request stream.
     */
    public fun writeChunk(chunkData: ByteArray, isFinalChunk: Boolean)
}
