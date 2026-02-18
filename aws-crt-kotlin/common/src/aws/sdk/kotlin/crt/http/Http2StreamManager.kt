/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.http

import aws.sdk.kotlin.crt.AsyncShutdown
import aws.sdk.kotlin.crt.Closeable

/**
 * Manages a pool of HTTP/2 connections and streams. Automatically creates and manages
 * HTTP/2 connections, distributing streams across them based on concurrency settings.
 *
 * This is the recommended way to use HTTP/2 for high-throughput scenarios.
 */
public expect class Http2StreamManager(options: Http2StreamManagerOptions) : Closeable, AsyncShutdown {
    /**
     * The options this manager was configured with
     */
    public val options: Http2StreamManagerOptions

    /**
     * Request an HTTP/2 stream from the manager. The manager will automatically select
     * or create an appropriate connection.
     *
     * @param request The HTTP/2 request to make
     * @param handler The stream handler for response callbacks
     * @return The HTTP/2 stream
     */
    public suspend fun acquireStream(request: Http2Request, handler: HttpStreamResponseHandler): Http2Stream

    /**
     * Get current metrics for the stream manager
     */
    public val managerMetrics: HttpManagerMetrics

    override fun close()
    override suspend fun waitForShutdown()
}
