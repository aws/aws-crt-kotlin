/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.http

import aws.sdk.kotlin.crt.io.*

/**
 * Configuration options for an [HttpClientConnectionManager] instance
 */
public class HttpClientConnectionManagerOptions internal constructor(
    builder: HttpClientConnectionManagerOptionsBuilder,
) {
    public val uri: Uri = requireNotNull(builder.uri) { "URI is required" }
    public val clientBootstrap: ClientBootstrap = requireNotNull(builder.clientBootstrap) { "ClientBootstrap is required" }
    public val socketOptions: SocketOptions = requireNotNull(builder.socketOptions) { "SocketOptions is required" }
    public val tlsContext: TlsContext? = builder.tlsContext
    public val initialWindowSize: Int = builder.initialWindowSize

    /**
     * Max connections the manager can contain
     */
    public val maxConnections: Int = builder.maxConnections

    public val proxyOptions: HttpProxyOptions? = builder.proxyOptions

    /**
     * Enables manual read back pressure. If false data arrives as fast as possible.
     * See [HttpStream.incrementWindow] documentation.
     */
    public val manualWindowManagement: Boolean = builder.manualWindowManagement
    public val monitoringOptions: HttpMonitoringOptions? = builder.monitoringOptions

    /**
     * If set to a non-zero value, then connections that stay in the pool longer than the specified
     * timeout will be closed automatically.
     */
    public val maxConnectionIdleMs: Long = builder.maxConnectionIdleMs

    init {
        require(initialWindowSize > 0) { "Window size must be > 0" }
        require(maxConnections > 0) { "Max connections must be > 0" }
        require(uri.scheme.isHttp()) { "URI has an unknown scheme: ${uri.scheme}" }

        if (uri.scheme.requiresTls()) {
            requireNotNull(tlsContext) { "TlsContext required by URI scheme: ${uri.scheme}" }
        }
    }

    public companion object {
        public const val DEFAULT_INITIAL_WINDOW_SIZE: Int = 16 * 1024
        public const val DEFAULT_MAX_CONNECTIONS: Int = 2

        public fun build(block: HttpClientConnectionManagerOptionsBuilder.() -> Unit): HttpClientConnectionManagerOptions = HttpClientConnectionManagerOptionsBuilder().apply(block).build()
    }
}

public class HttpClientConnectionManagerOptionsBuilder {
    public fun build(): HttpClientConnectionManagerOptions = HttpClientConnectionManagerOptions(this)

    public var uri: Uri? = null

    public fun uri(block: UriBuilder.() -> Unit) {
        uri = UriBuilder().apply(block).build()
    }

    public var clientBootstrap: ClientBootstrap? = null

    public var socketOptions: SocketOptions? = null

    public var tlsContext: TlsContext? = null

    public var initialWindowSize: Int = HttpClientConnectionManagerOptions.DEFAULT_INITIAL_WINDOW_SIZE

    public var maxConnections: Int = HttpClientConnectionManagerOptions.DEFAULT_MAX_CONNECTIONS

    public var proxyOptions: HttpProxyOptions? = null

    public var manualWindowManagement: Boolean = false

    public var monitoringOptions: HttpMonitoringOptions? = null

    public var maxConnectionIdleMs: Long = 0
}
