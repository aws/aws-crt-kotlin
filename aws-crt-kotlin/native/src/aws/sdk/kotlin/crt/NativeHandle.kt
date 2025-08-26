/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.crt

import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer

/**
 * Interface for a type that provides a native handle (pointer) to a CRT type
 */
internal interface NativeHandle<T : CPointed> {
    val ptr: CPointer<T>
}
