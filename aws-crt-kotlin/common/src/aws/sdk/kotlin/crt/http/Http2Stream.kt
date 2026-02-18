/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.http

/**
 * Represents a single HTTP/2 Request/Response pair within an Http2ClientConnection
 */
public interface Http2Stream : HttpStreamBase {
    /**
     * Reset the HTTP/2 stream. Note that if the stream closes before this async call is fully processed,
     * the RST_STREAM frame will not be sent.
     *
     * @param errorCode Reason to reset the stream
     */
    public fun resetStream(errorCode: Http2ErrorCode)
}
