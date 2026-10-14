/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.http

import aws.sdk.kotlin.crt.CrtRuntimeException
import aws.sdk.kotlin.crt.CrtTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import software.amazon.awssdk.crt.CrtRuntimeException as CrtRuntimeExceptionJni
import software.amazon.awssdk.crt.http.HttpClientConnection as HttpClientConnectionJni
import software.amazon.awssdk.crt.http.HttpRequest as HttpRequestJni
import software.amazon.awssdk.crt.http.HttpStream as HttpStreamJni
import software.amazon.awssdk.crt.http.HttpStreamResponseHandler as HttpStreamResponseHandlerJni
import software.amazon.awssdk.crt.http.HttpVersion as HttpVersionJni

/**
 * Regression for aws/aws-crt-kotlin#309: JNI makeRequest failures must surface as the Kotlin
 * [CrtRuntimeException] so CrtHttpEngine can map AWS_ERROR_HTTP_* to a retryable HttpException.
 */
class HttpClientConnectionMakeRequestExceptionTest : CrtTest() {
    companion object {
        // aws-c-http: AWS_ERROR_HTTP_CONNECTION_CLOSED
        const val AWS_ERROR_HTTP_CONNECTION_CLOSED = 2058
    }

    @Test
    fun makeRequestWrapsJniCrtRuntimeException() {
        val conn = HttpClientConnectionJVM(ThrowingClosedJniConnection())
        try {
            val request = HttpRequest.build {
                method = "GET"
                encodedPath = "/"
                headers {
                    append("Host", "example.com")
                }
            }
            val handler = object : HttpStreamResponseHandler {
                override fun onResponseHeaders(
                    stream: HttpStream,
                    responseStatusCode: Int,
                    blockType: Int,
                    nextHeaders: List<HttpHeader>?,
                ) {}

                override fun onResponseComplete(stream: HttpStream, errorCode: Int) {}
            }

            val ex = assertFailsWith<CrtRuntimeException> {
                conn.makeRequest(request, handler)
            }

            assertEquals(AWS_ERROR_HTTP_CONNECTION_CLOSED, ex.errorCode)
            assertIs<CrtRuntimeExceptionJni>(ex.cause)
            assertNotNull(ex.message)
        } finally {
            conn.close()
        }
    }

    /**
     * JNI connection stub that throws the Java CRT exception makeRequest raises when the pooled
     * connection has already been closed by the peer.
     *
     * Uses a non-zero sentinel handle so [HttpClientConnectionJni] construction succeeds; native
     * release is a no-op because no real CRT object was allocated.
     */
    private class ThrowingClosedJniConnection : HttpClientConnectionJni(SENTINEL_HANDLE) {
        override fun getVersion(): HttpVersionJni = HttpVersionJni.HTTP_1_1

        override fun makeRequest(
            request: HttpRequestJni,
            streamHandler: HttpStreamResponseHandlerJni,
        ): HttpStreamJni {
            throw CrtRuntimeExceptionJni(
                AWS_ERROR_HTTP_CONNECTION_CLOSED,
                "HttpClientConnection.MakeRequest: Unable to Execute Request",
            )
        }

        override fun releaseNativeHandle() {
            // No native handle was allocated for this stub.
        }

        override fun canReleaseReferencesImmediately(): Boolean = true

        companion object {
            private const val SENTINEL_HANDLE = 1L
        }
    }
}
