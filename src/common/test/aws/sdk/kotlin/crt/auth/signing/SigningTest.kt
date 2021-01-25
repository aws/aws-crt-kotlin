/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.sdk.kotlin.crt.auth.signing

import aws.sdk.kotlin.crt.*
import aws.sdk.kotlin.crt.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.crt.auth.credentials.build
import aws.sdk.kotlin.crt.http.HttpRequest
import aws.sdk.kotlin.crt.http.HttpRequestBodyStream
import aws.sdk.kotlin.crt.http.headers
import aws.sdk.kotlin.crt.io.Uri
import kotlin.test.*

// ported over from crt-java
class SigningTest : CrtTest() {
    private val TEST_ACCESS_KEY_ID: String = "AKIDEXAMPLE"
    private val TEST_SECRET_ACCESS_KEY: String = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY"

    // 2015-08-30T12:36:00Z
    private val TEST_DATE_EPOCH_MILLI: Long = 1440938160000

    private fun createSimpleRequest(url: String, method: String, path: String, body: String? = null): HttpRequest = HttpRequest.build {
        this.method = method
        this.encodedPath = path
        val uri = Uri.parse(url)

        val bodyBytes = body?.encodeToByteArray()
        val bodyLen = bodyBytes?.size ?: 0

        headers {
            append("Host", uri.host)
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
    fun testSigningSuccess() = runSuspendTest {
        StaticCredentialsProvider.build {
            accessKeyId = TEST_ACCESS_KEY_ID
            secretAccessKey = TEST_SECRET_ACCESS_KEY
        }.use { provider ->
            val request = createSimpleRequest("https://www.example.com", "POST", "/derp", "<body>Hello</body>")
            val signingConfig = AwsSigningConfig.build {
                algorithm = AwsSigningAlgorithm.SIGV4
                signatureType = AwsSignatureType.HTTP_REQUEST_VIA_HEADERS
                region = "us-east-1"
                service = "service"
                date = Platform.epochMilliNow()
                credentialsProvider = provider
                shouldSignHeader = { it != "bad-param" }
                useDoubleUriEncode = true
                normalizeUriPath = true
            }

            val signedRequest = AwsSigner.signRequest(request, signingConfig)
            assertTrue(signedRequest.headers.contains("X-Amz-Date"))
            assertTrue(signedRequest.headers.contains("Authorization"))
        }
    }

    @Test
    fun testQuerySigningSuccess() = runSuspendTest {
        StaticCredentialsProvider.build {
            accessKeyId = TEST_ACCESS_KEY_ID
            secretAccessKey = TEST_SECRET_ACCESS_KEY
        }.use { provider ->
            val request = createSigV4TestSuiteRequest()
            val signingConfig = AwsSigningConfig.build {
                algorithm = AwsSigningAlgorithm.SIGV4
                signatureType = AwsSignatureType.HTTP_REQUEST_VIA_QUERY_PARAMS
                region = "us-east-1"
                service = "service"
                date = TEST_DATE_EPOCH_MILLI
                credentialsProvider = provider
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
    }

    @Test
    fun testSigningBasicSigV4() = runSuspendTest {
        StaticCredentialsProvider.build {
            accessKeyId = TEST_ACCESS_KEY_ID
            secretAccessKey = TEST_SECRET_ACCESS_KEY
        }.use { provider ->
            val request = createSigV4TestSuiteRequest()
            val signingConfig = AwsSigningConfig.build {
                algorithm = AwsSigningAlgorithm.SIGV4
                signatureType = AwsSignatureType.HTTP_REQUEST_VIA_HEADERS
                region = "us-east-1"
                service = "service"
                date = TEST_DATE_EPOCH_MILLI
                credentialsProvider = provider
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
                    "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20150830/us-east-1/service/aws4_request, SignedHeaders=host;x-amz-date, Signature=28038455d6de14eafc1f9222cf5aa6f1a96197d7deb8263271d420d138af7f11"
                )
            )
        }
    }

    @Test
    fun testSigningFailureBadRequest() = runSuspendTest {
        StaticCredentialsProvider.build {
            accessKeyId = TEST_ACCESS_KEY_ID
            secretAccessKey = TEST_SECRET_ACCESS_KEY
        }.use { provider ->
            val request = createUnsignableRequest("POST", "/bad")
            val signingConfig = AwsSigningConfig.build {
                algorithm = AwsSigningAlgorithm.SIGV4
                signatureType = AwsSignatureType.HTTP_REQUEST_VIA_HEADERS
                region = "us-east-1"
                service = "service"
                date = Platform.epochMilliNow()
                credentialsProvider = provider
                useDoubleUriEncode = true
                normalizeUriPath = true
                signedBodyValue = AwsSignedBodyValue.EMPTY_SHA256
            }

            val ex = assertFailsWith<CrtRuntimeException> {
                AwsSigner.signRequest(request, signingConfig)
            }
            assertEquals("AWS_AUTH_SIGNING_ILLEGAL_REQUEST_HEADER", ex.errorName)
        }
    }
}
