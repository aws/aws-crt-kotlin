/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.http

import aws.sdk.kotlin.crt.util.Digest
import aws.sdk.kotlin.crt.util.encodeToHex
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.mockserver.client.MockServerClient
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import kotlin.test.*

private val TEST_DOC_LINE = "This is a sample to prove that http downloads and uploads work."
private val TEST_DOC_SHA256 = "c7fdb5314b9742467b16bd5ea2f8012190b5e2c44a005f7984f89aab58219534"

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HttpRequestResponseTest : HttpClientTest() {

    lateinit var mockServer: MockServerClient
    lateinit var url: String

    @BeforeAll
    fun setup() {
        mockServer = ClientAndServer.startClientAndServer(0)
        url = "http://localhost:${mockServer.port}"
    }

    @AfterAll
    fun tearDown() {
        mockServer.close()
    }

    // no body request
    private suspend fun testSimpleRequest(verb: String, path: String, expectedStatus: Int) {
        val expectedRequest = request().withMethod(verb).withPath(path)
        val unhandledRequest = request()

        // Set up the expected case
        mockServer.`when`(expectedRequest)
            .respond(response().withStatusCode(expectedStatus))

        // Unhandled requests
        mockServer.`when`(unhandledRequest).respond(response().withStatusCode(500))

        try {
            val response = roundTrip(url = url + path, verb = verb)
            assertEquals(
                expectedStatus,
                response.statusCode,
                "[$url]: expected http status ($expectedStatus) does not match",
            )
        } catch (ex: Exception) {
            fail("[$url]: failed to round trip request: $ex")
        } finally {
            // Clean up
            mockServer.clear(expectedRequest)
            mockServer.clear(unhandledRequest)
        }
    }

    @Test
    fun testHttpGet() = runBlocking {
        testSimpleRequest("GET", "/get", 200)
        testSimpleRequest("GET", "/post", 405)
        testSimpleRequest("GET", "/put", 405)
        testSimpleRequest("GET", "/delete", 405)
    }

    @Test
    fun testHttpPost() = runBlocking {
        testSimpleRequest("POST", "/get", 405)
        testSimpleRequest("POST", "/post", 200)
        testSimpleRequest("POST", "/put", 405)
        testSimpleRequest("POST", "/delete", 405)
    }

    @Test
    fun testHttpPut() = runBlocking {
        testSimpleRequest("PUT", "/get", 405)
        testSimpleRequest("PUT", "/post", 405)
        testSimpleRequest("PUT", "/put", 200)
        testSimpleRequest("PUT", "/delete", 405)
    }

    @Test
    fun testHttpDelete() = runBlocking {
        testSimpleRequest("DELETE", "/get", 405)
        testSimpleRequest("DELETE", "/post", 405)
        testSimpleRequest("DELETE", "/put", 405)
        testSimpleRequest("DELETE", "/delete", 200)
    }

    @Test
    fun testHttpDownload() = runBlocking {
        val response = roundTrip(url = "https://aws-crt-test-stuff.s3.amazonaws.com/http_test_doc.txt", verb = "GET")
        assertEquals(200, response.statusCode, "expected http status does not match")
        assertNotNull(response.body)

        assertEquals(TEST_DOC_SHA256, Digest.sha256(response.body).encodeToHex())
    }

    @Test
    fun testHttpUpload() = runBlocking {
        val bodyToSend = TEST_DOC_LINE

        // Set up mock server
        val expectedRequest = request().withMethod("PUT").withPath("/anything")
        mockServer.`when`(expectedRequest)
            .respond(response().withStatusCode(200).withBody(bodyToSend))

        val response = try {
            roundTrip(url = "$url/anything", verb = "PUT", body = bodyToSend)
        } finally {
            mockServer.clear(expectedRequest)
        }

        assertEquals(200, response.statusCode, "expected http status does not match")
        assertNotNull(response.body, "expected a response body for http upload")

        val bodyText = response.body.decodeToString()
        assertEquals(bodyToSend, bodyText)
    }
}
