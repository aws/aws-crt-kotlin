/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.crt.http

import aws.sdk.kotlin.crt.Allocator
import aws.sdk.kotlin.crt.awsAssertOpSuccess
import aws.sdk.kotlin.crt.util.asAwsByteCursor
import aws.sdk.kotlin.crt.util.initFromCursor
import aws.sdk.kotlin.crt.util.withAwsByteCursor
import kotlinx.cinterop.*
import libcrt.*

/**
 * Convert Kotlin HttpRequest to native HTTP/2 request message
 */
internal fun HttpRequest.toHttp2NativeRequest(): CPointer<cnames.structs.aws_http_message> {
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
                                aws_http_message_add_header(nativeReq, header),
                            ) { "aws_http_message_add_header()" }
                        }
                    }
                }
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
