/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.crt.util.hashing

import aws.sdk.kotlin.crt.CrtRuntimeException
import aws.sdk.kotlin.crt.use
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EcdsaSecp256r1NativeTest {
    private val ecPrivateKey = """
    MHcCAQEEIFDZHUzOG1Pzq+6F0mjMlOSp1syN9LRPBuHMoCFXTcXhoAoGCCqGSM49
    AwEHoUQDQgAE9qhj+KtcdHj1kVgwxWWWw++tqoh7H7UHs7oXh8jBbgF47rrYGC+t
    djiIaHK3dBvvdE7MGj5HsepzLm3Kj91bqA==
    """.trimIndent()
    private val privateKey =
        ecPrivateKey
            .encodeToByteArray()
            .copyOfRange(7, 39) // Extract private key scalar ("d") from EC private key

    @Test
    fun testInitializeAndClose() {
        EcdsaSecp256r1Native(privateKey).close()
    }

    @Test
    fun testSignMessage() {
        EcdsaSecp256r1Native(privateKey).use { ecdsa ->
            val message = "test message".encodeToByteArray()
            val signature = ecdsa.signMessage(message)
            assertTrue(signature.isNotEmpty())
        }
    }

    @Test
    fun testInvalidPrivateKey() {
        assertFailsWith<CrtRuntimeException> {
            EcdsaSecp256r1Native(ByteArray(10)).close()
        }
    }
}
