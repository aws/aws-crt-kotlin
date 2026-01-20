/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.io

/**
 * This class wraps the aws_tls_connection_options from aws-c-io to provide
 * access to TLS configuration contexts in the AWS Common Runtime.
 */
public class TlsContextOptions internal constructor(builder: TlsContextOptionsBuilder) {
    public val verifyPeer: Boolean = builder.verifyPeer
    public val minTlsVersion: TlsVersion = builder.minTlsVersion
    public val tlsCipherPreference: TlsCipherPreference = builder.tlsCipherPreference
    public val alpn: String = builder.alpn

    public val certificate: String? = builder.certificate
    public val privateKey: String? = builder.privateKey
    public val certificatePath: String? = builder.certificatePath
    public val privateKeyPath: String? = builder.privateKeyPath
    public val caRoot: String? = builder.caRoot
    public val caFile: String? = builder.caFile
    public val caDir: String? = builder.caDir
    public val pkcs12Path: String? = builder.pkcs12Path
    public val pkcs12Password: String? = builder.pkcs12Password

    public companion object {

        public fun build(block: TlsContextOptionsBuilder.() -> Unit): TlsContextOptions = TlsContextOptionsBuilder().apply(block).build()

        /**
         * Helper which creates a default set of TLS options for the current platform
         * @return A default configured set of options for a TLS client connection
         */
        public fun defaultClient(): TlsContextOptions = build {
            verifyPeer = true
        }

        /**
         * Helper which creates a default set of TLS options for the current platform
         *
         * @return A default configured set of options for a TLS server connection
         */
        public fun defaultServer(): TlsContextOptions = build {
            verifyPeer = false
        }

        /**
         * Returns whether or not the current platform can be configured to a specific TlsCipherPreference.
         * @param cipher The TlsCipherPreference to check
         * @return True if the current platform does support this TlsCipherPreference, false otherwise
         */
        public fun isCipherPreferenceSupported(cipher: TlsCipherPreference): Boolean = isCipherSupported(cipher)

        /**
         * Returns whether or not ALPN is supported on the current platform
         * @return true if ALPN is supported, false otherwise
         */
        public val isAlpnSupported: Boolean
            get() = isAlpnSupported()
    }
}

internal expect fun isCipherSupported(cipher: TlsCipherPreference): Boolean
internal expect fun isAlpnSupported(): Boolean

public class TlsContextOptionsBuilder {

    public fun build(): TlsContextOptions = TlsContextOptions(this)

    /**
     * Sets the minimum acceptable TLS version that the [TlsContext] will
     * allow. Not compatible with setCipherPreference() API.
     *
     * Select from TlsVersions, a good default is TlsVersions.TLS_VER_SYS_DEFAULTS
     * as this will update if the OS TLS is updated
     */
    public var minTlsVersion: TlsVersion = TlsVersion.SYS_DEFAULT

    /**
     * Sets the TLS Cipher Preferences that can be negotiated and used during the
     * TLS Connection. Not compatible with setMinimumTlsVersion() API.
     *
     */
    public var tlsCipherPreference: TlsCipherPreference = TlsCipherPreference.SYSTEM_DEFAULT
        set(value) {
            if (!TlsContextOptions.isCipherPreferenceSupported(value)) {
                throw IllegalArgumentException("TlsCipherPreference is not supported on this platform: $value")
            }

            if (minTlsVersion != TlsVersion.SYS_DEFAULT && value != TlsCipherPreference.SYSTEM_DEFAULT) {
                throw IllegalArgumentException("Currently only setting of either minimumTlsVersion or cipherPreference is supported, not both.")
            }

            field = value
        }

    /**
     * Semi-colon delimited list of supported ALPN protocols
     *
     * Sets the ALPN protocol list that will be provided when a TLS connection
     * starts e.g. "x-amzn-mqtt-ca"
     */
    public var alpn: String = ""

    /**
     * Set whether or not the peer should be verified. Default is true for clients,
     * and false for servers. If you are in a development or debugging environment,
     * you can disable this to avoid or diagnose trust store issues. This should
     * always be true on clients in the wild. If you set this to true on a server,
     * it will validate every client connection.
     */
    public var verifyPeer: Boolean = true

    public var certificate: String? = null
    public var privateKey: String? = null
    public var certificatePath: String? = null
    public var privateKeyPath: String? = null
    public var caRoot: String? = null
    public var caFile: String? = null
    public var caDir: String? = null
    public var pkcs12Path: String? = null
    public var pkcs12Password: String? = null
}
