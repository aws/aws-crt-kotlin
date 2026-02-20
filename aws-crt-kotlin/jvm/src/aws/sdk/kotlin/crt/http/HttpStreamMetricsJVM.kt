/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.crt.http

import software.amazon.awssdk.crt.http.HttpStreamMetrics as HttpStreamMetricsJni

/**
 * Convert a CRT JNI metrics object into a Kotlin-native one
 */
public fun HttpStreamMetricsJni.toKotlin(): HttpStreamMetrics = HttpStreamMetrics(
    sendStartTimestampNs = this.sendStartTimestampNs,
    sendEndTimestampNs = this.sendEndTimestampNs,
    sendingDurationNs = this.sendingDurationNs,
    receiveStartTimestampNs = this.receiveStartTimestampNs,
    receiveEndTimestampNs = this.receiveEndTimestampNs,
    receivingDurationNs = this.receivingDurationNs,
    streamId = this.streamId,
)
