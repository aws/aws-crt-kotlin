/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import software.amazon.awssdk.crt.Log
import software.amazon.awssdk.crt.http.HttpClientConnection
import software.amazon.awssdk.crt.http.HttpException
import software.amazon.awssdk.crt.CRT as crtJni

public actual object CRT {
    private var initialized = false
    private val initializerMu = Mutex() // protects `initialized`

    public actual fun initRuntime(block: Config.() -> Unit) {
        if (initialized) {
            return
        }

        runBlocking {
            initializerMu.withLock {
                if (initialized) {
                    return@runBlocking
                }

                System.setProperty("aws.crt.memory.tracing", "${CrtDebug.traceLevel}")
                // load the JNI library
                crtJni()
                val config = Config().apply(block)
                val logLevel = Log.LogLevel.valueOf(config.logLevel.name)
                when (config.logDestination) {
                    LogDestination.None -> return@runBlocking
                    LogDestination.Stdout -> Log.initLoggingToStdout(logLevel)
                    LogDestination.Stderr -> Log.initLoggingToStderr(logLevel)
                    LogDestination.File -> {
                        val logfile = config.logFile
                        requireNotNull(logfile) { "log filename must be specified when LogDestination.File is specified" }
                        Log.initLoggingToFile(logLevel, logfile)
                    }
                }
                initialized = true
            }
        }
    }

    /**
     * Returns the last error on the current thread.
     * @return Last error code recorded in this thread
     */
    public actual fun lastError(): Int = crtJni.awsLastError()

    /**
     * Given an integer error code from an internal operation
     * @param errorCode An error code returned from an exception or other native function call
     * @return A user-friendly description of the error
     */
    public actual fun errorString(errorCode: Int): String? = crtJni.awsErrorString(errorCode)

    /**
     * Given an integer error code from an internal operation
     *
     * @param errorCode An error code returned from an exception or other native
     * function call
     * @return A string identifier for the error
     */
    public actual fun errorName(errorCode: Int): String? = crtJni.awsErrorName(errorCode)

    public actual fun isHttpErrorRetryable(errorCode: Int): Boolean {
        // An exception subtype that doesn't create a stack, which saves a bunch of time
        class StacklessHttpException(errorCode: Int) : HttpException(errorCode) {
            // Changing `fillInStackTrace` to a no-op skips filling in the stack
            override fun fillInStackTrace(): Throwable = this
        }

        val phonyException = StacklessHttpException(errorCode)
        return HttpClientConnection.isErrorRetryable(phonyException)
    }

    /**
     * @return The number of bytes allocated in native resources. If aws.crt.memory.tracing is 1 or 2, this will
     * be a non-zero value. Otherwise, no tracing will be done, and the value will always be 0
     */
    public actual fun nativeMemory(): Long = crtJni.nativeMemory()

    public actual fun acquireShutdownRef() {
        crtJni.acquireShutdownRef()
    }

    public actual fun releaseShutdownRef() {
        crtJni.releaseShutdownRef()
    }
}
