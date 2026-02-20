/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.http

import aws.sdk.kotlin.crt.CrtTest
import aws.sdk.kotlin.crt.util.toKString
import kotlinx.cinterop.*
import libcrt.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class Http2RequestNativeTest : CrtTest() {
    @Test
    fun testToHttp2NativeRequestCreatesValidMessage() {
        val request = HttpRequest.build {
            method = "POST"
            encodedPath = "/api/test"
            headers {
                append(":method", "POST")
                append(":path", "/api/test")
                append(":scheme", "https")
                append(":authority", "example.com")
                append("content-type", "application/json")
            }
        }

        val nativeReq = request.toHttp2NativeRequest()
        assertNotNull(nativeReq)

        try {
            val headers = aws_http_message_get_headers(nativeReq)
            assertNotNull(headers, "Headers should not be null")
        } finally {
            aws_http_message_release(nativeReq)
        }
    }

    @Test
    fun testToHttp2NativeRequestWithHeaders() {
        val request = HttpRequest.build {
            method = "GET"
            encodedPath = "/test"
            headers {
                append(":method", "GET")
                append(":path", "/test")
                append(":scheme", "https")
                append(":authority", "example.com")
                append("x-custom", "value")
                append("content-type", "application/json")
            }
        }

        val nativeReq = request.toHttp2NativeRequest()
        assertNotNull(nativeReq)

        try {
            val headers = aws_http_message_get_headers(nativeReq)
            assertNotNull(headers, "Headers should not be null")

            // Verify all headers are present
            val headerCount = aws_http_message_get_header_count(nativeReq).toInt()
            assertEquals(6, headerCount)

            val headersMap = mutableMapOf<String, String>()

            memScoped {
                val header = alloc<aws_http_header>()
                for (i in (0 until headerCount)) {
                    aws_http_headers_get_index(headers, i.toULong(), header.ptr)
                    headersMap[header.name.toKString()] = header.value.toKString()
                }
            }

            assertEquals("GET", headersMap[":method"])
            assertEquals("/test", headersMap[":path"])
            assertEquals("https", headersMap[":scheme"])
            assertEquals("example.com", headersMap[":authority"])
            assertEquals("value", headersMap["x-custom"])
            assertEquals("application/json", headersMap["content-type"])
        } finally {
            aws_http_message_release(nativeReq)
        }
    }
}
