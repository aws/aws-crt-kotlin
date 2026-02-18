/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.http

import aws.sdk.kotlin.crt.CrtTest
import aws.sdk.kotlin.crt.io.*
import kotlin.test.Test
import kotlin.test.assertEquals

class Http2StreamManagerOptionsTest : CrtTest() {

    @Test
    fun testBuildOptions() {
        val elg = EventLoopGroup()
        val hr = HostResolver(elg)
        val clientBootstrap = ClientBootstrap(elg, hr)
        
        try {
            val options = Http2StreamManagerOptions.build {
                connectionManagerOptions = HttpClientConnectionManagerOptions.build {
                    uri = Uri.parse("http://localhost")
                    this.clientBootstrap = clientBootstrap
                    socketOptions = SocketOptions()
                }
                idealConcurrentStreamsPerConnection = 50
                maxConcurrentStreamsPerConnection = 100
                priorKnowledge = true
                closeConnectionOnServerError = true
                connectionPingPeriodMs = 5000
                connectionPingTimeoutMs = 3000

                initialSettings {
                    enablePush(false)
                    initialWindowSize(65535)
                    maxConcurrentStreams(100)
                }
            }

            assertEquals(50, options.idealConcurrentStreamsPerConnection)
            assertEquals(100, options.maxConcurrentStreamsPerConnection)
            assertEquals(true, options.priorKnowledge)
            assertEquals(true, options.closeConnectionOnServerError)
            assertEquals(5000, options.connectionPingPeriodMs)
            assertEquals(3000, options.connectionPingTimeoutMs)
            assertEquals(3, options.initialSettings.size)
        } finally {
            clientBootstrap.close()
            hr.close()
            elg.close()
        }
    }

    @Test
    fun testBuildSettings() {
        val builder = Http2SettingsBuilder()
        builder.enablePush(false)
        builder.initialWindowSize(65535)
        builder.maxConcurrentStreams(100)
        builder.maxFrameSize(16384)
        
        val settings = builder.build()
        
        assertEquals(4, settings.size)
        assertEquals(Http2SettingId.ENABLE_PUSH, settings[0].id)
        assertEquals(0, settings[0].value)
        assertEquals(Http2SettingId.INITIAL_WINDOW_SIZE, settings[1].id)
        assertEquals(65535, settings[1].value)
        assertEquals(Http2SettingId.MAX_CONCURRENT_STREAMS, settings[2].id)
        assertEquals(100, settings[2].value)
        assertEquals(Http2SettingId.MAX_FRAME_SIZE, settings[3].id)
        assertEquals(16384, settings[3].value)
    }
}
