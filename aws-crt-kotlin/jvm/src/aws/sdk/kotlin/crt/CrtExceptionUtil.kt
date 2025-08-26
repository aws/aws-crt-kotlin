/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt
import software.amazon.awssdk.crt.CrtRuntimeException as CrtRuntimeExceptionJni

private class CrtJniExceptionWrapper(wrapped: CrtRuntimeExceptionJni) : CrtRuntimeException(wrapped.message, wrapped, wrapped.errorCode)

/**
 * Wrap any CRT JNI call exception that happens in [block] into an instance of the kotlin equivalent
 */
internal fun <T> crtJniCall(block: () -> T): T {
    try {
        return block()
    } catch (ex: CrtRuntimeExceptionJni) {
        throw CrtJniExceptionWrapper(ex)
    }
}

internal suspend fun <T> asyncCrtJniCall(block: suspend () -> T): T {
    try {
        return block()
    } catch (ex: CrtRuntimeExceptionJni) {
        throw CrtJniExceptionWrapper(ex)
    }
}
