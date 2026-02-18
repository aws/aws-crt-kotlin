/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.http

import aws.sdk.kotlin.crt.io.requiresTls

/**
 * Configuration options for [Http2StreamManager]
 */
public class Http2StreamManagerOptions internal constructor(builder: Http2StreamManagerOptionsBuilder) {
    /**
     * Connection manager options for underlying connections
     */
    public val connectionManagerOptions: HttpClientConnectionManagerOptions =
        requireNotNull(builder.connectionManagerOptions) { "Connection manager options are required" }

    /**
     * Ideal number of concurrent streams per connection. Stream manager will try to create
     * a new connection if one connection reaches this number.
     */
    public val idealConcurrentStreamsPerConnection: Int = builder.idealConcurrentStreamsPerConnection

    /**
     * Maximum number of concurrent streams per connection. The actual limit will be the minimum
     * of this value and the server's SETTINGS_MAX_CONCURRENT_STREAMS.
     */
    public val maxConcurrentStreamsPerConnection: Int = builder.maxConcurrentStreamsPerConnection

    /**
     * Initial HTTP/2 settings to send when connections are established
     */
    public val initialSettings: List<Http2ConnectionSetting> = builder.initialSettings.toList()

    /**
     * Enable connection-level manual flow control
     */
    public val connectionManualWindowManagement: Boolean = builder.connectionManualWindowManagement

    /**
     * Use HTTP/2 prior knowledge (cleartext HTTP/2 without upgrade)
     */
    public val priorKnowledge: Boolean = builder.priorKnowledge

    /**
     * Close connection when server error (500/502/503/504) is received
     */
    public val closeConnectionOnServerError: Boolean = builder.closeConnectionOnServerError

    /**
     * Period for sending PING frames (milliseconds). 0 disables periodic pings.
     */
    public val connectionPingPeriodMs: Int = builder.connectionPingPeriodMs

    /**
     * Timeout for PING responses (milliseconds)
     */
    public val connectionPingTimeoutMs: Int = builder.connectionPingTimeoutMs

    init {
        val uri = connectionManagerOptions.uri
        val useTls = uri.scheme.requiresTls()

        require(!useTls || !priorKnowledge) {
            "HTTP/2 prior knowledge cannot be used with TLS"
        }

        require(useTls || connectionManagerOptions.tlsContext != null || priorKnowledge) {
            "Prior knowledge must be used for cleartext HTTP/2 connections"
        }

        require(maxConcurrentStreamsPerConnection > 0) {
            "Max concurrent streams per connection must be greater than zero"
        }

        require(idealConcurrentStreamsPerConnection in 1..maxConcurrentStreamsPerConnection) {
            "Ideal concurrent streams must be between 1 and max concurrent streams"
        }
    }

    public companion object {
        public const val DEFAULT_IDEAL_CONCURRENT_STREAMS: Int = 100
        public const val DEFAULT_MAX_CONCURRENT_STREAMS: Int = Int.MAX_VALUE
        public const val DEFAULT_CONNECTION_PING_TIMEOUT_MS: Int = 3000

        public fun build(block: Http2StreamManagerOptionsBuilder.() -> Unit): Http2StreamManagerOptions =
            Http2StreamManagerOptionsBuilder().apply(block).build()
    }
}

/**
 * Builder for [Http2StreamManagerOptions]
 */
public class Http2StreamManagerOptionsBuilder {
    public var connectionManagerOptions: HttpClientConnectionManagerOptions? = null
    public var idealConcurrentStreamsPerConnection: Int = Http2StreamManagerOptions.DEFAULT_IDEAL_CONCURRENT_STREAMS
    public var maxConcurrentStreamsPerConnection: Int = Http2StreamManagerOptions.DEFAULT_MAX_CONCURRENT_STREAMS
    public var connectionManualWindowManagement: Boolean = false
    public var priorKnowledge: Boolean = false
    public var closeConnectionOnServerError: Boolean = false
    public var connectionPingPeriodMs: Int = 0
    public var connectionPingTimeoutMs: Int = Http2StreamManagerOptions.DEFAULT_CONNECTION_PING_TIMEOUT_MS

    internal val initialSettings: MutableList<Http2ConnectionSetting> = mutableListOf()

    /**
     * Add initial HTTP/2 settings
     */
    public fun initialSettings(block: Http2SettingsBuilder.() -> Unit) {
        initialSettings.addAll(Http2SettingsBuilder().apply(block).build())
    }

    public fun build(): Http2StreamManagerOptions = Http2StreamManagerOptions(this)
}

/**
 * Builder for HTTP/2 connection settings
 */
public class Http2SettingsBuilder {
    private val settings = mutableListOf<Http2ConnectionSetting>()

    public fun setting(id: Http2SettingId, value: Long) {
        settings.add(Http2ConnectionSetting(id, value))
    }

    public fun headerTableSize(value: Long) {
        setting(Http2SettingId.HEADER_TABLE_SIZE, value)
    }

    public fun enablePush(enabled: Boolean) {
        setting(Http2SettingId.ENABLE_PUSH, if (enabled) 1 else 0)
    }

    public fun maxConcurrentStreams(value: Long) {
        setting(Http2SettingId.MAX_CONCURRENT_STREAMS, value)
    }

    public fun initialWindowSize(value: Long) {
        setting(Http2SettingId.INITIAL_WINDOW_SIZE, value)
    }

    public fun maxFrameSize(value: Long) {
        setting(Http2SettingId.MAX_FRAME_SIZE, value)
    }

    public fun maxHeaderListSize(value: Long) {
        setting(Http2SettingId.MAX_HEADER_LIST_SIZE, value)
    }

    internal fun build(): List<Http2ConnectionSetting> = settings.toList()
}
