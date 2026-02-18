/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.http

import software.amazon.awssdk.crt.http.Http2Stream as Http2StreamJni

/**
 * JVM implementation of Http2Stream wrapping aws-crt-java's Http2Stream
 */
internal class Http2StreamJVM(private val jniStream: Http2StreamJni) : Http2Stream {
    override val responseStatusCode: Int
        get() = jniStream.responseStatusCode

    override fun incrementWindow(size: Int) {
        jniStream.incrementWindow(size)
    }

    override fun activate() {
        jniStream.activate()
    }

    override fun resetStream(errorCode: Http2ErrorCode) {
        jniStream.resetStream(errorCode.toJni())
    }

    override fun close() = jniStream.close()
}
