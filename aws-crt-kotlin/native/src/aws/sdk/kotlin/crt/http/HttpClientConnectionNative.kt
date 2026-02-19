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
    override val version: HttpVersion = HttpVersion.fromInt(aws_http_connection_get_version(ptr).value.toInt())

    override fun makeRequest(httpReq: HttpRequest, handler: HttpStreamResponseHandler): HttpStream {
        val nativeReq = if (version == HttpVersion.HTTP_2) {
            httpReq.toHttp2NativeRequest()
        } else {
            httpReq.toNativeRequest()
        }
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
            aws_http_message_release(nativeReq)
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

private fun callbackError(): Int = aws_raise_error(AWS_ERROR_HTTP_CALLBACK_FAILURE.toInt())

private fun onResponseHeaders(
    nativeStream: CPointer<cnames.structs.aws_http_stream>?,
    blockType: aws_http_header_block,
    headerArray: CPointer<aws_http_header>?,
    numHeaders: size_t,
    userdata: COpaquePointer?,
): Int = userdata?.withDereferenced<HttpStreamContext, _> { ctx ->
    ctx.stream?.let { stream ->
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
            AWS_OP_SUCCESS
        } catch (ex: Exception) {
            log(LogLevel.Error, "onResponseHeaders: $ex")
            null
        }
    }
} ?: callbackError()

private fun onResponseHeaderBlockDone(
    nativeStream: CPointer<cnames.structs.aws_http_stream>?,
    blockType: aws_http_header_block,
    userdata: COpaquePointer?,
): Int = userdata?.withDereferenced<HttpStreamContext, _> { ctx ->
    ctx.stream?.let { stream ->
        try {
            ctx.handler.onResponseHeadersDone(stream, blockType.value.toInt())
            AWS_OP_SUCCESS
        } catch (ex: Exception) {
            log(LogLevel.Error, "onResponseHeaderBlockDone: $ex")
            null
        }
    }
} ?: callbackError()

private fun onIncomingBody(
    nativeStream: CPointer<cnames.structs.aws_http_stream>?,
    data: CPointer<aws_byte_cursor>?,
    userdata: COpaquePointer?,
): Int = userdata?.withDereferenced<HttpStreamContext, _> { ctx ->
    ctx.stream?.let { stream ->
        try {
            val body = if (data != null) {
                ByteCursorBuffer(data)
            } else {
                Buffer.Empty
            }
            val windowIncrement = ctx.handler.onResponseBody(stream, body)

            if (windowIncrement < 0) {
                null
            } else {
                if (windowIncrement > 0) {
                    aws_http_stream_update_window(nativeStream, windowIncrement.convert())
                }
                AWS_OP_SUCCESS
            }
        } catch (ex: Exception) {
            log(LogLevel.Error, "onIncomingBody: $ex")
            null
        }
    }
} ?: callbackError()

private fun onStreamComplete(
    nativeStream: CPointer<cnames.structs.aws_http_stream>?,
    errorCode: Int,
    userdata: COpaquePointer?,
) {
    userdata?.withDereferenced<HttpStreamContext>(dispose = true) { ctx ->
        try {
            val stream = ctx.stream ?: return
            ctx.handler.onResponseComplete(stream, errorCode)
        } catch (ex: Exception) {
            log(LogLevel.Error, "onStreamComplete: $ex")
            // close connection if callback throws an exception
            aws_http_connection_close(aws_http_stream_get_connection(nativeStream))
        } finally {
            // cleanup request object
            aws_http_message_release(ctx.nativeReq)
        }
    }
}

