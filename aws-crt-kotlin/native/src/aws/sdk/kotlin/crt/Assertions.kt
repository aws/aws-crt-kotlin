/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt

import kotlinx.cinterop.ExperimentalForeignApi
import libcrt.AWS_OP_SUCCESS

/**
 * Assert that the given [errorCode] return value is `AWS_OP_SUCCESS`. If not, throw [CrtRuntimeException] with the given evaluated
 * [lazyMessage].
 */
@OptIn(ExperimentalForeignApi::class)
internal inline fun awsAssertOpSuccess(errorCode: Int, lazyMessage: () -> String) {
    if (errorCode != AWS_OP_SUCCESS) {
        throw CrtRuntimeException(lazyMessage(), ec = errorCode)
    }
}
