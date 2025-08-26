/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.io

import aws.sdk.kotlin.crt.AsyncShutdown
import aws.sdk.kotlin.crt.Closeable

/**
 * This class wraps the aws_client_bootstrap from aws-c-io to provide
 * a client context for all protocol stacks in the AWS Common Runtime.
 */
public expect class ClientBootstrap(elg: EventLoopGroup, hr: HostResolver) :
    Closeable,
    AsyncShutdown {
    public constructor()

    override suspend fun waitForShutdown()
    override fun close()
}
