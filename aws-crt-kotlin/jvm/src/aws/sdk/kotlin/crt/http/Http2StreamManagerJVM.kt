/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.http

import kotlinx.coroutines.future.await
import software.amazon.awssdk.crt.http.Http2StreamManager as Http2StreamManagerJni

/**
 * JVM implementation of Http2StreamManager wrapping aws-crt-java's Http2StreamManager
 */
public actual class Http2StreamManager actual constructor(
    public actual val options: Http2StreamManagerOptions,
) : aws.sdk.kotlin.crt.Closeable,
    aws.sdk.kotlin.crt.AsyncShutdown {

    private val jniManager: Http2StreamManagerJni = Http2StreamManagerJni.create(options.toJni())

    public actual val managerMetrics: HttpManagerMetrics
        get() {
            val jniMetrics = jniManager.managerMetrics
            return HttpManagerMetrics(
                availableConcurrency = jniMetrics.availableConcurrency,
                pendingConcurrencyAcquires = jniMetrics.pendingConcurrencyAcquires,
                leasedConcurrency = jniMetrics.leasedConcurrency,
            )
        }

    public actual suspend fun acquireStream(
        request: Http2Request,
        handler: HttpStreamResponseHandler,
    ): Http2Stream {
        val jniStream = jniManager.acquireStream(request.toJni(), handler.asJniStreamBaseResponseHandler()).await()
        return Http2StreamJVM(jniStream)
    }

    actual override fun close() {
        jniManager.close()
    }

    actual override suspend fun waitForShutdown() {
        jniManager.shutdownCompleteFuture.await()
    }
}
