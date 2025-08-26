/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.auth.signing

import aws.sdk.kotlin.crt.*
import aws.sdk.kotlin.crt.auth.credentials.Credentials
import aws.sdk.kotlin.crt.http.*
import aws.sdk.kotlin.crt.util.asAwsByteCursor
import aws.sdk.kotlin.crt.util.initFromCursor
import aws.sdk.kotlin.crt.util.toAwsString
import aws.sdk.kotlin.crt.util.toKString
import aws.sdk.kotlin.crt.util.use
import kotlinx.cinterop.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import libcrt.*
import platform.posix.UINT64_MAX

/**
 * Static class for a variety of AWS signing APIs.
 */
public actual object AwsSigner : WithCrt() {
    public actual suspend fun signRequest(
        request: HttpRequest,
        config: AwsSigningConfig,
    ): HttpRequest = checkNotNull(sign(request, config).signedRequest) { "received null signed request" }

    public actual suspend fun sign(
        request: HttpRequest,
        config: AwsSigningConfig,
    ): AwsSigningResult = memScoped {
        request.toNativeRequest().usePinned { nativeRequest ->
            // Pair of HTTP request and callback channel containing the signature
            val userData = nativeRequest to Channel<ByteArray>(1)
            val userDataStableRef = StableRef.create(userData)

            val signable = checkNotNull(
                aws_signable_new_http_request(
                    allocator = Allocator.Default.allocator,
                    request = nativeRequest.get(),
                ),
            ) { "aws_signable_new_http_request" }

            val nativeSigningConfig: CPointer<aws_signing_config_base> = config.toNativeSigningConfig().reinterpret()

            awsAssertOpSuccess(
                aws_sign_request_aws(
                    allocator = Allocator.Default.allocator,
                    signable = signable,
                    base_config = nativeSigningConfig,
                    on_complete = staticCFunction(::signCallback),
                    userdata = userDataStableRef.asCPointer(),
                ),
            ) { "sign() aws_sign_request_aws" }

            val callbackChannel = userDataStableRef.get().second
            val signature = callbackChannel.receive() // wait for async signing to complete....
            return AwsSigningResult(nativeRequest.get().toHttpRequest(), signature).also {
                userDataStableRef.dispose()
                callbackChannel.close()
            }
        }
    }

    public actual suspend fun signChunk(
        chunkBody: ByteArray,
        prevSignature: ByteArray,
        config: AwsSigningConfig,
    ): AwsSigningResult {
        val chunkSignable = memScoped {
            chunkBody.usePinned { chunkBodyPinned ->
                val chunkInputStream: CValuesRef<aws_input_stream> = checkNotNull(
                    aws_input_stream_new_from_cursor(
                        Allocator.Default.allocator,
                        chunkBodyPinned.asAwsByteCursor(),
                    ),
                ) { "signChunk() aws_input_stream_new_from_cursor" }

                prevSignature.usePinned { prevSignaturePinned ->
                    checkNotNull(
                        aws_signable_new_chunk(Allocator.Default.allocator, chunkInputStream, prevSignaturePinned.asAwsByteCursor()),
                    ) { "aws_signable_new_chunk unexpectedly null" }
                }
            }
        }

        return signChunkSignable(chunkSignable, config)
    }

    public actual suspend fun signChunkTrailer(
        trailingHeaders: Headers,
        prevSignature: ByteArray,
        config: AwsSigningConfig,
    ): AwsSigningResult {
        val chunkTrailerSignable = memScoped {
            val nativeTrailingHeaders = aws_http_headers_new(Allocator.Default.allocator)
            trailingHeaders.forEach { key, values ->
                key.encodeToByteArray().usePinned { keyPinned ->
                    val keyCursor = keyPinned.asAwsByteCursor()
                    values.forEach {
                        it.encodeToByteArray().usePinned { valuePinned ->
                            val valueCursor = valuePinned.asAwsByteCursor()
                            awsAssertOpSuccess(aws_http_headers_add(nativeTrailingHeaders, keyCursor, valueCursor)) {
                                "signChunkTrailer() aws_http_headers_add"
                            }
                        }
                    }
                }
            }

            checkNotNull(
                aws_signable_new_trailing_headers(
                    Allocator.Default.allocator,
                    nativeTrailingHeaders,
                    prevSignature.usePinned { it.asAwsByteCursor() },
                ),
            ) { "aws_signable_new_trailing_headers unexpectedly null" }
        }

        return signChunkSignable(chunkTrailerSignable, config)
    }
}

private fun signChunkSignable(signable: CPointer<aws_signable>, config: AwsSigningConfig): AwsSigningResult = memScoped {
    val callbackChannel = Channel<ByteArray>(1)
    val callbackChannelStableRef = StableRef.create(callbackChannel)

    val nativeConfig: CPointer<aws_signing_config_base> = config.toNativeSigningConfig().reinterpret()

    awsAssertOpSuccess(
        aws_sign_request_aws(
            allocator = Allocator.Default.allocator,
            signable = signable,
            base_config = nativeConfig,
            on_complete = staticCFunction(::signChunkCallback),
            userdata = callbackChannelStableRef.asCPointer(),
        ),
    ) { "aws_sign_request_aws() failed in signChunkSignable" }

    // wait for async signing to complete....
    val signature = runBlocking { callbackChannel.receive() }.also {
        callbackChannelStableRef.dispose()
        callbackChannel.close()
    }

    return AwsSigningResult(null, signature)
}

/**
 * Get the signature of a given [aws_signing_result].
 * The signature is found in the "signature" property on the signing result.
 */
private fun CPointer<aws_signing_result>.getSignature(): ByteArray {
    val signature = Allocator.Default.allocPointerTo<aws_string>()
    val propertyName = "signature".toAwsString()

    awsAssertOpSuccess(aws_signing_result_get_property(this, propertyName, signature.ptr)) {
        "aws_signing_result_get_property"
    }

    val kSignature = signature.value?.toKString()?.encodeToByteArray()
    return checkNotNull(kSignature) { "signature was null" }
}

private fun AwsSigningAlgorithm.toNativeSigningAlgorithm(): aws_signing_algorithm = when (this) {
    AwsSigningAlgorithm.SIGV4 -> aws_signing_algorithm.AWS_SIGNING_ALGORITHM_V4
    AwsSigningAlgorithm.SIGV4_ASYMMETRIC -> aws_signing_algorithm.AWS_SIGNING_ALGORITHM_V4_ASYMMETRIC
    AwsSigningAlgorithm.SIGV4_S3EXPRESS -> aws_signing_algorithm.AWS_SIGNING_ALGORITHM_V4_S3EXPRESS
}

private fun AwsSignatureType.toNativeSignatureType(): aws_signature_type = when (this) {
    AwsSignatureType.HTTP_REQUEST_VIA_HEADERS -> aws_signature_type.AWS_ST_HTTP_REQUEST_HEADERS
    AwsSignatureType.HTTP_REQUEST_VIA_QUERY_PARAMS -> aws_signature_type.AWS_ST_HTTP_REQUEST_QUERY_PARAMS
    AwsSignatureType.HTTP_REQUEST_EVENT -> aws_signature_type.AWS_ST_HTTP_REQUEST_EVENT
    AwsSignatureType.HTTP_REQUEST_CHUNK -> aws_signature_type.AWS_ST_HTTP_REQUEST_CHUNK
    AwsSignatureType.HTTP_REQUEST_TRAILING_HEADERS -> aws_signature_type.AWS_ST_HTTP_REQUEST_TRAILING_HEADERS
}

private fun AwsSignedBodyHeaderType.toNativeSignedBodyHeaderType() = when (this) {
    AwsSignedBodyHeaderType.NONE -> aws_signed_body_header_type.AWS_SBHT_NONE
    AwsSignedBodyHeaderType.X_AMZ_CONTENT_SHA256 -> aws_signed_body_header_type.AWS_SBHT_X_AMZ_CONTENT_SHA256
}

private fun AwsSigningConfig.toNativeSigningConfig(): CPointer<aws_signing_config_aws> {
    val config = Allocator.Default.alloc<aws_signing_config_aws>()

    config.apply {
        config_type = AWS_SIGNING_CONFIG_AWS
        algorithm = this@toNativeSigningConfig.algorithm.toNativeSigningAlgorithm()
        signature_type = this@toNativeSigningConfig.signatureType.toNativeSignatureType()
        region.initFromCursor(this@toNativeSigningConfig.region.toAwsString().asAwsByteCursor())
        service.initFromCursor(this@toNativeSigningConfig.service.toAwsString().asAwsByteCursor())
        aws_date_time_init_epoch_millis(date.ptr, this@toNativeSigningConfig.date.toULong())

        this@toNativeSigningConfig.shouldSignHeader?.let {
            val shouldSignHeaderStableRef = StableRef.create(it)
            should_sign_header = staticCFunction(::nativeShouldSignHeaderFn)
            should_sign_header_ud = shouldSignHeaderStableRef.asCPointer()
        }

        flags.use_double_uri_encode = if (this@toNativeSigningConfig.useDoubleUriEncode) 1u else 0u
        flags.should_normalize_uri_path = if (this@toNativeSigningConfig.normalizeUriPath) 1u else 0u
        flags.omit_session_token = if (this@toNativeSigningConfig.omitSessionToken) 1u else 0u

        this@toNativeSigningConfig.signedBodyValue?.let {
            signed_body_value.initFromCursor(it.toAwsString().asAwsByteCursor())
        }
        signed_body_header = this@toNativeSigningConfig.signedBodyHeader.toNativeSignedBodyHeaderType()

        credentials = this@toNativeSigningConfig.credentials?.toNativeCredentials()
        // TODO implement native CredentialsProvider
        // credentials_provider =

        expiration_in_seconds = this@toNativeSigningConfig.expirationInSeconds.toULong()
    }

    return config.ptr
}

private typealias ShouldSignHeaderFunction = (String) -> Boolean
private fun nativeShouldSignHeaderFn(headerName: CPointer<aws_byte_cursor>?, userData: COpaquePointer?): Boolean {
    checkNotNull(headerName) { "aws_should_sign_header_fn expected non-null header name" }
    if (userData == null) {
        return true
    }

    userData.asStableRef<ShouldSignHeaderFunction>().use {
        val kShouldSignHeaderFn = it.get()
        val kHeaderName = headerName.pointed.toKString()
        return kShouldSignHeaderFn(kHeaderName)
    }
}

/**
 * Callback for standard request signing. Applies the given signing result to the HTTP message and then returns the
 * signature via callback channel.
 */
private fun signCallback(signingResult: CPointer<aws_signing_result>?, errorCode: Int, userData: COpaquePointer?) {
    awsAssertOpSuccess(errorCode) { "signing failed with code $errorCode: ${CRT.errorString(errorCode)}" }
    checkNotNull(signingResult) { "signing callback received null aws_signing_result" }
    checkNotNull(userData) { "signing callback received null user data" }

    val (pinnedRequestToSign, callbackChannel) = userData
        .asStableRef<Pair<Pinned<CPointer<cnames.structs.aws_http_message>>, Channel<ByteArray>>>()
        .get()

    val requestToSign = pinnedRequestToSign.get()

    awsAssertOpSuccess(aws_apply_signing_result_to_http_request(requestToSign, Allocator.Default.allocator, signingResult)) {
        "aws_apply_signing_result_to_http_request"
    }

    runBlocking { callbackChannel.send(signingResult.getSignature()) }
}

/**
 * Callback for chunked signing. Returns the signature via callback channel.
 */
private fun signChunkCallback(signingResult: CPointer<aws_signing_result>?, errorCode: Int, userData: COpaquePointer?) {
    awsAssertOpSuccess(errorCode) { "signing failed with code $errorCode: ${CRT.errorString(errorCode)}" }
    checkNotNull(signingResult) { "signing callback received null aws_signing_result" }
    checkNotNull(userData) { "signing callback received null user data" }

    val callbackChannel = userData.asStableRef<Channel<ByteArray>>().get()
    runBlocking { callbackChannel.send(signingResult.getSignature()) }
}

private fun Credentials.toNativeCredentials(): CPointer<cnames.structs.aws_credentials>? = aws_credentials_new_from_string(
    Allocator.Default.allocator,
    access_key_id = accessKeyId.toAwsString(),
    secret_access_key = secretAccessKey.toAwsString(),
    session_token = sessionToken?.toAwsString(),
    expiration_timepoint_seconds = UINT64_MAX, // FIXME?: Our Credentials do not have an expiration field
)
