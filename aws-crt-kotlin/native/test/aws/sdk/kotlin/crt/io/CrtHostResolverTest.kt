/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.io

import aws.sdk.kotlin.crt.use
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Copied from smithy-kotlin
class CrtHostResolverTest {
    @Test
    fun testResolveLocalhost() = runTest {
        HostResolver().use {
            val addresses = it.resolve("localhost")
            assertTrue(addresses.isNotEmpty())

            addresses.forEach { addr ->
                assertEquals("localhost", addr.host)

                val localHostAddress = when (addr.addressType) {
                    AddressType.IpV4 -> "127.0.0.1"
                    AddressType.IpV6 -> "::1"
                }
                assertEquals(localHostAddress, addr.address)
            }
        }
    }

    @Test
    fun testResolveIpv4Address() = runTest {
        HostResolver().use {
            val addresses = it.resolve("127.0.0.1")
            assertTrue(addresses.isNotEmpty())

            addresses.forEach { addr ->
                assertEquals(AddressType.IpV4, addr.addressType)
                assertEquals("127.0.0.1", addr.address)
            }
        }
    }

    @Test
    fun testResolveIpv6Address() = runTest {
        HostResolver().use {
            val addresses = it.resolve("::1")
            assertTrue(addresses.isNotEmpty())

            addresses.forEach { addr ->
                assertEquals(AddressType.IpV6, addr.addressType)
                assertEquals("::1", addr.address)
            }
        }
    }

    @Test
    fun testResolveExampleDomain() = runTest {
        HostResolver().use {
            val addresses = it.resolve("example.com")
            assertNotNull(addresses)
            assertTrue(addresses.isNotEmpty())

            addresses.forEach { addr ->
                assertEquals("example.com", addr.host)
                when (addr.addressType) {
                    AddressType.IpV4 -> assertEquals(3, addr.address.count { it == '.' })
                    AddressType.IpV6 -> assertEquals(if (addr.address.contains("::")) 6 else 7, addr.address.count { it == ':' })
                }
            }
        }
    }

    @Test
    fun testResolveInvalidDomain() = runTest {
        assertFails {
            val hr = HostResolver()
            try {
                hr.resolve("this-domain-definitely-does-not-exist-12345.local")
            } finally {
                hr.close()
            }
        }
    }
}
