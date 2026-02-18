/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.http

/**
 * Http2ClientConnection represents a single HTTP/2 connection to a service endpoint.
 * This class is not thread safe and should not be called from different threads.
 */
public interface Http2ClientConnection : HttpClientConnection {
    /**
     * Schedules an HTTP/2 request on the connection. The request does not start sending automatically
     * once the stream is created. You must call [Http2Stream.activate] to begin execution.
     *
     * @param request The HTTP/2 request to make to the server
     * @param handler The stream handler to be called from the event loop
     * @return The Http2Stream that represents this Request/Response pair
     */
    public fun makeRequest(request: Http2Request, handler: HttpStreamResponseHandler): Http2Stream

    /**
     * Send a SETTINGS frame. Settings will be applied locally when SETTINGS ACK is received from peer.
     *
     * @param settings The list of settings to change
     */
    public suspend fun updateSettings(settings: List<Http2ConnectionSetting>)

    /**
     * Send a PING frame. Round-trip-time is calculated when PING ACK is received from peer.
     *
     * @param pingData 8 bytes of data to send with the PING frame, or null
     * @return The round-trip time in nanoseconds
     */
    public suspend fun sendPing(pingData: ByteArray? = null): Long

    /**
     * Send a GOAWAY frame to gracefully close the connection.
     *
     * @param errorCode The HTTP/2 error code (RFC-7540 section 7) to send
     * @param allowMoreStreams Whether to allow more streams before closing
     * @param debugData Optional debug data to send with the GOAWAY frame
     */
    public fun sendGoAway(errorCode: Http2ErrorCode, allowMoreStreams: Boolean, debugData: ByteArray? = null)

    /**
     * Increment the connection's flow-control window to keep data flowing.
     *
     * @param incrementSize The number of bytes to increment the window by
     */
    public fun updateConnectionWindow(incrementSize: Long)
}
