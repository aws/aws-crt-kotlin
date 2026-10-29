/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.crt.util

import aws.sdk.kotlin.crt.CrtRuntimeException
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.*

/**
 * C callback signature shared by the CRT shutdown-complete hooks (`void (*)(void *user_data)`)
 */
internal typealias ShutdownCompleteCallback = CPointer<CFunction<(COpaquePointer?) -> Unit>>

/**
 * Owns the lifecycle of a ref-counted CRT object that signals shutdown asynchronously: creation, a single release, and
 * notification once CRT finishes tearing it down.
 *
 * The shutdown callback may run on any thread, possibly synchronously inside [release] or inside a failed native
 * constructor, and often after [close] has returned. So the [StableRef] passed as its user data is never disposed by
 * [close]. It is disposed exactly once, by whichever runs first: that callback, or the failure path here when creation
 * fails.
 *
 * @param name Name of the native constructor, used in the exception message when creation fails
 * @param release Native function which releases this object's reference
 * @param create Creates the native object, wiring the given callback and user data into its shutdown options. The
 * native constructor must be the last step that can throw: if [create] throws after the native object exists, that
 * object is never released.
 * @throws CrtRuntimeException If [create] returns null. Any exception thrown by [create] itself is rethrown.
 */
internal class NativeLifecycle<T : CPointed>(
    name: String,
    private val release: (CPointer<T>) -> Unit,
    create: (callback: ShutdownCompleteCallback, userData: COpaquePointer) -> CPointer<T>?,
) {
    private val closed = atomic(false)
    private val signal = ShutdownSignal()
    private val signalRef = StableRef.create(signal)

    val ptr: CPointer<T> = try {
        create(staticCFunction(::onShutdownComplete), signalRef.asCPointer()) ?: throw CrtRuntimeException("$name()")
    } catch (e: Throwable) {
        // Some constructors (e.g. aws_http_connection_manager_new) invoke the shutdown callback synchronously while
        // cleaning up a partially created object, and that callback may already have disposed the ref
        signal.disposeOnce(signalRef)
        throw e
    }

    /**
     * Release the native object. Subsequent calls do nothing.
     * @return true if this call performed the release
     */
    fun close(): Boolean = closed.compareAndSet(false, true).also { released ->
        if (released) release(ptr)
    }

    /**
     * Suspend until CRT signals shutdown is complete. Returns immediately if it already has.
     */
    suspend fun awaitShutdown() {
        signal.channel.receiveCatching()
    }
}

/**
 * Target of the [StableRef] passed to CRT as shutdown callback user data
 */
private class ShutdownSignal {
    val channel = shutdownChannel()
    private val disposed = atomic(false)
    private val counter = ShutdownRefTracker.active()?.also { it.created() }

    /**
     * Dispose [ref], which must point at this signal, unless it has been disposed already
     */
    fun disposeOnce(ref: StableRef<ShutdownSignal>) {
        if (disposed.compareAndSet(false, true)) {
            ref.dispose()
            counter?.disposed()
        }
    }
}

private fun onShutdownComplete(userData: COpaquePointer?) {
    if (userData == null) return
    val ref = userData.asStableRef<ShutdownSignal>()
    val signal = ref.get()
    signal.channel.trySend(Unit)
    signal.channel.close()
    signal.disposeOnce(ref)
}

/**
 * Counts shutdown [StableRef]s created while it is active that have not been disposed yet
 */
internal class ShutdownRefCounter {
    private val outstanding = atomic(0)

    val count: Int get() = outstanding.value

    fun created() {
        outstanding.incrementAndGet()
    }

    fun disposed() {
        outstanding.decrementAndGet()
    }
}

/**
 * Test instrumentation. While a [ShutdownRefCounter] is active, each new [NativeLifecycle] reports to it, so a test
 * can detect a ref that leaks (count never returns to 0) or is disposed twice (count goes below 0). Lifecycles created
 * before [start] keep reporting to their own counter (or none), so late callbacks from earlier tests can't move the
 * count. Costs one atomic read per lifecycle when inactive.
 */
internal object ShutdownRefTracker {
    private val current = atomic<ShutdownRefCounter?>(null)

    fun active(): ShutdownRefCounter? = current.value

    fun start(): ShutdownRefCounter = ShutdownRefCounter().also { current.value = it }

    fun stop() {
        current.value = null
    }
}
