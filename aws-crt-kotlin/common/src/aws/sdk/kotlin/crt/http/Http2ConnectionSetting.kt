/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.http

/**
 * Predefined settings identifiers (RFC-7540 6.5.2)
 */
public enum class Http2SettingId(public val value: Int) {
    HEADER_TABLE_SIZE(1),
    ENABLE_PUSH(2),
    MAX_CONCURRENT_STREAMS(3),
    INITIAL_WINDOW_SIZE(4),
    MAX_FRAME_SIZE(5),
    MAX_HEADER_LIST_SIZE(6),
}

/**
 * Represents an HTTP/2 connection setting
 */
public data class Http2ConnectionSetting(
    val id: Http2SettingId,
    val value: Long,
)
