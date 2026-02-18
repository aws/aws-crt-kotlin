/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.http

public enum class HttpVersion(public val value: Int) {
    UNKNOWN(0),
    HTTP_1_0(1),
    HTTP_1_1(2),
    HTTP_2(3);

    public companion object {
        public fun fromInt(value: Int): HttpVersion = entries.firstOrNull { it.value == value } ?: UNKNOWN
    }
}
