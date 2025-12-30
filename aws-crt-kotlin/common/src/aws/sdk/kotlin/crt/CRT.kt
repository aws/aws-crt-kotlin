/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt

public expect object CRT {
    /**
     * Initialize the CRT libraries if needed
     */
    public fun initRuntime(block: Config.() -> Unit = {})

    /**
     * Returns the last error on the current thread.
     * @return Last error code recorded in this thread
     */
    public fun lastError(): Int

    /**
     * Given an integer error code from an internal operation, return the associated error message.
     * @param errorCode An error code returned from an exception or other native function call
     * @return A user-friendly description of the error
     */
    public fun errorString(errorCode: Int): String?

    /**
     * Given an integer error code from an internal operation, return the associated error name.
     *
     * @param errorCode An error code returned from an exception or other native
     * function call
     * @return A string identifier for the error
     */
    public fun errorName(errorCode: Int): String?

    /**
     * Given an integer HTTP error code from an internal operation, return whether the error is retryable.
     * @param errorCode An error code returned from an exception or other native function call
     * @return True if the given HTTP error is retryable; otherwise, false.
     */
    public fun isHttpErrorRetryable(errorCode: Int): Boolean

    /**
     * @return The number of bytes allocated in native resources. If aws.crt.memory.tracing is 1 or 2, this will
     * be a non-zero value. Otherwise, no tracing will be done, and the value will always be 0
     */
    public fun nativeMemory(): Long

    /**
     * Acquire a reference to the CRT shutdown process. Each call to [acquireShutdownRef] requires a corresponding call
     * to [releaseShutdownRef] when the caller is ready for the CRT to be shut down.
     */
    public fun acquireShutdownRef(): CrtShutdownHandle

    /**
     * Release a reference to the CRT shutdown process.
     * Each call to [acquireShutdownRef] must have a corresponding call to [releaseShutdownRef].
     */
    public fun releaseShutdownRef(handle: CrtShutdownHandle)
}

/**
 * Configuration settings related to the common runtime
 */
public class Config {
    /**
     * The level to log internals at
     */
    public var logLevel: LogLevel = LogLevel.None

    /**
     * The destination to log to
     */
    public var logDestination: LogDestination = LogDestination.Stdout

    /**
     * The filename to write logs to. Required if [LogDestination.File] is specified
     */
    public var logFile: String? = null
}
