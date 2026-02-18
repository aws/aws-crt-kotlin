/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.http

import aws.sdk.kotlin.crt.Closeable

/**
 * This class wraps aws-c-http to provide the basic HTTP request/response functionality via the AWS Common Runtime.
 *
 * HttpClientConnection represents a single connection to an HTTP service endpoint.
 *
 * This class is not thread safe and should not be called from different threads.
 */
public interface HttpClientConnection : Closeable {
    /**
     * The unique ID of this connection.
     */
    public val id: String

    /**
     * The HTTP version of this connection.
     */
    public val version: HttpVersion

    /**
     * Schedules an HttpRequest on the Native EventLoop for this HttpClientConnection.
     * The request does not start sending automatically once the stream is created. You must call
     * [HttpStreamBase.activate] to begin execution of the request.
     *
     * @param httpReq The Request to make to the Server.
     * @param handler The Stream Handler to be called from the Native EventLoop
     * @throws CrtRuntimeException if stream creation fails
     * @return The HttpStreamBase that represents this Request/Response Pair. It can be closed at any time during the
     *          request/response, but must be closed by the user thread making this request when it's done.
     */
    public fun makeRequest(httpReq: HttpRequest, handler: HttpStreamResponseHandler): HttpStreamBase

    /**
     * Forcefully close the connection
     *
     * NOTE: connections are vended via [HttpClientConnectionManager], [close] will just return the connection to the pool
     * and potentially be re-used.
     */
    public fun shutdown()
}
