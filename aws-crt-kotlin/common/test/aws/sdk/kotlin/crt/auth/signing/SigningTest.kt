/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.auth.signing

import aws.sdk.kotlin.crt.*
import aws.sdk.kotlin.crt.auth.credentials.Credentials
import aws.sdk.kotlin.crt.http.*
import aws.sdk.kotlin.crt.io.Uri
import kotlinx.coroutines.test.runTest
import kotlin.test.*

private val TEST_ACCESS_KEY_ID: String = "AKIDEXAMPLE"
private val TEST_SECRET_ACCESS_KEY: String = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY"

// 2015-08-30T12:36:00Z
private val TEST_DATE_EPOCH_MILLI: Long = 1440938160000

// ported over from crt-java
class SigningTest : CrtTest() {
    private fun createSimpleRequest(url: String, method: String, path: String, body: String? = null): HttpRequest = HttpRequest.build {
        this.method = method
        this.encodedPath = path
        val uri = Uri.parse(url)

        val bodyBytes = body?.encodeToByteArray()
        val bodyLen = bodyBytes?.size ?: 0

        headers {
            append("Host", uri.hostAndPort)
            append("Content-Length", bodyLen.toString())
        }

        this.body = bodyBytes?.let { HttpRequestBodyStream.fromByteArray(it) }
    }

    /**
     * post-vanilla-query test case
     */
    private fun createSigV4TestSuiteRequest(): HttpRequest = HttpRequest.build {
        method = "POST"
        headers.append("Host", "example.amazonaws.com")
        encodedPath = "/?Param1=value1"
    }

    private fun createUnsignableRequest(method: String, path: String): HttpRequest = HttpRequest.build {
        this.method = method
        this.encodedPath = path
        headers.append("Authorization", "example.amazonaws.com")
    }

    @Test
    fun testSigningSuccess() = runTest {
        val request = createSimpleRequest("https://www.example.com", "POST", "/derp", "<body>Hello</body>")
        val signingConfig = AwsSigningConfig.build {
            algorithm = AwsSigningAlgorithm.SIGV4
            signatureType = AwsSignatureType.HTTP_REQUEST_VIA_HEADERS
            region = "us-east-1"
            service = "service"
            date = Platform.epochMilliNow()
            credentials = Credentials(TEST_ACCESS_KEY_ID, TEST_SECRET_ACCESS_KEY, null)
            shouldSignHeader = { it != "bad-param" }
            useDoubleUriEncode = true
            normalizeUriPath = true
        }

        val signedRequest = AwsSigner.signRequest(request, signingConfig)
        assertTrue(signedRequest.headers.contains("X-Amz-Date"))
        assertTrue(signedRequest.headers.contains("Authorization"))
    }

    @Test
    fun testQuerySigningSuccess() = runTest {
        val request = createSigV4TestSuiteRequest()
        val signingConfig = AwsSigningConfig.build {
            algorithm = AwsSigningAlgorithm.SIGV4
            signatureType = AwsSignatureType.HTTP_REQUEST_VIA_QUERY_PARAMS
            region = "us-east-1"
            service = "service"
            date = TEST_DATE_EPOCH_MILLI
            credentials = Credentials(TEST_ACCESS_KEY_ID, TEST_SECRET_ACCESS_KEY, null)
            useDoubleUriEncode = true
            normalizeUriPath = true
            signedBodyValue = AwsSignedBodyValue.EMPTY_SHA256
            expirationInSeconds = 60
        }

        val signedRequest = AwsSigner.signRequest(request, signingConfig)

        val path = signedRequest.encodedPath
        assertTrue(path.contains("X-Amz-Signature="), "`$path` did not contain expected signature")
        assertTrue(path.contains("X-Amz-SignedHeaders=host"), "`$path` did not contain expected host")
        assertTrue(path.contains("X-Amz-Credential=AKIDEXAMPLE%2F20150830%2F"), "`$path` did not contain expected credentials")
        assertTrue(path.contains("X-Amz-Algorithm=AWS4-HMAC-SHA256"), "`$path` did not contain expected algorithm")
        assertTrue(path.contains("X-Amz-Expires=60"), "`$path` did not contain expected expiration")
    }

    @Test
    fun testSigningBasicSigV4() = runTest {
        val request = createSigV4TestSuiteRequest()
        val signingConfig = AwsSigningConfig.build {
            algorithm = AwsSigningAlgorithm.SIGV4
            signatureType = AwsSignatureType.HTTP_REQUEST_VIA_HEADERS
            region = "us-east-1"
            service = "service"
            date = TEST_DATE_EPOCH_MILLI
            credentials = Credentials(TEST_ACCESS_KEY_ID, TEST_SECRET_ACCESS_KEY, null)
            useDoubleUriEncode = true
            normalizeUriPath = true
            signedBodyValue = AwsSignedBodyValue.EMPTY_SHA256
            expirationInSeconds = 60
        }

        val signedRequest = AwsSigner.signRequest(request, signingConfig)
        assertTrue(signedRequest.headers.contains("X-Amz-Date", "20150830T123600Z"), "${signedRequest.headers}")
        assertTrue(
            signedRequest.headers.contains(
                "Authorization",
                "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20150830/us-east-1/service/aws4_request, SignedHeaders=host;x-amz-date, Signature=28038455d6de14eafc1f9222cf5aa6f1a96197d7deb8263271d420d138af7f11",
            ),
            "sigv4 authorization not equal: " + signedRequest.headers["Authorization"],
        )
    }

    @Test
    fun testSigningFailureBadRequest() = runTest {
        val request = createUnsignableRequest("POST", "/bad")
        val signingConfig = AwsSigningConfig.build {
            algorithm = AwsSigningAlgorithm.SIGV4
            signatureType = AwsSignatureType.HTTP_REQUEST_VIA_HEADERS
            region = "us-east-1"
            service = "service"
            date = Platform.epochMilliNow()
            credentials = Credentials(TEST_ACCESS_KEY_ID, TEST_SECRET_ACCESS_KEY, null)
            useDoubleUriEncode = true
            normalizeUriPath = true
            signedBodyValue = AwsSignedBodyValue.EMPTY_SHA256
        }

        val ex = assertFailsWith<CrtRuntimeException> {
            AwsSigner.signRequest(request, signingConfig)
        }
        assertEquals("AWS_AUTH_SIGNING_ILLEGAL_REQUEST_HEADER", ex.errorName)
    }

    @Test
    fun testSigningSigV4Asymmetric() = runTest {
        val request = createSigV4TestSuiteRequest()
        val signingConfig = AwsSigningConfig.build {
            algorithm = AwsSigningAlgorithm.SIGV4_ASYMMETRIC
            signatureType = AwsSignatureType.HTTP_REQUEST_VIA_HEADERS
            region = "us-east-1"
            service = "service"
            date = TEST_DATE_EPOCH_MILLI
            credentials = Credentials(TEST_ACCESS_KEY_ID, TEST_SECRET_ACCESS_KEY, null)
            useDoubleUriEncode = true
            normalizeUriPath = true
            signedBodyValue = AwsSignedBodyValue.EMPTY_SHA256
            expirationInSeconds = 60
        }

        val signedRequest = AwsSigner.signRequest(request, signingConfig)
        assertTrue(signedRequest.headers.contains("X-Amz-Date", "20150830T123600Z"), "${signedRequest.headers}")
        val prefix = "AWS4-ECDSA-P256-SHA256 Credential=AKIDEXAMPLE/20150830/service/aws4_request, SignedHeaders=host;x-amz-date;x-amz-region-set, Signature="
        assertTrue(signedRequest.headers["Authorization"]!!.contains(prefix), signedRequest.headers["Authorization"])
    }

    @Test
    fun testSigningChunkTrailingHeaders() = runTest {
        val signingConfig = AwsSigningConfig.build {
            algorithm = AwsSigningAlgorithm.SIGV4
            signatureType = AwsSignatureType.HTTP_REQUEST_TRAILING_HEADERS
            region = "foo"
            service = "bar"
            date = 1651022625000
            credentials = Credentials(TEST_ACCESS_KEY_ID, TEST_SECRET_ACCESS_KEY, null)
        }

        val trailingHeaders = Headers.build {
            append("x-amz-checksum-crc32", "AAAAAA==")
            append("x-amz-arbitrary-header-with-value", "test")
        }

        val previousSignature = "106d0654706e3e8dde144d69ca9882ea38d4d72576056c724ba763f8ed3074f3".encodeToByteArray()

        val signature = AwsSigner.signChunkTrailer(trailingHeaders, previousSignature, signingConfig).signature.decodeToString()
        val expectedSignature = "8b578658fa1705d62bf26aa73e764ac4b705e6d9efd223a2d9e156580f085de4" // validated using DefaultAwsSigner
        assertEquals(expectedSignature, signature)
    }

    @Test
    fun testShouldSignHeader() = runTest {
        val request = HttpRequestBuilder().apply {
            method = "POST"
            encodedPath = "https://www.example.com"
            headers {
                append("bad-header", "should not be signed")
                append("Host", "https://www.example.com")
            }
        }.build()

        val baseSigningConfig = AwsSigningConfig.Builder().apply {
            algorithm = AwsSigningAlgorithm.SIGV4
            signatureType = AwsSignatureType.HTTP_REQUEST_VIA_HEADERS
            region = "us-east-1"
            service = "service"
            date = Platform.epochMilliNow()
            credentials = Credentials(TEST_ACCESS_KEY_ID, TEST_SECRET_ACCESS_KEY, null)
            useDoubleUriEncode = true
            normalizeUriPath = true
        }

        val skipHeaderConfig = baseSigningConfig.apply {
            shouldSignHeader = { it != "bad-header" }
        }.build()
        val implicitSignAllHeadersConfig = baseSigningConfig.apply {
            shouldSignHeader = null
        }.build()
        val explicitSignAllHeadersConfig = baseSigningConfig.apply {
            shouldSignHeader = { true }
        }.build()

        val skipHeaderSignedRequest = AwsSigner.signRequest(request, skipHeaderConfig)
        assertTrue(skipHeaderSignedRequest.headers.contains("Authorization"))
        assertFalse(skipHeaderSignedRequest.headers["Authorization"]!!.contains("bad-header"))

        val implicitSignAllHeadersRequest = AwsSigner.signRequest(request, implicitSignAllHeadersConfig)
        assertTrue(implicitSignAllHeadersRequest.headers.contains("Authorization"))
        assertTrue(implicitSignAllHeadersRequest.headers["Authorization"]!!.contains("bad-header"))

        val explicitSignAllHeadersRequest = AwsSigner.signRequest(request, explicitSignAllHeadersConfig)
        assertTrue(explicitSignAllHeadersRequest.headers.contains("Authorization"))
        assertTrue(explicitSignAllHeadersRequest.headers["Authorization"]!!.contains("bad-header"))
    }
}
