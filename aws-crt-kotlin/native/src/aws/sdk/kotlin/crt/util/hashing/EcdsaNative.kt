package aws.sdk.kotlin.crt.util.hashing

import aws.sdk.kotlin.crt.Allocator
import aws.sdk.kotlin.crt.awsAssertOpSuccess
import aws.sdk.kotlin.crt.util.asAwsByteCursor
import kotlinx.cinterop.*
import libcrt.*

public fun keyPairFromPrivateKey(privateKey: ByteArray): CPointer<aws_ecc_key_pair>? {
    val cursor = privateKey.usePinned { it.asAwsByteCursor() }
    return aws_ecc_key_pair_new_from_private_key(Allocator.Default, aws_ecc_curve_name.AWS_CAL_ECDSA_P256, cursor)
}

public fun destroyKeyPair(keyPair: CPointer<aws_ecc_key_pair>) {
    aws_ecc_key_pair_release(keyPair)
}

public fun signMessage(keyPair: CPointer<aws_ecc_key_pair>, message: ByteArray): ByteArray {
    val messageCursor = message.usePinned { it.asAwsByteCursor() }
    val signature = ByteArray(256)
    val signatureBuf = signature.usePinned {
        cValue<aws_byte_buf> {
            capacity = signature.size.convert()
            len = 0U
            buffer = it.addressOf(0).reinterpret()
        }
    }
    awsAssertOpSuccess(aws_ecc_key_pair_sign_message(keyPair, messageCursor, signatureBuf)) { "aws_ecc_key_pair_sign_message" }
    return signature.copyOf(signatureBuf.useContents { len.toInt() })
}