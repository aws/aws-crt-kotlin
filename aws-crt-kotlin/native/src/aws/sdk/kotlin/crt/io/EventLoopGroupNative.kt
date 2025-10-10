/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.io

import aws.sdk.kotlin.crt.*
import aws.sdk.kotlin.crt.util.ShutdownChannel
import aws.sdk.kotlin.crt.util.shutdownChannel
import aws.sdk.kotlin.crt.util.withDereferenced
import cnames.structs.aws_event_loop_group
import kotlinx.cinterop.*
import libcrt.aws_event_loop_group_new
import libcrt.aws_event_loop_group_options
import libcrt.aws_event_loop_group_release
import libcrt.aws_shutdown_callback_options

/**
 * Creates a new event loop group for the I/O subsystem to use to run blocking I/O requests
 * This class wraps the aws_event_loop_group from aws-c-io
 *
 * @param maxThreads If maxThreads == 0, then the loop count will be the number of available processors on the machine.
 * Otherwise, maxThreads will be the number of event loops in the group.
 * @throws [aws.sdk.kotlin.crt.CrtRuntimeException] If the system is unable to allocate space for a native event loop group
 */
@OptIn(ExperimentalForeignApi::class)
public actual class EventLoopGroup actual constructor(maxThreads: Int) :
    WithCrt(),
    NativeHandle<aws_event_loop_group>,
    Closeable,
    AsyncShutdown {

    override val ptr: CPointer<aws_event_loop_group>

    private val shutdownCompleteChannel = shutdownChannel()
    private val channelStableRef = StableRef.create(shutdownCompleteChannel)

    init {
        ptr = memScoped {
            val shutdownOpts = cValue<aws_shutdown_callback_options> {
                shutdown_callback_fn = staticCFunction(::onShutdownComplete)
                shutdown_callback_user_data = channelStableRef.asCPointer()
            }

            val eventLoopGroupOpts = cValue<aws_event_loop_group_options> {
                shutdown_options = shutdownOpts.ptr
            }

            checkNotNull(aws_event_loop_group_new(Allocator.Default, eventLoopGroupOpts)) {
                "aws_event_loop_group_new()"
            }
        }
    }

    actual override suspend fun waitForShutdown() {
        shutdownCompleteChannel.receive()
    }

    actual override fun close() {
        aws_event_loop_group_release(ptr)
        channelStableRef.dispose()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun onShutdownComplete(userData: COpaquePointer?) {
    userData?.withDereferenced<ShutdownChannel>(dispose = true) { ch ->
        ch?.trySend(Unit)
        ch?.close()
    }
}
