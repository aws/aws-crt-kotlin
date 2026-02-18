/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.http

/**
 * An immutable HTTP/2 request ready to send
 * @property method The HTTP method (verb) to use (e.g. "GET", "POST", etc)
 * @property encodedPath The (percent encoded) URL path
 * @property headers The HTTP [Headers] to send with the request (should include pseudo-headers for HTTP/2)
 * @property body The optional request body stream for sending a payload
 */
public data class Http2Request(
    val method: String,
    val encodedPath: String,
    val headers: Headers,
    val body: HttpRequestBodyStream? = null,
) {
    public companion object {
        public fun build(block: Http2RequestBuilder.() -> Unit): Http2Request = Http2RequestBuilder().apply(block).build()
    }
}

/**
 * Build an immutable [Http2Request]
 */
public class Http2RequestBuilder {
    public var method: String = "GET"
    public var encodedPath: String = ""
    public val headers: HeadersBuilder = HeadersBuilder()
    public var body: HttpRequestBodyStream? = null

    public fun build(): Http2Request = Http2Request(method, encodedPath, headers.build(), body)
}

/**
 * Modify the headers inside the given block
 */
public fun Http2RequestBuilder.headers(block: HeadersBuilder.() -> Unit): Unit = headers.let(block)
