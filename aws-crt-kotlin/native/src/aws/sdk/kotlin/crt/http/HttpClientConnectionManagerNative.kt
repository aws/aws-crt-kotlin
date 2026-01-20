/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.http

import aws.sdk.kotlin.crt.*
import aws.sdk.kotlin.crt.io.SocketDomain
import aws.sdk.kotlin.crt.io.SocketOptions
import aws.sdk.kotlin.crt.io.SocketType
import aws.sdk.kotlin.crt.io.requiresTls
import aws.sdk.kotlin.crt.util.*
import cnames.structs.aws_http_connection_manager
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.*
import libcrt.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

public actual class HttpClientConnectionManager actual constructor(
    public actual val options: HttpClientConnectionManagerOptions,
) : WithCrt(),
    Closeable,
    AsyncShutdown {

    private val closed = atomic(false)

    public actual val managerMetrics: HttpManagerMetrics
        get() = memScoped {
            val metrics = alloc<aws_http_manager_metrics>()
            aws_http_connection_manager_fetch_metrics(manager, metrics.ptr)
            HttpManagerMetrics(
                availableConcurrency = metrics.available_concurrency.convert(),
                pendingConcurrencyAcquires = metrics.pending_concurrency_acquires.convert(),
                leasedConcurrency = metrics.leased_concurrency.convert(),
            )
        }

    private val manager: CPointer<aws_http_connection_manager>

    private val shutdownComplete = shutdownChannel()
    private val shutdownCompleteStableRef = StableRef.create(shutdownComplete)
    init {
        manager = memScoped {
            val endpointBytes = options.uri.host.encodeToByteArray().pin()
            val endpoint = endpointBytes.asAwsByteCursor()
            defer { endpointBytes.unpin() }

            val tlsConnOpts: aws_tls_connection_options? = if (options.uri.scheme.requiresTls()) {
                val ctx = requireNotNull(options.tlsContext) { "TlsContext is required for https" }
                val opts = alloc<aws_tls_connection_options>()
                aws_tls_connection_options_init_from_ctx(opts.ptr, ctx.ptr)
                aws_tls_connection_options_set_server_name(opts.ptr, Allocator.Default, endpoint)
                defer { aws_tls_connection_options_clean_up(opts.ptr) }
                opts
            } else {
                null
            }

            val monitoringOpts: aws_http_connection_monitoring_options? = options.monitoringOptions?.let {
                val opts = alloc<aws_http_connection_monitoring_options>()
                opts.allowable_throughput_failure_interval_seconds = it.allowableThroughputFailureIntervalSeconds.convert()
                opts.minimum_throughput_bytes_per_second = it.minThroughputBytesPerSecond.convert()
                opts
            }

            val proxyOpts: aws_http_proxy_options? = options.proxyOptions?.let { proxyOptions ->
                val opts = alloc<aws_http_proxy_options>()

                val proxyHost = proxyOptions.host.toAwsString()
                defer { proxyHost.free() }

                opts.host.initFromCursor(proxyHost.asAwsByteCursor())
                opts.port = (proxyOptions.port ?: options.uri.scheme.defaultPort).convert()

                val proxyTlsConnOpts: aws_tls_connection_options? = proxyOptions.tlsContext?.let { tlsCtx ->
                    val tlsOpts = alloc<aws_tls_connection_options>()
                    val proxyEndpoint = proxyHost.asAwsByteCursor()
                    aws_tls_connection_options_init_from_ctx(tlsOpts.ptr, tlsCtx.ptr)
                    aws_tls_connection_options_set_server_name(tlsOpts.ptr, Allocator.Default, proxyEndpoint)

                    defer { aws_tls_connection_options_clean_up(tlsOpts.ptr) }

                    tlsOpts
                }

                opts.tls_options = proxyTlsConnOpts?.ptr

                opts.auth_type = proxyOptions.authType.value.convert()
                proxyOptions.authUsername?.let {
                    val username = it.toAwsString()
                    defer { username.free() }
                    opts.auth_username.initFromCursor(username.asAwsByteCursor())
                }
                proxyOptions.authPassword?.let {
                    val pwd = it.toAwsString()
                    defer { pwd.free() }
                    opts.auth_password.initFromCursor(pwd.asAwsByteCursor())
                }
                opts
            }

            val socketOpts = alloc<aws_socket_options>()
            socketOpts.kinit(options.socketOptions)

            val managerOpts = cValue<aws_http_connection_manager_options> {
                bootstrap = options.clientBootstrap.ptr
                initial_window_size = options.initialWindowSize.convert()
                host.initFromCursor(endpoint)
                port = options.uri.port.convert()

                max_connections = options.maxConnections.convert()
                enable_read_back_pressure = options.manualWindowManagement
                max_connection_idle_in_milliseconds = options.maxConnectionIdleMs.convert()

                shutdown_complete_callback = staticCFunction(::onShutdownComplete)
                shutdown_complete_user_data = shutdownCompleteStableRef.asCPointer()

                tls_connection_options = tlsConnOpts?.ptr
                monitoring_options = monitoringOpts?.ptr
                proxy_options = proxyOpts?.ptr
                socket_options = socketOpts.ptr
            }

            aws_http_connection_manager_new(Allocator.Default, managerOpts)
        } ?: throw CrtRuntimeException("aws_http_connection_manager_new()")
    }

    /**
     * Request an HttpClientConnection from the pool
     */
    public actual suspend fun acquireConnection(): HttpClientConnection = suspendCoroutine { cont ->
        val cb = staticCFunction(::onConnectionAcquired)
        val userdata = StableRef.create(HttpConnectionAcquisitionRequest(cont, this))
        aws_http_connection_manager_acquire_connection(manager, cb, userdata.asCPointer())
    }

    /**
     * Releases this HttpClientConnection back into the Connection Pool, and allows another Request to acquire this connection.
     * @param conn Connection to release
     */
    public actual fun releaseConnection(conn: HttpClientConnection) {
        val nativeConn = conn as HttpClientConnectionNative
        awsAssertOpSuccess(
            aws_http_connection_manager_release_connection(manager, nativeConn.ptr),
        ) { "aws_http_connection_manager_release_connection()" }
    }

    actual override suspend fun waitForShutdown() {
        shutdownComplete.receiveCatching().getOrNull()
    }

    actual override fun close() {
        if (closed.compareAndSet(false, true)) {
            aws_http_connection_manager_release(manager)
            shutdownCompleteStableRef.dispose()
        }
    }
}

// initialize from Kotlin equivalent
private fun aws_socket_options.kinit(opts: SocketOptions) {
    type = opts.type.toNativeSocketType()
    domain = opts.domain.toNativeSocketDomain()
    connect_timeout_ms = opts.connectTimeoutMs.convert()
    keep_alive_interval_sec = opts.keepAliveIntervalSecs.convert()
    keep_alive_timeout_sec = opts.keepAliveTimeoutSecs.convert()
}

private fun SocketType.toNativeSocketType() = when (this) {
    SocketType.STREAM -> aws_socket_type.AWS_SOCKET_STREAM
    SocketType.DGRAM -> aws_socket_type.AWS_SOCKET_DGRAM
}

private fun SocketDomain.toNativeSocketDomain() = when (this) {
    SocketDomain.IPv4 -> aws_socket_domain.AWS_SOCKET_IPV4
    SocketDomain.IPv6 -> aws_socket_domain.AWS_SOCKET_IPV6
    SocketDomain.LOCAL -> aws_socket_domain.AWS_SOCKET_LOCAL
}

private fun onShutdownComplete(userdata: COpaquePointer?) {
    userdata?.withDereferenced<ShutdownChannel>(dispose = true) { notify ->
        notify.trySend(Unit)
        notify.close()
    }
}

private data class HttpConnectionAcquisitionRequest(
    val cont: Continuation<HttpClientConnection>,
    val manager: HttpClientConnectionManager,
)

private fun onConnectionAcquired(
    conn: CPointer<cnames.structs.aws_http_connection>?,
    errCode: Int,
    userdata: COpaquePointer?,
) {
    userdata?.withDereferenced<HttpConnectionAcquisitionRequest>(dispose = true) { request ->
        when {
            errCode != AWS_OP_SUCCESS -> request.cont.resumeWithException(HttpException(errCode))

            conn == null -> request.cont.resumeWithException(
                CrtRuntimeException("acquireConnection(): http connection null", ec = errCode),
            )

            else -> {
                val kconn = HttpClientConnectionNative(request.manager, conn)
                request.cont.resume(kconn)
            }
        }
    }
}
