/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.io

import aws.sdk.kotlin.crt.*
import aws.sdk.kotlin.crt.util.NativeLifecycle
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
    private constructor(elg: EventLoopGroup, manageElg: Boolean) : this(elg, manageElg, ownedHostResolver(elg, manageElg), true)
    public actual constructor(elg: EventLoopGroup, hr: HostResolver) : this(elg, false, hr, false)

    private val lifecycle = try {
        NativeLifecycle("aws_client_bootstrap_new", ::aws_client_bootstrap_release) { cb, userData ->
            val opts = cValue<aws_client_bootstrap_options> {
                event_loop_group = elg.ptr
                host_resolver = hr.ptr
                on_shutdown_complete = cb
                user_data = userData
            }

            aws_client_bootstrap_new(Allocator.Default, opts)
        }
    } catch (e: Throwable) {
        if (manageHr) hr.close()
        if (manageElg) elg.close()
        throw e
    }

    override val ptr: CPointer<aws_client_bootstrap> get() = lifecycle.ptr

    /**
     * Suspend until this bootstrap has shut down. A [HostResolver] or [EventLoopGroup] this bootstrap created itself may
     * still be shutting down when this returns.
     */
    actual override suspend fun waitForShutdown() {
        lifecycle.awaitShutdown()
    }

    actual override fun close() {
        if (lifecycle.close()) {
            if (manageHr) hr.close()
            if (manageElg) elg.close()
        }
    }
}

// Runs before the primary constructor, so it must close an owned elg itself if the resolver fails to create
private fun ownedHostResolver(elg: EventLoopGroup, manageElg: Boolean): HostResolver = try {
    HostResolver(elg)
} catch (e: Throwable) {
    if (manageElg) elg.close()
    throw e
}
