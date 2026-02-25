/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.util.hashing

import aws.sdk.kotlin.crt.Allocator
import aws.sdk.kotlin.crt.Closeable
import aws.sdk.kotlin.crt.CrtRuntimeException
import aws.sdk.kotlin.crt.WithCrt
import aws.sdk.kotlin.crt.awsAssertOpSuccess
import aws.sdk.kotlin.crt.util.asAwsByteCursor
import kotlinx.cinterop.*
import libcrt.*

/**
 * Provides ECDSA on the SECP256R1 curve functionality backed by CRT implementation.
 */
public class EcdsaSecp256r1Native(
    privateKey: ByteArray,
) : WithCrt(),
    Closeable {

    private val eccKeyPair = privateKey.usePinned { pinnedPrivateKey ->
        aws_ecc_key_pair_new_from_private_key(
            Allocator.Default,
            aws_ecc_curve_name.AWS_CAL_ECDSA_P256,
            pinnedPrivateKey.asAwsByteCursor(),
        ) ?: throw CrtRuntimeException("Failed to create ECC key pair from private key")
    }

    override fun close() {
        aws_ecc_key_pair_release(eccKeyPair)
    }

    public fun signMessage(message: ByteArray): ByteArray = memScoped {
        val signature = ByteArray(
            aws_ecc_key_pair_signature_length(eccKeyPair).convert(),
        )

        return signature.usePinned { pinnedSignature ->
            val signatureBuf = alloc<aws_byte_buf>()
            signatureBuf.capacity = signature.size.convert()
            signatureBuf.len = 0U
            signatureBuf.buffer = pinnedSignature.addressOf(0).reinterpret()

            message.usePinned { pinnedMessage ->
                awsAssertOpSuccess(
                    aws_ecc_key_pair_sign_message(
                        eccKeyPair,
                        pinnedMessage.asAwsByteCursor(),
                        signatureBuf.ptr,
                    ),
                ) { "Unable to sign message with key pair" }
            }

            signature
        }
    }
}
