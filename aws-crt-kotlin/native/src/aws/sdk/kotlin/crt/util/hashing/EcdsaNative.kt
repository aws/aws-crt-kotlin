/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.util.hashing

import aws.sdk.kotlin.crt.Allocator
import aws.sdk.kotlin.crt.awsAssertOpSuccess
import aws.sdk.kotlin.crt.util.asAwsByteCursor
import kotlinx.cinterop.*
import libcrt.*

public fun eccKeyPairFromPrivateKey(privateKey: ByteArray): CPointer<aws_ecc_key_pair>? = aws_ecc_key_pair_new_from_private_key(
    Allocator.Default,
    aws_ecc_curve_name.AWS_CAL_ECDSA_P256,
    privateKey.usePinned { it.asAwsByteCursor() },
)

public fun releaseEccKeyPair(keyPair: CPointer<aws_ecc_key_pair>) {
    aws_ecc_key_pair_release(keyPair)
}

public fun signMessage(keyPair: CPointer<aws_ecc_key_pair>, message: ByteArray): ByteArray = memScoped {
    val signature = ByteArray(
        aws_ecc_key_pair_signature_length(keyPair).convert(),
    )

    return signature.usePinned { pinned ->
        val signatureBuf = alloc<aws_byte_buf>()
        signatureBuf.capacity = signature.size.convert()
        signatureBuf.len = 0U
        signatureBuf.buffer = pinned.addressOf(0).reinterpret()

        awsAssertOpSuccess(
            aws_ecc_key_pair_sign_message(
                keyPair,
                message.usePinned { it.asAwsByteCursor() },
                signatureBuf.ptr,
            ),
        ) { "Unable to sign message with key pair" }

        signature
    }
}
