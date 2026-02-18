package aws.sdk.kotlin.crt.http

public actual class Http2StreamManager actual constructor(options: Http2StreamManagerOptions) :
    aws.sdk.kotlin.crt.Closeable, aws.sdk.kotlin.crt.AsyncShutdown {
    public actual val options: Http2StreamManagerOptions
        get() = TODO("Not yet implemented")

    public actual suspend fun acquireStream(
        request: Http2Request,
        handler: HttpStreamResponseHandler
    ): Http2Stream {
        TODO("Not yet implemented")
    }

    public actual val managerMetrics: HttpManagerMetrics
        get() = TODO("Not yet implemented")

    actual override fun close() {
    }

    actual override suspend fun waitForShutdown() {
    }
}