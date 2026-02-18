/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.http

import kotlin.test.Test
import kotlin.test.assertEquals

class Http2RequestTest {
    @Test
    fun testBuildHttp2Request() {
        val request = Http2Request.build {
            method = "GET"
            encodedPath = "/test"
            headers {
                append(":scheme", "https")
                append(":authority", "example.com")
                append(":method", "GET")
                append(":path", "/test")
            }
        }

        assertEquals("GET", request.method)
        assertEquals("/test", request.encodedPath)
        assertEquals("https", request.headers[":scheme"])
    }
}
