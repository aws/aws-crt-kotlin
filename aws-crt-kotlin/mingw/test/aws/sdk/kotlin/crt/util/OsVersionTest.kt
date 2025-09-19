package aws.sdk.kotlin.crt.util

import kotlin.test.Test

class OsVersionTest {
    @Test
    fun testOsInfo() = runTest {
        val version = osVersionFromKernel()
        assertNotNull(osInfo.version)
    }
}