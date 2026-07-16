/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.http

import aws.sdk.kotlin.crt.io.Buffer
import software.amazon.awssdk.crt.http.Http2ConnectionSetting as Http2ConnectionSettingJni
import software.amazon.awssdk.crt.http.Http2Request as Http2RequestJni
import software.amazon.awssdk.crt.http.Http2StreamManagerOptions as Http2StreamManagerOptionsJni
import software.amazon.awssdk.crt.http.HttpHeader as HttpHeaderJni
import software.amazon.awssdk.crt.http.HttpStreamBase as HttpStreamBaseJni
import software.amazon.awssdk.crt.http.HttpStreamBaseResponseHandler as HttpStreamBaseResponseHandlerJni
import software.amazon.awssdk.crt.http.HttpStreamMetrics as HttpStreamMetricsJni

/**
 * Convert Kotlin HttpRequest to JNI Http2Request for HTTP/2 connections
 */
internal fun HttpRequest.toHttp2Jni(): Http2RequestJni {
    val jniHeaders = headers.entries()
        .flatMap { entry -> entry.value.map { HttpHeaderJni(entry.key, it) } }
        .toTypedArray()

    val bodyStream = body?.let { JniRequestBodyStream(it) }
    return Http2RequestJni(jniHeaders, bodyStream)
}

/**
 * Convert Kotlin Http2ConnectionSetting list to JNI format
 */
internal fun List<Http2ConnectionSetting>.toJni(): List<Http2ConnectionSettingJni> = map { Http2ConnectionSettingJni(Http2ConnectionSettingJni.ID.entries[it.id.ordinal], it.value) }

/**
 * Convert Kotlin Http2StreamManagerOptions to JNI Http2StreamManagerOptions
 */
internal fun Http2StreamManagerOptions.toJni(): Http2StreamManagerOptionsJni {
    val jniOptions = Http2StreamManagerOptionsJni()

    jniOptions.withConnectionManagerOptions(connectionManagerOptions.into())
        .withIdealConcurrentStreamsPerConnection(idealConcurrentStreamsPerConnection)
        .withMaxConcurrentStreamsPerConnection(maxConcurrentStreamsPerConnection)
        .withConnectionManualWindowManagement(connectionManualWindowManagement)
        .withPriorKnowledge(priorKnowledge)
        .withCloseConnectionOnServerError(closeConnectionOnServerError)

    if (initialSettings.isNotEmpty()) {
        jniOptions.withInitialSettingsList(initialSettings.toJni())
    }

    if (connectionPingPeriodMs > 0) {
        jniOptions.withConnectionPing(connectionPingPeriodMs, connectionPingTimeoutMs)
    }

    return jniOptions
}

/**
 * Convert Kotlin HttpStreamResponseHandler to JNI HttpStreamBaseResponseHandler for HTTP/2
 */
internal fun HttpStreamResponseHandler.asJniStreamBaseResponseHandler(): HttpStreamBaseResponseHandlerJni {
    val handler = this
    return object : HttpStreamBaseResponseHandlerJni {
        private var cachedJni: HttpStreamBaseJni? = null
        private var ktStream: HttpStreamJVM? = null
        private val bodyBuffer = ReusableByteArrayBuffer()

        private fun stream(jni: HttpStreamBaseJni): HttpStreamJVM {
            if (cachedJni === jni) return ktStream!!
            return HttpStreamJVM(jni).also {
                ktStream = it
                cachedJni = jni
            }
        }

        override fun onResponseHeaders(
            stream: HttpStreamBaseJni,
            statusCode: Int,
            blockType: Int,
            headers: Array<out HttpHeaderJni>?,
        ) {
            val ktHeaders = headers?.map { HttpHeader(it.name, it.value) }
            handler.onResponseHeaders(stream(stream), statusCode, blockType, ktHeaders)
        }

        override fun onResponseHeadersDone(stream: HttpStreamBaseJni, blockType: Int) {
            handler.onResponseHeadersDone(stream(stream), blockType)
        }

        override fun onResponseBody(stream: HttpStreamBaseJni, bodyBytesIn: ByteArray?): Int {
            if (bodyBytesIn == null) return 0
            bodyBuffer.bytes = bodyBytesIn
            return handler.onResponseBody(stream(stream), bodyBuffer)
        }

        override fun onResponseComplete(stream: HttpStreamBaseJni, errorCode: Int) {
            handler.onResponseComplete(stream(stream), errorCode)
            cachedJni = null
            ktStream = null
        }

        override fun onMetrics(stream: HttpStreamBaseJni, metrics: HttpStreamMetricsJni) {
            handler.onMetrics(stream(stream), metrics.toKotlin())
        }
    }
}
