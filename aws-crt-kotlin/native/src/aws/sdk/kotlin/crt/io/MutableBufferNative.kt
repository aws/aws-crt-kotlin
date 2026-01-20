/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.crt.io

import aws.sdk.kotlin.crt.Allocator
import aws.sdk.kotlin.crt.Closeable
import aws.sdk.kotlin.crt.CrtRuntimeException
import aws.sdk.kotlin.crt.WithCrt
import kotlinx.cinterop.*
import libcrt.*

/**
 * Represents a mutable linear range of bytes that can be written to.
 * Instance of this class has no additional state except the bytes themselves.
 */
@OptIn(ExperimentalForeignApi::class)
public actual class MutableBuffer private constructor(
    private val buffer: InnerBuffer,
) : WithCrt(),
    Closeable {
    internal constructor(borrowed: CPointer<aws_byte_buf>) : this(InnerBuffer.Borrowed(borrowed))

    /**
     * The amount of remaining write capacity before the buffer is full
     */
    public actual val writeRemaining: Int
        get() = (buffer.capacity - buffer.len).toInt()

    /**
     * Write as much of [length] bytes from [src] as possible starting at [offset].
     * The number of bytes written is returned which may be less than [length]
     */
    public actual fun write(src: ByteArray, offset: Int, length: Int): Int {
        src.usePinned { pinnedSrc ->
            val offsetPinnedSrc = pinnedSrc.addressOf(offset).reinterpret<UByteVar>()
            val wc = minOf(length, writeRemaining)
            return if (aws_byte_buf_write(buffer.pointer, offsetPinnedSrc, wc.convert())) wc else 0
        }
    }

    public override fun close() {
        buffer.release()
    }

    public actual companion object {
        /**
         * Create a buffer instance backed by [src]
         */
        public actual fun of(src: ByteArray): MutableBuffer = MutableBuffer(InnerBuffer.KBuffer(src))
    }
}

private sealed interface InnerBuffer {
    val pointer: CPointer<aws_byte_buf>
    fun release() {}

    val capacity: ULong
        get() = pointer.pointed.capacity

    val len: ULong
        get() = pointer.pointed.len

    /**
     * A buffer we don't own
     */
    data class Borrowed(
        override val pointer: CPointer<aws_byte_buf>,
    ) : InnerBuffer

    /**
     * A buffer that is backed by a Kotlin [ByteArray]. All write operations are reflected in the
     * given [dest] array.
     */
    data class KBuffer(
        private val dest: ByteArray,
    ) : WithCrt(),
        InnerBuffer {
        private val pinned = dest.pin()

        override val pointer: CPointer<aws_byte_buf> =
            aws_mem_calloc(
                Allocator.Default,
                1.convert(),
                sizeOf<aws_byte_buf>().convert(),
            )
                ?.reinterpret() ?: throw CrtRuntimeException("aws_mem_calloc() aws_byte_buf")

        init {
            pointer.pointed.len = 0.convert()
            pointer.pointed.capacity = dest.size.convert()
            pointer.pointed.buffer = pinned.takeUnless { dest.isEmpty() }
                ?.addressOf(0)
                ?.reinterpret()
        }

        override fun release() {
            pinned.unpin()
            Allocator.Default.free(pointer)
        }
    }
}
