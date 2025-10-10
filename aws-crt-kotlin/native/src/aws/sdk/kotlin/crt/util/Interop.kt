/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.crt.util

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.coroutines.channels.Channel

/**
 * Channel used to signal async shutdown from C callback to a suspend fn
 */
internal typealias ShutdownChannel = Channel<Unit>

/**
 * Create a new shutdown notification channel
 */
internal fun shutdownChannel(): ShutdownChannel = Channel(Channel.RENDEZVOUS)

/**
 * Execute [block] using [StableRef], then dispose it.
 */
internal inline fun <T : Any, R> StableRef<T>.use(block: (StableRef<T>) -> R): R {
    try {
        return block(this)
    } finally {
        dispose()
    }
}

internal inline fun <reified T : Any, R> COpaquePointer.withDereferenced(
    dispose: Boolean = false,
    block: (T) -> R,
): R? =
    try {
        val stableRef = asStableRef<T>() // can throw NPE when target type can't be coerced to type arg
        try {
            val value = stableRef.get() // can throw NPE when pointer has been cleaned up by CRT
            block(value)
        } finally {
            if (dispose) {
                stableRef.dispose()
            }
        }
    } catch (_: NullPointerException) {
        null
    }

internal inline fun <reified T : Any> COpaquePointer.withDereferenced(
    dispose: Boolean = false,
    block: (T) -> Unit,
) = withDereferenced<T, Unit>(dispose, block)
