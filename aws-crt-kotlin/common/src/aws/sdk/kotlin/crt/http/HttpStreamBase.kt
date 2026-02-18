/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.http

import aws.sdk.kotlin.crt.Closeable

/**
 * Base interface for HTTP streams representing a single HTTP Request/Response pair.
 * This is the common interface for both HTTP/1.1 and HTTP/2 streams.
 */
public interface HttpStreamBase : Closeable {
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
}
