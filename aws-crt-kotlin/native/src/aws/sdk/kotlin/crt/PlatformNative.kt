/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.crt

import kotlinx.cinterop.*
import libcrt.aws_date_time
import libcrt.aws_date_time_init_now

@OptIn(ExperimentalForeignApi::class)
internal actual object Platform {
    /**
     * Get an environment variable by name
     */
    internal actual fun getenv(name: String): String? = platform.posix.getenv(name)?.toKString()

    /**
     * Get the current time in epoch milliseconds
     */
    internal actual fun epochMilliNow(): Long = memScoped {
        val dt = cValue<aws_date_time>()
        aws_date_time_init_now(dt)
        dt.ptr.pointed.timestamp
    }
}
