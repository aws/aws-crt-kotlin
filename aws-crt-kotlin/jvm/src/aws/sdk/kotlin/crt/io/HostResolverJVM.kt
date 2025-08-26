/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.io

import aws.sdk.kotlin.crt.AsyncShutdown
import aws.sdk.kotlin.crt.Closeable
import software.amazon.awssdk.crt.io.HostResolver as HostResolverJni

public actual class HostResolver private constructor(
    private val elg: EventLoopGroup,
    private val manageElg: Boolean,
    maxEntries: Int,
) : Closeable,
    AsyncShutdown {
    internal val jniHr = HostResolverJni(elg.jniElg, maxEntries)

    public actual constructor(elg: EventLoopGroup, maxEntries: Int) : this(elg, false, maxEntries)
    public actual constructor(elg: EventLoopGroup) : this(elg, false, DEFAULT_MAX_ENTRIES)
    public actual constructor() : this(EventLoopGroup(), true, DEFAULT_MAX_ENTRIES)

    actual override fun close() {
        jniHr.close()

        if (manageElg) elg.close()
    }

    actual override suspend fun waitForShutdown() {
        // TODO jni HostResolver is missing a shutdown complete future...
    }
}
