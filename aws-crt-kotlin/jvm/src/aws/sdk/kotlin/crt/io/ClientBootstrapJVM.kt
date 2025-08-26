/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.io

import aws.sdk.kotlin.crt.AsyncShutdown
import aws.sdk.kotlin.crt.Closeable
import kotlinx.coroutines.future.await
import software.amazon.awssdk.crt.io.ClientBootstrap as ClientBootstrapJni

public actual class ClientBootstrap private constructor(
    private val elg: EventLoopGroup,
    private val manageElg: Boolean,
    private val hr: HostResolver,
    private val manageHr: Boolean,
) : Closeable,
    AsyncShutdown {

    public actual constructor() : this(EventLoopGroup(), true)

    private constructor(elg: EventLoopGroup, manageElg: Boolean) : this(elg, manageElg, HostResolver(elg), true)

    public actual constructor(elg: EventLoopGroup, hr: HostResolver) : this(elg, false, hr, false)

    internal val jniBootstrap = ClientBootstrapJni(elg.jniElg, hr.jniHr)

    actual override fun close() {
        jniBootstrap.close()

        if (manageHr) hr.close()
        if (manageElg) elg.close()
    }

    actual override suspend fun waitForShutdown() {
        jniBootstrap.shutdownCompleteFuture.await()
    }
}
