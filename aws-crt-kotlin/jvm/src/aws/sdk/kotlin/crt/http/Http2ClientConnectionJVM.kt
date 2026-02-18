/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.http

import kotlinx.coroutines.future.await
import software.amazon.awssdk.crt.http.Http2ClientConnection as Http2ClientConnectionJni

/**
 * JVM implementation of Http2ClientConnection wrapping aws-crt-java's Http2ClientConnection
 */
internal class Http2ClientConnectionJVM(private val jniConn: Http2ClientConnectionJni) : Http2ClientConnection {
    override val id: String = jniConn.nativeHandle.toString()
    override val version: HttpVersion = HttpVersion.fromInt(jniConn.version.value)

    override fun makeRequest(httpReq: HttpRequest, handler: HttpStreamResponseHandler): HttpStreamBase = Http2StreamJVM(jniConn.makeRequest(httpReq.into(), handler.asJniStreamBaseResponseHandler()))

    override fun makeRequest(request: Http2Request, handler: HttpStreamResponseHandler): Http2Stream {
        val jniStream = jniConn.makeRequest(request.toJni(), handler.asJniStreamBaseResponseHandler())
        return Http2StreamJVM(jniStream)
    }

    override suspend fun updateSettings(settings: List<Http2ConnectionSetting>) {
        jniConn.updateSettings(settings.toJni()).await()
    }

    override suspend fun sendPing(pingData: ByteArray?): Long = jniConn.sendPing(pingData).await()

    override fun sendGoAway(errorCode: Http2ErrorCode, allowMoreStreams: Boolean, debugData: ByteArray?) {
        jniConn.sendGoAway(errorCode.toJni(), allowMoreStreams, debugData)
    }

    override fun updateConnectionWindow(incrementSize: Long) {
        jniConn.updateConnectionWindow(incrementSize)
    }

    override fun close() = jniConn.close()

    override fun shutdown() = jniConn.shutdown()
}
