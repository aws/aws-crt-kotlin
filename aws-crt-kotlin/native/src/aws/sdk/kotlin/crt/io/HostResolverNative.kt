/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.io

import aws.sdk.kotlin.crt.*
import aws.sdk.kotlin.crt.util.ShutdownChannel
import aws.sdk.kotlin.crt.util.shutdownChannel
import aws.sdk.kotlin.crt.util.toAwsString
import aws.sdk.kotlin.crt.util.toKString
import kotlinx.cinterop.*
import kotlinx.coroutines.channels.Channel
import libcrt.*

@OptIn(ExperimentalForeignApi::class)
public actual class HostResolver private constructor(
    private val elg: EventLoopGroup,
    private val manageElg: Boolean,
    private val maxEntries: Int,
) : WithCrt(),
    NativeHandle<aws_host_resolver>,
    Closeable,
    AsyncShutdown {

    public actual constructor(elg: EventLoopGroup, maxEntries: Int) : this(elg, false, maxEntries)
    public actual constructor(elg: EventLoopGroup) : this(elg, false, DEFAULT_MAX_ENTRIES)
    public actual constructor() : this(EventLoopGroup(), true, DEFAULT_MAX_ENTRIES)

    override val ptr: CPointer<aws_host_resolver>

    private val shutdownCompleteChannel = shutdownChannel()
    private val channelStableRef = StableRef.create(shutdownCompleteChannel)

    init {
        ptr = memScoped {
            val shutdownOpts = cValue<aws_shutdown_callback_options> {
                shutdown_callback_fn = staticCFunction(::onShutdownComplete)
                shutdown_callback_user_data = channelStableRef.asCPointer()
            }

            val resolverOpts = cValue<aws_host_resolver_default_options> {
                el_group = elg.ptr
                shutdown_options = shutdownOpts.ptr
                max_entries = maxEntries.convert()
            }

            checkNotNull(aws_host_resolver_new_default(Allocator.Default, resolverOpts)) {
                "aws_host_resolver_new_default()"
            }
        }
    }

    actual override suspend fun waitForShutdown() {
        shutdownCompleteChannel.receive()
    }

    actual override fun close() {
        aws_host_resolver_release(ptr)
        channelStableRef.dispose()

        if (manageElg) elg.close()
    }

    public suspend fun resolve(hostname: String): List<CrtHostAddress> = memScoped {
        val awsHostname = hostname.toAwsString()
        val resultCallback = staticCFunction(::awsOnHostResolveFn)

        val channel: Channel<Result<List<CrtHostAddress>>> = Channel(Channel.RENDEZVOUS)
        val channelStableRef = StableRef.create(channel)
        val userData = channelStableRef.asCPointer()

        aws_host_resolver_resolve_host(ptr, awsHostname, resultCallback, aws_host_resolver_init_default_resolution_config(), userData)

        return channel.receive().getOrThrow().also {
            aws_string_destroy(awsHostname)
            channelStableRef.dispose()
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun onShutdownComplete(userData: COpaquePointer?) {
    if (userData == null) {
        return
    }
    val stableRef = userData.asStableRef<ShutdownChannel>()
    val ch = stableRef.get()
    ch.trySend(Unit)
    ch.close()
    stableRef.dispose()
}

// implementation of `aws_on_host_resolved_result_fn`: https://github.com/awslabs/aws-c-io/blob/db7a1bddc9a29eca18734d0af189c3924775dcf1/include/aws/io/host_resolver.h#L53C14-L53C44
private fun awsOnHostResolveFn(
    hostResolver: CPointer<aws_host_resolver>?,
    hostName: CPointer<aws_string>?,
    errCode: Int,
    hostAddresses: CPointer<aws_array_list>?, // list of `aws_host_address`
    userData: COpaquePointer?,
): Unit = memScoped {
    if (userData == null) {
        throw CrtRuntimeException("aws_on_host_resolved_result_fn: userData unexpectedly null")
    }

    val stableRef = userData.asStableRef<Channel<Result<List<CrtHostAddress>>>>()
    val channel = stableRef.get()

    try {
        if (errCode != AWS_OP_SUCCESS) {
            throw CrtRuntimeException("aws_on_host_resolved_result_fn", ec = errCode)
        }

        val length = aws_array_list_length(hostAddresses)
        if (length == 0uL) {
            throw CrtRuntimeException("Failed to resolve host address for ${hostName?.toKString()}")
        }

        val addressList = ArrayList<CrtHostAddress>(length.toInt())

        val element = alloc<COpaquePointerVar>()
        for (i in 0uL until length) {
            awsAssertOpSuccess(
                aws_array_list_get_at_ptr(
                    hostAddresses,
                    element.ptr,
                    i,
                ),
            ) { "aws_array_list_get_at_ptr failed at index $i" }

            val elemOpaque = element.value ?: run {
                throw CrtRuntimeException("aws_host_addresses value at index $i unexpectedly null")
            }

            val addr = elemOpaque.reinterpret<aws_host_address>().pointed

            val hostStr = addr.host?.toKString() ?: run {
                throw CrtRuntimeException("aws_host_addresses `host` at index $i unexpectedly null")
            }
            val addressStr = addr.address?.toKString() ?: run {
                throw CrtRuntimeException("aws_host_addresses `address` at index $i unexpectedly null")
            }

            val addressType = when (addr.record_type) {
                aws_address_record_type.AWS_ADDRESS_RECORD_TYPE_A -> AddressType.IpV4
                aws_address_record_type.AWS_ADDRESS_RECORD_TYPE_AAAA -> AddressType.IpV6
                else -> throw CrtRuntimeException("received unsupported aws_host_address `aws_address_record_type`: ${addr.record_type}")
            }

            addressList += CrtHostAddress(host = hostStr, address = addressStr, addressType)
        }

        channel.trySend(Result.success(addressList))
    } catch (e: Exception) {
        channel.trySend(Result.failure(e))
    } finally {
        channel.close()
    }
}

// Minimal wrapper of aws_host_address
// https://github.com/awslabs/aws-c-io/blob/db7a1bddc9a29eca18734d0af189c3924775dcf1/include/aws/io/host_resolver.h#L31
@InternalApi
public data class CrtHostAddress(
    val host: String,
    val address: String,
    val addressType: AddressType,
)

@InternalApi
public enum class AddressType {
    IpV4,
    IpV6,
}
