/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.crt.auth.credentials

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull

private val ACCESS_KEY = "ACCESS_KEY".encodeToByteArray()
private val SECRET_ACCESS_KEY = "SECRET_ACCESS_KEY".encodeToByteArray()
private val SESSION_TOKEN = "SESSION_TOKEN".encodeToByteArray()

class StsAssumeRoleCredentialsProviderJVMTest {

    @Test
    fun itAdaptsSdkToCrtCredentialsProviderTypes() {
        val sdkCredentialsProvider = object : CredentialsProvider {
            override suspend fun getCredentials(): Credentials = Credentials(ACCESS_KEY, SECRET_ACCESS_KEY, SESSION_TOKEN)

            override fun close() {}
            override suspend fun waitForShutdown() {}
        }

        val adapted = adapt(sdkCredentialsProvider)

        assertNotNull(adapted)

        val adaptedCreds = adapted.credentials.get()

        assertContentEquals(adaptedCreds.accessKeyId, ACCESS_KEY)
        assertContentEquals(adaptedCreds.secretAccessKey, SECRET_ACCESS_KEY)
        assertContentEquals(adaptedCreds.sessionToken, SESSION_TOKEN)
    }
}
