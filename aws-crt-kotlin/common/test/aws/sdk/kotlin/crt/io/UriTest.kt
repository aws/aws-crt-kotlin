/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.io

import aws.sdk.kotlin.crt.CrtTest
import kotlin.test.*

class UriTest : CrtTest() {
    @Test
    fun basicToString() {
        val expected = "https://test.aws.com/kotlin"
        val uri = Uri(
            Protocol.HTTPS,
            "test.aws.com",
            path = "/kotlin",
        )
        assertEquals(expected, uri.toString())
    }

    @Test
    fun forceRetainQuery() {
        val expected = "https://test.aws.com/kotlin?"
        val uri = UriBuilder.build {
            host = "test.aws.com"
            path = "/kotlin"
            forceQuery = true
        }
        assertEquals(expected, uri.toString())
    }

    @Test
    fun withParameters() {
        val expected = "https://test.aws.com/kotlin?foo=bar&baz=quux&baz=qux"

        val uri = Uri(
            Protocol.HTTPS,
            "test.aws.com",
            path = "/kotlin",
            parameters = "foo=bar&baz=quux&baz=qux",
        )
        assertEquals(expected, uri.toString())
    }

    @Test
    fun specificPort() {
        val expected = "https://test.aws.com:8000"
        val uri = Uri(
            Protocol.HTTPS,
            "test.aws.com",
            specifiedPort = 8000,
        )
        assertEquals(expected, uri.toString())

        val expected2 = "http://test.aws.com"
        val uri2 = Uri(
            Protocol.HTTP,
            "test.aws.com",
            specifiedPort = 80,
        )
        assertEquals(expected2, uri2.toString())
    }

    @Test
    fun portRange() {
        fun checkPort(n: Int) {
            assertEquals(
                n,
                Uri(
                    Protocol.HTTPS,
                    "test.aws.com",
                    specifiedPort = n,
                ).specifiedPort,
            )
        }

        checkPort(1)
        checkPort(65536)
        assertFails {
            checkPort(65537)
        }
    }

    @Test
    fun userinfoNoPassword() {
        val expected = "https://user@test.aws.com"
        val uri = UriBuilder.build {
            scheme = Protocol.HTTPS
            host = "test.aws.com"
            userInfo = UserInfo("user", "")
        }
        assertEquals(expected, uri.toString())
    }

    @Test
    fun fullUserinfo() {
        val expected = "https://user:password@test.aws.com"
        val uri = UriBuilder.build {
            scheme = Protocol.HTTPS
            host = "test.aws.com"
            userInfo = UserInfo("user", "password")
        }
        assertEquals(expected, uri.toString())
    }

    @Test
    fun authority() {
        val tests = listOf(
            "user:password@test.aws.com:1234" to UriBuilder.build {
                userInfo = UserInfo("user", "password")
                host = "test.aws.com"
                port = 1234
            },
            "test.aws.com:1234" to UriBuilder.build {
                host = "test.aws.com"
                port = 1234
            },
            "user:password@test.aws.com" to UriBuilder.build {
                userInfo = UserInfo("user", "password")
                host = "test.aws.com"
            },
            "test.aws.com" to UriBuilder.build {
                host = "test.aws.com"
            },
        )
        tests.forEach { (expected, uri) -> assertEquals(expected, uri.authority) }
    }

    @Test
    fun hostAndPort() {
        val tests = listOf(
            "test.aws.com:1234" to UriBuilder.build {
                host = "test.aws.com"
                port = 1234
            },
            "test.aws.com" to UriBuilder.build {
                host = "test.aws.com"
            },
        )
        tests.forEach { (expected, uri) -> assertEquals(expected, uri.hostAndPort) }
    }

    @Test
    fun itBuilds() {
        val builder = UriBuilder()
        builder.scheme = Protocol.HTTP
        builder.host = "test.aws.com"
        builder.path = "/kotlin"
        val uri = builder.build()
        val expected = "http://test.aws.com/kotlin"
        assertEquals(expected, uri.toString())
        assertEquals(Protocol.HTTP, builder.scheme)
        assertEquals("test.aws.com", builder.host)
        assertEquals(null, builder.port)
        assertEquals(null, builder.fragment)
        assertEquals(null, builder.userInfo)
    }

    @Test
    fun itBuildsWithNonDefaultPort() {
        val uri = UriBuilder.build {
            scheme = Protocol.HTTP
            host = "test.aws.com"
            path = "/kotlin"
            port = 3000
        }
        val expected = "http://test.aws.com:3000/kotlin"
        assertEquals(expected, uri.toString())
    }

    @Test
    fun itBuildsWithParameters() {
        val uri = UriBuilder.build {
            scheme = Protocol.HTTP
            host = "test.aws.com"
            path = "/kotlin"
            parameters = "foo=baz"
        }
        val expected = "http://test.aws.com/kotlin?foo=baz"
        assertEquals(expected, uri.toString())
    }

    @Test
    fun itParses() {
        val uri = "https://test.aws.com:8000/kotlin?foo=baz"
        val actual = Uri.parse(uri)
        assertEquals(Protocol.HTTPS, actual.scheme)
        assertEquals(8000, actual.port)
        assertEquals("test.aws.com", actual.host)
        assertEquals("/kotlin", actual.path)
        assertEquals("foo=baz", actual.parameters)
        assertNull(actual.userInfo)

        // val actual2 = Uri.parse("https://aws.amazon.com")
        // assertEquals("", actual2.path)
    }
}
