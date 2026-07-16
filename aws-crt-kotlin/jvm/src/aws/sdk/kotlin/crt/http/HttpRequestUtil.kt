/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.http

import aws.sdk.kotlin.crt.io.Buffer
import aws.sdk.kotlin.crt.io.MutableBuffer
import java.nio.ByteBuffer
import software.amazon.awssdk.crt.http.HttpHeader as HttpHeaderJni
import software.amazon.awssdk.crt.http.HttpRequest as HttpRequestJni
import software.amazon.awssdk.crt.http.HttpRequestBodyStream as HttpRequestBodyStreamJni
import software.amazon.awssdk.crt.http.HttpStream as HttpStreamJni
import software.amazon.awssdk.crt.http.HttpStreamMetrics as HttpStreamMetricsJni
import software.amazon.awssdk.crt.http.HttpStreamResponseHandler as HttpStreamResponseHandlerJni

/**
 * Convert the KMP version of [HttpRequest] into the JNI equivalent
 */
internal fun HttpRequest.into(): HttpRequestJni {
    val jniHeaders = buildList {
        headers.entries().forEach { (key, values) ->
            values.forEach { add(HttpHeaderJni(key, it)) }
        }
    }.toTypedArray()

    val bodyStream = body?.let { JniRequestBodyStream(it) }
    return HttpRequestJni(method, encodedPath, jniHeaders, bodyStream)
}

internal fun HttpStreamResponseHandler.asJniStreamResponseHandler(): HttpStreamResponseHandlerJni {
    val handler = this
    return object : HttpStreamResponseHandlerJni {
        private var cachedJni: HttpStreamJni? = null
        private var ktStream: HttpStreamJVM? = null
        private val bodyBuffer = ReusableByteArrayBuffer()

        private fun stream(jni: HttpStreamJni): HttpStreamJVM {
            if (cachedJni === jni) return ktStream!!
            return HttpStreamJVM(jni).also {
                ktStream = it
                cachedJni = jni
            }
        }

        override fun onResponseHeaders(
            stream: HttpStreamJni,
            statusCode: Int,
            blockType: Int,
            headers: Array<out HttpHeaderJni>?,
        ) {
            val ktHeaders = headers?.map { HttpHeader(it.name, it.value) }
            handler.onResponseHeaders(stream(stream), statusCode, blockType, ktHeaders)
        }

        override fun onResponseHeadersDone(stream: HttpStreamJni, blockType: Int) {
            handler.onResponseHeadersDone(stream(stream), blockType)
        }

        override fun onResponseBody(stream: HttpStreamJni, bodyBytesIn: ByteArray?): Int {
            if (bodyBytesIn == null) return 0
            bodyBuffer.bytes = bodyBytesIn
            return handler.onResponseBody(stream(stream), bodyBuffer)
        }

        override fun onResponseComplete(stream: HttpStreamJni, errorCode: Int) {
            handler.onResponseComplete(stream(stream), errorCode)
            cachedJni = null
            ktStream = null
        }

        override fun onMetrics(stream: HttpStreamJni, metrics: HttpStreamMetricsJni) {
            handler.onMetrics(stream(stream), metrics.toKotlin())
        }
    }
}

/**
 * A reusable [Buffer] implementation that avoids allocating a new wrapper object per body chunk.
 * The backing [bytes] array is swapped on each callback invocation.
 */
internal class ReusableByteArrayBuffer : Buffer {
    var bytes: ByteArray = byteArrayOf()
    override val len: Int get() = bytes.size
    override fun copyTo(dest: ByteArray, offset: Int): Int {
        bytes.copyInto(dest, offset)
        return bytes.size
    }
    override fun readAll(): ByteArray = bytes
}

/**
 * Wrapper around kotlin [HttpRequest] request body stream
 */
internal class JniRequestBodyStream(val ktStream: HttpRequestBodyStream) : HttpRequestBodyStreamJni {
    override fun sendRequestBody(bodyBytesOut: ByteBuffer?): Boolean {
        if (bodyBytesOut == null) return true
        return ktStream.sendRequestBody(MutableBuffer(bodyBytesOut))
    }

    override fun resetPosition(): Boolean = ktStream.resetPosition()
}

/**
 * Convert a JNI HttpRequest back to our KMP version
 */
internal fun HttpRequest.Companion.from(jniRequest: HttpRequestJni): HttpRequest = build {
    method = jniRequest.method
    encodedPath = jniRequest.encodedPath
    headers {
        jniRequest.headers.forEach {
            append(it.name, it.value)
        }
    }

    val jniBodyStream = jniRequest.bodyStream
    if (jniBodyStream != null) {
        if (jniBodyStream is JniRequestBodyStream) {
            body = jniBodyStream.ktStream
        } else {
            // need to fill in support to proxy via an (possibly anonymous) object that implements HttpRequestBodyStream
            TODO("JNI request body stream is not an instance of JniRequestBodyStream - proxying other stream types not implemented yet")
        }
    }
}
