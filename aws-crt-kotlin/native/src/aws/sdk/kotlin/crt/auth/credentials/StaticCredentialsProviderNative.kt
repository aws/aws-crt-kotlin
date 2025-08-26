/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.auth.credentials

/**
 * A credentials provider for a fixed set of credentials
 */
public actual class StaticCredentialsProvider internal actual constructor(builder: StaticCredentialsProviderBuilder) : CredentialsProvider {
    private val credentials = Credentials(builder.accessKeyId!!, builder.secretAccessKey!!, builder.sessionToken)

    public actual companion object {}
    actual override suspend fun getCredentials(): Credentials = credentials
    actual override fun close() { }
    actual override suspend fun waitForShutdown() { }
}
