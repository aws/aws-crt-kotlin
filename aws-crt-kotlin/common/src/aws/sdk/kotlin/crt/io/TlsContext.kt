/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.io

import aws.sdk.kotlin.crt.Closeable

/**
 * This class wraps the aws_tls_context from aws-c-io to provide
 * access to TLS configuration contexts in the AWS Common Runtime.
 */
public expect class TlsContext(options: TlsContextOptions? = null) : Closeable {
    public companion object {}
    override fun close()
}

/**
 * Build and configure a TLS context in the given [block]
 */
public fun TlsContext.Companion.build(block: TlsContextOptionsBuilder.() -> Unit): TlsContext = TlsContextOptionsBuilder().apply(block).build().let { TlsContext(it) }
