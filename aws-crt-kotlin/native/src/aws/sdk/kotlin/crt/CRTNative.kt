/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt

import kotlinx.cinterop.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import libcrt.*
import platform.posix.atexit

private val shutdownHandleManager = ShutdownHandleManager()
private val handle = runBlocking { shutdownHandleManager.acquire() }
private fun atexitHandler() {
    runBlocking { CRT.releaseShutdownRef(handle) }
}

@OptIn(ExperimentalForeignApi::class)
public actual object CRT {
    private var initialized = false
    private val initializerMu = Mutex() // protects `initialized`

    /**
     * Initialize the CRT libraries if needed
     * @param block used to configure CRT [Config]
     */
    public actual fun initRuntime(block: Config.() -> Unit): Unit = runBlocking {
        initializerMu.withLock {
            if (initialized) {
                return@runBlocking
            }

            val config = Config().apply(block)

            // bootstrap our allocator
            s_crt_kotlin_init_allocator(CrtDebug.traceLevel)

            aws_common_library_init(Allocator.Default)
            aws_compression_library_init(Allocator.Default)
            aws_io_library_init(Allocator.Default)
            aws_http_library_init(Allocator.Default)
            aws_auth_library_init(Allocator.Default)
            aws_cal_library_init(Allocator.Default)

            Logging.initialize(config)
            aws_register_log_subject_info_list(s_crt_log_subject_list.ptr)
            atexit(staticCFunction(::atexitHandler))

            initialized = true
        }
    }

    /**
     * Returns the last error on the current thread.
     * @return Last error code recorded in this thread
     */
    public actual fun lastError(): Int = aws_last_error()

    /**
     * Given an integer error code from an internal operation, return the user-friendly description of the error.
     * @param errorCode An error code returned from an exception or other native function call
     * @return A user-friendly description of the error, or null if one does not exist
     */
    public actual fun errorString(errorCode: Int): String? = aws_error_str(errorCode)?.toKString()

    /**
     * Given an integer error code from an internal operation, return the name for the error.
     *
     * @param errorCode An error code returned from an exception or other native function call
     * @return A string identifier for the error
     */
    public actual fun errorName(errorCode: Int): String? = aws_error_name(errorCode)?.toKString()

    /**
     * Return whether a given [errorCode] is retryable.
     * @param errorCode An error code returned from an exception or other native call.
     * @return A boolean representing whether this error is retryable or not.
     */
    public actual fun isHttpErrorRetryable(errorCode: Int): Boolean = // see https://github.com/awslabs/aws-crt-java/blob/v0.29.10/src/native/http_request_response.c#L792
        when (errorCode.toUInt()) {
            AWS_ERROR_HTTP_HEADER_NOT_FOUND,
            AWS_ERROR_HTTP_INVALID_HEADER_FIELD,
            AWS_ERROR_HTTP_INVALID_HEADER_NAME,
            AWS_ERROR_HTTP_INVALID_HEADER_VALUE,
            AWS_ERROR_HTTP_INVALID_METHOD,
            AWS_ERROR_HTTP_INVALID_PATH,
            AWS_ERROR_HTTP_INVALID_STATUS_CODE,
            AWS_ERROR_HTTP_MISSING_BODY_STREAM,
            AWS_ERROR_HTTP_INVALID_BODY_STREAM,
            AWS_ERROR_HTTP_OUTGOING_STREAM_LENGTH_INCORRECT,
            AWS_ERROR_HTTP_CALLBACK_FAILURE,
            AWS_ERROR_HTTP_STREAM_MANAGER_SHUTTING_DOWN,
            AWS_HTTP2_ERR_CANCEL,
            -> false

            else -> true
        }

    /**
     * @return The number of bytes allocated in native resources. If aws.crt.memory.tracing is 1 or 2, this will
     * be a non-zero value. Otherwise, no tracing will be done, and the value will always be 0
     */
    public actual fun nativeMemory(): Long = if (CrtDebug.traceLevel > 0) {
        aws_mem_tracer_bytes(Allocator.Default).convert<Long>()
    } else {
        0
    }

    public actual suspend fun acquireShutdownRef(): CrtShutdownHandle = shutdownHandleManager.acquire().also {
        log(LogLevel.Trace, "Vending CRT shutdown handle $it")
    }

    public actual suspend fun releaseShutdownRef(handle: CrtShutdownHandle) {
        if (shutdownHandleManager.release(handle)) {
            log(LogLevel.Trace, "Released CRT shutdown handle $handle")
        } else {
            log(LogLevel.Warn, "CRT shutdown handle $handle does not exist and may have already been released!")
        }

        if (!shutdownHandleManager.hasActiveHandles) {
            cleanup()
        }
    }
}

/**
 * Clean up CRT resources after K/N runtime has been released.
 */
private fun cleanup() {
    aws_unregister_log_subject_info_list(s_crt_log_subject_list.ptr)
    aws_http_library_clean_up()
    aws_compression_library_clean_up()
    aws_io_library_clean_up()
    aws_common_library_clean_up()
    aws_auth_library_clean_up()
    aws_cal_library_clean_up()

    s_crt_kotlin_clean_up()
}
