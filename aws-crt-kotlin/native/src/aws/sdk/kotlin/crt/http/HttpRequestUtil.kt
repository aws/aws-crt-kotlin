/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.crt.http

import aws.sdk.kotlin.crt.Allocator
import aws.sdk.kotlin.crt.awsAssertOpSuccess
import aws.sdk.kotlin.crt.util.asAwsByteCursor
import aws.sdk.kotlin.crt.util.initFromCursor
import aws.sdk.kotlin.crt.util.toKString
import aws.sdk.kotlin.crt.util.withAwsByteCursor
import cnames.structs.aws_http_message
import kotlinx.cinterop.*
import libcrt.*

/**
 * Convert Kotlin HttpRequest to native HTTP/1.1 request message
 */
internal fun HttpRequest.toNativeRequest(): CPointer<aws_http_message> {
    val nativeReq = checkNotNull(
        aws_http_message_new_request(Allocator.Default),
    ) { "aws_http_message_new_request()" }

    try {
        awsAssertOpSuccess(
            withAwsByteCursor(method) { method ->
                aws_http_message_set_request_method(nativeReq, method)
            },
        ) { "aws_http_message_set_request_method()" }

        awsAssertOpSuccess(
            withAwsByteCursor(encodedPath) { encodedPath ->
                aws_http_message_set_request_path(nativeReq, encodedPath)
            },
        ) { "aws_http_message_set_request_path()" }

        headers.forEach { key, values -> nativeReq.addHeader(key, values) }

        val bodyStream = body?.let { inputStream(it) }
        aws_http_message_set_body_stream(nativeReq, bodyStream)
    } catch (ex: Exception) {
        aws_http_message_release(nativeReq)
        throw ex
    }

    return nativeReq
}

/**
 * Convert Kotlin HttpRequest to native HTTP/2 request message
 */
internal fun HttpRequest.toHttp2NativeRequest(): CPointer<aws_http_message> {
    val nativeReq = checkNotNull(
        aws_http2_message_new_request(Allocator.Default),
    ) { "aws_http2_message_new_request()" }

    try {
        val headers = aws_http_message_get_headers(nativeReq)

        // Set HTTP/2 pseudo-headers
        headers?.let { h ->
            // :method
            awsAssertOpSuccess(
                withAwsByteCursor(method) { method ->
                    aws_http2_headers_set_request_method(h, method)
                },
            ) { "aws_http2_headers_set_request_method()" }

            // :path
            awsAssertOpSuccess(
                withAwsByteCursor(encodedPath) { path ->
                    aws_http2_headers_set_request_path(h, path)
                },
            ) { "aws_http2_headers_set_request_path()" }

            // :scheme and :authority from headers if present
            this.headers[":scheme"]?.let { scheme ->
                awsAssertOpSuccess(
                    withAwsByteCursor(scheme) { s ->
                        aws_http2_headers_set_request_scheme(h, s)
                    },
                ) { "aws_http2_headers_set_request_scheme()" }
            }

            this.headers[":authority"]?.let { authority ->
                awsAssertOpSuccess(
                    withAwsByteCursor(authority) { a ->
                        aws_http2_headers_set_request_authority(h, a)
                    },
                ) { "aws_http2_headers_set_request_authority()" }
            }
        }

        // Add regular headers (skip pseudo-headers)
        this.headers.entries().forEach { (key, values) ->
            if (!key.startsWith(":")) {
                nativeReq.addHeader(key, values)
            }
        }

        val bodyStream = body?.let { inputStream(it) }
        aws_http_message_set_body_stream(nativeReq, bodyStream)
    } catch (ex: Exception) {
        aws_http_message_release(nativeReq)
        throw ex
    }

    return nativeReq
}

private fun CPointer<aws_http_message>.addHeader(key: String, values: List<String>) {
    // instead of usual idiomatic map(), forEach()...
    // have to be a little more careful here as some of these are temporaries and we need
    // stable memory addresses
    key.encodeToByteArray().usePinned { keyBytes ->
        val keyCursor = keyBytes.asAwsByteCursor()
        values.forEach { value ->
            value.encodeToByteArray().usePinned { valueBytes ->
                val valueCursor = valueBytes.asAwsByteCursor()
                val header = cValue<aws_http_header> {
                    name.initFromCursor(keyCursor)
                    this.value.initFromCursor(valueCursor)
                }
                awsAssertOpSuccess(
                    aws_http_message_add_header(this, header),
                ) { "aws_http_message_add_header()" }
            }
        }
    }
}

/**
 * Convert native request message to Kotlin HttpRequest
 */
internal fun CPointer<aws_http_message>.toHttpRequest(): HttpRequest = memScoped {
    val nativeReq = this@toHttpRequest
    val req = HttpRequestBuilder()

    val nativeMethod = cValue<aws_byte_cursor>()
    val nativeMethodPtr = nativeMethod.ptr
    aws_http_message_get_request_method(nativeReq, nativeMethodPtr)
    req.method = nativeMethodPtr.pointed.toKString()

    val encodedPath = cValue<aws_byte_cursor>()
    val encodedPathPtr = encodedPath.ptr
    aws_http_message_get_request_path(nativeReq, encodedPathPtr)
    req.encodedPath = encodedPathPtr.pointed.toKString()

    val headers = aws_http_message_get_headers(nativeReq)
    for (i in 0 until aws_http_message_get_header_count(nativeReq).toInt()) {
        val header = cValue<aws_http_header>()
        val headerPtr = header.ptr
        aws_http_headers_get_index(headers, i.toULong(), headerPtr)
        req.headers.append(headerPtr.pointed.name.toKString(), headerPtr.pointed.value.toKString())
    }

    val nativeStream = aws_http_message_get_body_stream(nativeReq)
    req.body = nativeStream?.toHttpRequestBodyStream()

    return req.build()
}
