/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.http

import aws.sdk.kotlin.crt.io.Buffer

/**
 * Interface that Native code knows how to call when handling Http Responses
 *
 * Maps 1-1 to the Native Http API here: https://github.com/awslabs/aws-c-http/blob/master/include/aws/http/request_response.h
 */
public interface HttpStreamResponseHandler {
    /**
     * Called from Native when new Http Headers have been received.
     * Note that this function may be called multiple times as HTTP headers are received.
     *
     * @param stream The HttpStreamBase object
     * @param responseStatusCode The HTTP Response Status Code
     * @param blockType The HTTP header block type
     * @param nextHeaders The headers received in the latest IO event.
     */
    public fun onResponseHeaders(
        stream: HttpStream,
        responseStatusCode: Int,
        blockType: Int,
        nextHeaders: List<HttpHeader>?,
    )

    /**
     * Called from Native once all HTTP Headers are processed. Will not be called if there are no Http Headers in the
     * response. Guaranteed to be called exactly once if there is at least 1 Header.
     *
     * @param stream The HttpStreamBase object
     * @param blockType The type of the header block, corresponds to [HttpHeaderBlock]
     */
    public fun onResponseHeadersDone(stream: HttpStream, blockType: Int) {
        /* Optional Callback, do nothing by default */
    }

    /**
     * Called when new Response Body bytes have been received. Note that this function may be called multiple times over
     * the lifetime of an HttpClientConnection as bytes are received.
     *
     * Users must read ALL data from bodyBytesIn before returning.
     *
     * Do NOT keep a reference to this [Buffer] past the lifetime of this function call. The common runtime reserves
     * the right to use memory that only lives as long as the function call.
     *
     * Sliding Window:
     * The Native HttpClientConnection EventLoop will keep sending data until the end of the sliding Window is reached.
     * The user application is responsible for setting the initial Window size appropriately when creating the
     * HttpClientConnection, and for incrementing the sliding window appropriately throughout the lifetime of the HttpStream.
     *
     * For more info, see:
     * - https://en.wikipedia.org/wiki/Sliding_window_protocol
     *
     * @param stream The HTTP Stream the body was delivered to
     * @param bodyBytesIn The HTTP Body Bytes received in the last IO Event.
     * @return The number of bytes to move the sliding window by. Repeatedly returning zero will eventually cause the
     * sliding window to fill up and data to stop flowing until the user slides the window back open.
     */
    public fun onResponseBody(stream: HttpStream, bodyBytesIn: Buffer): Int = /* Optional Callback, ignore incoming response body by default unless user wants to capture it. */
        // FIXME - do we want to follow this pattern or just have a single way of incrementing the
        // window size through `Stream.incrementWindow()`?
        bodyBytesIn.len

    /**
     * Called right before stream is complete, whether successful or unsuccessful.
     * @param stream The HTTP stream to which the metrics apply
     * @param metrics The [HttpStreamMetrics] containing metrics for the given stream
     */
    public fun onMetrics(stream: HttpStream, metrics: HttpStreamMetrics) {
        /* Optional callback, nothing to do by default */
    }

    /**
     * Called from Native when the Response has completed.
     * @param stream completed stream
     * @param errorCode resultant errorCode for the response
     */
    public fun onResponseComplete(stream: HttpStream, errorCode: Int)
}
