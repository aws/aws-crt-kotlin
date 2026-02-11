/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.crt.http

import aws.sdk.kotlin.crt.Allocator
import aws.sdk.kotlin.crt.CrtRuntimeException
import aws.sdk.kotlin.crt.NativeHandle
import aws.sdk.kotlin.crt.awsAssertOpSuccess
import aws.sdk.kotlin.crt.util.asAwsByteCursor
import aws.sdk.kotlin.crt.util.use
import aws.sdk.kotlin.crt.util.withDereferenced
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.*
import libcrt.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

internal class HttpStreamNative(
    override val ptr: CPointer<cnames.structs.aws_http_stream>,
) : HttpStream,
    NativeHandle<cnames.structs.aws_http_stream> {

    private val closed = atomic(false)

    override val responseStatusCode: Int
        get() {
            return memScoped {
                val status = alloc<IntVar>()
                awsAssertOpSuccess(
                    aws_http_stream_get_incoming_response_status(ptr, status.ptr),
                ) { "aws_http_stream_get_incoming_response_status()" }
                status.value
            }
        }

    override fun incrementWindow(size: Int) {
        aws_http_stream_update_window(ptr, size.convert())
    }

    override fun activate() {
        awsAssertOpSuccess(
            aws_http_stream_activate(ptr),
        ) { "aws_http_stream_activate()" }
    }

    override suspend fun writeChunk(chunkData: ByteArray, isFinalChunk: Boolean) {
        suspendCoroutine { cont ->
            val byteBuf: CPointer<aws_byte_buf> =
                aws_mem_calloc(
                    Allocator.Default,
                    1.convert(),
                    sizeOf<aws_byte_buf>().convert(),
                )?.reinterpret() ?: throw CrtRuntimeException("writeChunkData: aws_mem_calloc()")

            chunkData.usePinned {
                awsAssertOpSuccess(
                    aws_byte_buf_init_copy_from_cursor(byteBuf, Allocator.Default, it.asAwsByteCursor()),
                ) { "aws_byte_buf_init_copy_from_cursor()" }
            }

            val chunkCur = aws_byte_cursor_from_buf(byteBuf)
            val stream = aws_input_stream_new_from_cursor(Allocator.Default, chunkCur) ?: run {
                Allocator.Default.free(byteBuf)
                throw CrtRuntimeException("aws_input_stream_new_from_cursor()")
            }

            val req = WriteChunkRequest(cont, byteBuf, stream)
            StableRef.create(req).use { stableRef ->
                val chunkOpts = cValue<aws_http1_chunk_options> {
                    chunk_data_size = chunkData.size.convert()
                    chunk_data = stream
                    on_complete = staticCFunction(::onWriteChunkComplete)
                    user_data = stableRef.asCPointer()
                }
                awsAssertOpSuccess(
                    aws_http1_stream_write_chunk(ptr, chunkOpts),
                ) {
                    cleanupWriteChunkCbData(req)
                    "aws_http1_stream_write_chunk()"
                }
            }
        }

        if (isFinalChunk) {
            val chunkOpts = cValue<aws_http1_chunk_options> {
                chunk_data_size = 0.convert()
            }

            awsAssertOpSuccess(
                aws_http1_stream_write_chunk(ptr, chunkOpts),
            ) { "aws_http_1_stream_write_chunk(): final chunk" }
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            aws_http_stream_release(ptr)
        }
    }
}

private data class WriteChunkRequest(
    val cont: Continuation<Unit>,
    val chunkData: CPointer<aws_byte_buf>,
    val inputStream: CPointer<aws_input_stream>,
)

private fun onWriteChunkComplete(
    stream: CPointer<cnames.structs.aws_http_stream>?,
    errCode: Int,
    userData: COpaquePointer?,
) {
    if (stream == null) return
    userData?.withDereferenced<WriteChunkRequest> { req ->
        checkNotNull(req) { "Received null request in onWriteChunkComplete" }
        when {
            errCode != AWS_OP_SUCCESS -> req.cont.resumeWithException(HttpException(errCode))
            else -> req.cont.resume(Unit)
        }
        cleanupWriteChunkCbData(req)
    }
}

private fun cleanupWriteChunkCbData(req: WriteChunkRequest) {
    aws_input_stream_destroy(req.inputStream)
    aws_byte_buf_clean_up(req.chunkData)
    Allocator.Default.free(req.chunkData)
}
