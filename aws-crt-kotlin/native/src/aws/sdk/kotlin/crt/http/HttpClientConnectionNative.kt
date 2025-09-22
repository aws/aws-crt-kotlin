/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.crt.http

import aws.sdk.kotlin.crt.*
import aws.sdk.kotlin.crt.io.Buffer
import aws.sdk.kotlin.crt.io.ByteCursorBuffer
import aws.sdk.kotlin.crt.util.*
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.*
import libcrt.*
import platform.posix.size_t

internal class HttpClientConnectionNative(
    private val manager: HttpClientConnectionManager,
    override val ptr: CPointer<cnames.structs.aws_http_connection>,
) : WithCrt(),
    Closeable,
    HttpClientConnection,
    NativeHandle<cnames.structs.aws_http_connection> {

    private val closed = atomic(false)

    override val id: String = ptr.rawValue.toString()
    override fun makeRequest(httpReq: HttpRequest, handler: HttpStreamResponseHandler): HttpStream {
        val nativeReq = httpReq.toNativeRequest()
        val cbData = HttpStreamContext(null, handler, nativeReq)
        val stableRef = StableRef.create(cbData)
        val reqOptions = cValue<aws_http_make_request_options> {
            self_size = sizeOf<aws_http_make_request_options>().convert()
            request = nativeReq
            user_data = stableRef.asCPointer()

            // callbacks
            on_response_headers = staticCFunction(::onResponseHeaders)
            on_response_header_block_done = staticCFunction(::onResponseHeaderBlockDone)
            on_response_body = staticCFunction(::onIncomingBody)
            on_complete = staticCFunction(::onStreamComplete)
        }

        val stream = aws_http_connection_make_request(ptr, reqOptions)

        if (stream == null) {
            aws_http_message_destroy(nativeReq)
            stableRef.dispose()
            throw CrtRuntimeException("aws_http_connection_make_request()")
        }

        return HttpStreamNative(stream).also { cbData.stream = it }
    }

    override fun shutdown() {
        aws_http_connection_close(ptr)
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            manager.releaseConnection(this)
        }
    }
}

/**
 * Userdata passed through the native callbacks for HTTP responses
 */
private class HttpStreamContext(
    /**
     * The Kotlin stream object. This starts as null because the context is created before the stream itself. We need
     * the stream in callbacks so we set it lazily.
     */
    var stream: HttpStreamNative? = null,

    /**
     * The actual Kotlin handler for each callback
     */
    val handler: HttpStreamResponseHandler,

    /**
     * The aws-c-http request instance
     */
    val nativeReq: CPointer<cnames.structs.aws_http_message>,
)

private fun onResponseHeaders(
    nativeStream: CPointer<cnames.structs.aws_http_stream>?,
    blockType: aws_http_header_block,
    headerArray: CPointer<aws_http_header>?,
    numHeaders: size_t,
    userdata: COpaquePointer?,
): Int {
    val ctxStableRef = userdata?.asStableRef<HttpStreamContext>() ?: return aws_raise_error(AWS_ERROR_HTTP_CALLBACK_FAILURE.toInt())
    ctxStableRef.use {
        val ctx = it.get()
        val stream = ctx.stream ?: return AWS_OP_ERR

        val hdrCnt = numHeaders.toInt()
        val headers: List<HttpHeader>? = if (hdrCnt > 0 && headerArray != null) {
            val kheaders = mutableListOf<HttpHeader>()
            for (i in 0 until hdrCnt) {
                val nativeHdr = headerArray[i]
                val hdr = HttpHeader(nativeHdr.name.toKString(), nativeHdr.value.toKString())
                kheaders.add(hdr)
            }
            kheaders
        } else {
            null
        }

        try {
            ctx.handler.onResponseHeaders(stream, stream.responseStatusCode, blockType.value.toInt(), headers)
        } catch (ex: Exception) {
            log(LogLevel.Error, "onResponseHeaders: $ex")
            return aws_raise_error(AWS_ERROR_HTTP_CALLBACK_FAILURE.toInt())
        }

        return AWS_OP_SUCCESS
    }
}

private fun onResponseHeaderBlockDone(
    nativeStream: CPointer<cnames.structs.aws_http_stream>?,
    blockType: aws_http_header_block,
    userdata: COpaquePointer?,
): Int {
    val ctx = userdata?.asStableRef<HttpStreamContext>()?.get() ?: return AWS_OP_ERR
    val stream = ctx.stream ?: return AWS_OP_ERR

    try {
        ctx.handler.onResponseHeadersDone(stream, blockType.value.toInt())
    } catch (ex: Exception) {
        log(LogLevel.Error, "onResponseHeaderBlockDone: $ex")
        return aws_raise_error(AWS_ERROR_HTTP_CALLBACK_FAILURE.toInt())
    }

    return AWS_OP_SUCCESS
}

private fun onIncomingBody(
    nativeStream: CPointer<cnames.structs.aws_http_stream>?,
    data: CPointer<aws_byte_cursor>?,
    userdata: COpaquePointer?,
): Int {
    val stableRef = try { userdata?.asStableRef<HttpStreamContext>() } catch (_: NullPointerException) { return AWS_OP_ERR }
    val ctx = try { stableRef?.get() } catch (_: NullPointerException) { return AWS_OP_ERR }
    if (ctx == null) return AWS_OP_ERR
    val stream = ctx.stream
    if (stream == null) return AWS_OP_ERR

    try {
        val body = if (data != null) ByteCursorBuffer(data) else Buffer.Empty
        val windowIncrement = ctx.handler.onResponseBody(stream, body)
        if (windowIncrement < 0) {
            return aws_raise_error(AWS_ERROR_HTTP_CALLBACK_FAILURE.toInt())
        }

        if (windowIncrement > 0) {
            aws_http_stream_update_window(nativeStream, windowIncrement.convert())
        }
    } catch (ex: Exception) {
        log(LogLevel.Error, "onIncomingBody: $ex")
        return aws_raise_error(AWS_ERROR_HTTP_CALLBACK_FAILURE.toInt())
    }

    return AWS_OP_SUCCESS
}

private fun onStreamComplete(
    nativeStream: CPointer<cnames.structs.aws_http_stream>?,
    errorCode: Int,
    userdata: COpaquePointer?,
) {
    val stableRef = userdata?.asStableRef<HttpStreamContext>() ?: return
    val ctx = stableRef.get()
    val stream = ctx.stream ?: return

    try {
        ctx.handler.onResponseComplete(stream, errorCode)
    } catch (ex: Exception) {
        log(LogLevel.Error, "onStreamComplete: $ex")
        // close connection if callback throws an exception
        aws_http_connection_close(aws_http_stream_get_connection(nativeStream))
    } finally {
        // cleanup stream resources
        stableRef.dispose()
        aws_http_message_destroy(ctx.nativeReq)
    }
}

internal fun HttpRequest.toNativeRequest(): CPointer<cnames.structs.aws_http_message> {
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

        headers.forEach { key, values ->
            // instead of usual idiomatic map(), forEach()...
            // have to be a little more careful here as some of these are temporaries and we need
            // stable memory addresses
            key.encodeToByteArray().usePinned { keyBytes ->
                val keyCursor = keyBytes.asAwsByteCursor()
                values.forEach {
                    it.encodeToByteArray().usePinned { valueBytes ->
                        val valueCursor = valueBytes.asAwsByteCursor()

                        val header = cValue<aws_http_header> {
                            name.initFromCursor(keyCursor)
                            value.initFromCursor(valueCursor)
                        }

                        awsAssertOpSuccess(
                            aws_http_message_add_header(nativeReq, header),
                        ) {
                            "aws_http_message_add_header()"
                        }
                    }
                }
            }
        }

        val bodyStream = body?.let { inputStream(it) }
        aws_http_message_set_body_stream(nativeReq, bodyStream)
    } catch (ex: Exception) {
        aws_http_message_destroy(nativeReq)
        throw ex
    }

    return nativeReq
}

internal fun CPointer<cnames.structs.aws_http_message>.toHttpRequest(): HttpRequest = memScoped {
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
