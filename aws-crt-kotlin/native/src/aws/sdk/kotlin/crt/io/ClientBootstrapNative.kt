/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.io

import aws.sdk.kotlin.crt.*
import aws.sdk.kotlin.crt.util.ShutdownChannel
import aws.sdk.kotlin.crt.util.shutdownChannel
import kotlinx.cinterop.*
import libcrt.aws_client_bootstrap
import libcrt.aws_client_bootstrap_new
import libcrt.aws_client_bootstrap_options
import libcrt.aws_client_bootstrap_release

@OptIn(ExperimentalForeignApi::class)
public actual class ClientBootstrap private constructor(
    private val elg: EventLoopGroup,
    private val manageElg: Boolean,
    private val hr: HostResolver,
    private val manageHr: Boolean,
) : WithCrt(),
    NativeHandle<aws_client_bootstrap>,
    Closeable,
    AsyncShutdown {

    public actual constructor() : this(EventLoopGroup(), true)
    private constructor(elg: EventLoopGroup, manageElg: Boolean) : this(elg, manageElg, HostResolver(elg), true)
    public actual constructor(elg: EventLoopGroup, hr: HostResolver) : this(elg, false, hr, false)

    private val shutdownCompleteChannel = shutdownChannel()
    private val channelStableRef = StableRef.create(shutdownCompleteChannel)
    override val ptr: CPointer<aws_client_bootstrap>

    init {
        val opts = cValue<aws_client_bootstrap_options> {
            event_loop_group = elg.ptr
            host_resolver = hr.ptr
            on_shutdown_complete = staticCFunction(::onShutdownComplete)
            user_data = channelStableRef.asCPointer()
        }

        ptr = checkNotNull(aws_client_bootstrap_new(Allocator.Default, opts)) {
            "aws_client_bootstrap_new()"
        }
    }

    actual override suspend fun waitForShutdown() {
        shutdownCompleteChannel.receive()
    }

    actual override fun close() {
        aws_client_bootstrap_release(ptr)
        channelStableRef.dispose()

        if (manageHr) hr.close()
        if (manageElg) elg.close()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun onShutdownComplete(userData: COpaquePointer?) {
    if (userData == null) return
    val stableRef = userData.asStableRef<ShutdownChannel>()
    val ch = stableRef.get()
    ch.trySend(Unit)
    ch.close()
    stableRef.dispose()
}
