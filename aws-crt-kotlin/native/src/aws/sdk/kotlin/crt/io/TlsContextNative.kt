/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.io

import aws.sdk.kotlin.crt.*
import aws.sdk.kotlin.crt.util.asAwsByteCursor
import aws.sdk.kotlin.crt.util.free
import aws.sdk.kotlin.crt.util.toAwsString
import kotlinx.cinterop.*
import libcrt.*

public actual class TlsContext actual constructor(options: TlsContextOptions?) :
    WithCrt(),
    NativeHandle<aws_tls_ctx>,
    Closeable {
    private val tlsCtxOpts: aws_tls_ctx_options = Allocator.Default.alloc()

    public actual companion object {}

    override val ptr: CPointer<aws_tls_ctx>

    init {
        aws_tls_ctx_options_init_default_client(tlsCtxOpts.ptr, Allocator.Default)
        val kopts = options ?: TlsContextOptions.defaultClient()

        try {
            // Certificate file or path will cause an init which overrides other fields, so parse those first
            if (kopts.certificate != null && kopts.privateKey != null) {
                initClientMtls(kopts.certificate, kopts.privateKey)
            } else if (kopts.certificatePath != null && kopts.privateKeyPath != null) {
                initClientMtlsFromPath(kopts.certificatePath, kopts.privateKeyPath)
            }

            if (kopts.caRoot != null) {
                overrideDefaultTrustStore(kopts.caRoot)
            } else if (kopts.caFile != null && kopts.caDir != null) {
                overrideDefaultTrustStoreFromPath(kopts.caFile, kopts.caDir)
            }

            tlsCtxOpts.minimum_tls_version = kopts.minTlsVersion.value.convert()
            tlsCtxOpts.cipher_pref = kopts.tlsCipherPreference.value.convert()
            tlsCtxOpts.verify_peer = kopts.verifyPeer

            if (kopts.alpn.isNotBlank()) {
                awsAssertOpSuccess(aws_tls_ctx_options_set_alpn_list(tlsCtxOpts.ptr, kopts.alpn)) {
                    "aws_tls_ctx_options_set_alpn_list()"
                }
            }
        } catch (ex: CrtRuntimeException) {
            Allocator.Default.free(tlsCtxOpts.rawPtr)
            throw ex
        }

        ptr = aws_tls_client_ctx_new(Allocator.Default, tlsCtxOpts.ptr) ?: run {
            aws_tls_ctx_options_clean_up(tlsCtxOpts.ptr)
            Allocator.Default.free(tlsCtxOpts.rawPtr)
            throw CrtRuntimeException("aws_tls_client_ctx_new()")
        }
    }

    // aws_tls_ctx_options_init_client_mtls()
    private fun initClientMtls(certificate: String, privateKey: String) {
        val cert = certificate.toAwsString()
        val pkey = privateKey.toAwsString()

        try {
            val certCursor = cert.asAwsByteCursor()
            val pkeyCursor = pkey.asAwsByteCursor()

            awsAssertOpSuccess(aws_tls_ctx_options_init_client_mtls(tlsCtxOpts.ptr, Allocator.Default, certCursor, pkeyCursor)) {
                "aws_tls_ctx_options_init_client_mtls()"
            }
        } finally {
            cert.free()
            pkey.free()
        }
    }

    // aws_tls_ctx_options_init_client_mtls_from_path()
    private fun initClientMtlsFromPath(certificatePath: String, privateKeyPath: String) {
        awsAssertOpSuccess(
            aws_tls_ctx_options_init_client_mtls_from_path(tlsCtxOpts.ptr, Allocator.Default, certificatePath, privateKeyPath),
        ) { "aws_tls_ctx_options_init_client_mtls_from_path(): certificatePath: `$certificatePath`; privateKeyPath: $privateKeyPath" }
    }

    // aws_tls_ctx_options_override_default_trust_store()
    private fun overrideDefaultTrustStore(caRoot: String) {
        val ca = caRoot.toAwsString()
        try {
            val caCursor = ca.asAwsByteCursor()
            awsAssertOpSuccess(aws_tls_ctx_options_override_default_trust_store(tlsCtxOpts.ptr, caCursor)) {
                "aws_tls_ctx_options_override_default_trust_store()"
            }
        } finally {
            ca.free()
        }
    }

    // aws_tls_ctx_options_override_default_trust_store_from_path()
    private fun overrideDefaultTrustStoreFromPath(caFile: String?, caPath: String?) {
        awsAssertOpSuccess(aws_tls_ctx_options_override_default_trust_store_from_path(tlsCtxOpts.ptr, caFile, caPath)) {
            "aws_tls_ctx_options_override_default_trust_store_from_path()"
        }
    }

    actual override fun close() {
        aws_tls_ctx_release(ptr)
        aws_tls_ctx_options_clean_up(tlsCtxOpts.ptr)
        Allocator.Default.free(tlsCtxOpts.rawPtr)
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun isCipherSupported(cipher: TlsCipherPreference): Boolean = aws_tls_is_cipher_pref_supported(cipher.value.convert())

@OptIn(ExperimentalForeignApi::class)
internal actual fun isAlpnSupported(): Boolean = aws_tls_is_alpn_available()
