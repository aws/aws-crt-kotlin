/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.crt

/**
 * A mixin class used to ensure CRT is initialized before the class is invoked
 */
public open class WithCrt {
    init {
        CRT.initRuntime { }
    }
}
