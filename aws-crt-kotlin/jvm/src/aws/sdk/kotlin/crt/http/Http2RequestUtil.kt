/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.http

import software.amazon.awssdk.crt.http.Http2ClientConnection as Http2ClientConnectionJni
import software.amazon.awssdk.crt.http.Http2ConnectionSetting as Http2ConnectionSettingJni
import software.amazon.awssdk.crt.http.Http2Request as Http2RequestJni
import software.amazon.awssdk.crt.http.HttpHeader as HttpHeaderJni
import software.amazon.awssdk.crt.http.HttpStreamBase as HttpStreamBaseJni
import software.amazon.awssdk.crt.http.HttpStreamBaseResponseHandler as HttpStreamBaseResponseHandlerJni

/**
 * Convert Kotlin Http2Request to JNI Http2Request
 */
internal fun Http2Request.toJni(): Http2RequestJni {
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
 * Convert Kotlin Http2ErrorCode to JNI Http2ErrorCode
 */
internal fun Http2ErrorCode.toJni(): Http2ClientConnectionJni.Http2ErrorCode = Http2ClientConnectionJni.Http2ErrorCode.entries[this.ordinal]

/**
 * Convert Kotlin Http2StreamManagerOptions to JNI Http2StreamManagerOptions
 */
internal fun Http2StreamManagerOptions.toJni(): software.amazon.awssdk.crt.http.Http2StreamManagerOptions {
    val jniOptions = software.amazon.awssdk.crt.http.Http2StreamManagerOptions()

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
        override fun onResponseHeaders(
            stream: HttpStreamBaseJni,
            statusCode: Int,
            blockType: Int,
            headers: Array<out HttpHeaderJni>?,
        ) {
            val ktHeaders = headers?.map { HttpHeader(it.name, it.value) }
            val ktStream = Http2StreamJVM(stream as software.amazon.awssdk.crt.http.Http2Stream)
            handler.onResponseHeaders(ktStream, statusCode, blockType, ktHeaders)
        }

        override fun onResponseHeadersDone(stream: HttpStreamBaseJni, blockType: Int) {
            val ktStream = Http2StreamJVM(stream as software.amazon.awssdk.crt.http.Http2Stream)
            handler.onResponseHeadersDone(ktStream, blockType)
        }

        override fun onResponseBody(stream: HttpStreamBaseJni, bodyBytesIn: ByteArray?): Int {
            if (bodyBytesIn == null) {
                return 0
            }
            val ktStream = Http2StreamJVM(stream as software.amazon.awssdk.crt.http.Http2Stream)
            val buffer = aws.sdk.kotlin.crt.io.byteArrayBuffer(bodyBytesIn)
            return handler.onResponseBody(ktStream, buffer)
        }

        override fun onResponseComplete(stream: HttpStreamBaseJni, errorCode: Int) {
            val ktStream = Http2StreamJVM(stream as software.amazon.awssdk.crt.http.Http2Stream)
            handler.onResponseComplete(ktStream, errorCode)
        }

        override fun onMetrics(stream: HttpStreamBaseJni, metrics: software.amazon.awssdk.crt.http.HttpStreamMetrics) {
            val ktStream = Http2StreamJVM(stream as software.amazon.awssdk.crt.http.Http2Stream)
            handler.onMetrics(ktStream, metrics.toKotlin())
        }
    }
}
