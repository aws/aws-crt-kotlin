/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt

public open class CrtRuntimeException(
    message: String? = null,
    cause: Throwable? = null,
    ec: Int? = null,
) : RuntimeException(message, cause) {

    public val errorCode: Int = ec ?: CRT.lastError()

    override val message: String?
        get() = buildString {
            if (super.message != null) {
                append(super.message)
                append(" ")
            }
            append("ErrorCode: $errorCode")
            errorName?.let {
                append("; ErrorName: $it")
            }
            errorDescription?.let {
                append("; ErrorDescription: $it")
            }
        }

    public val errorName: String?
        get() = CRT.errorName(errorCode)

    public val errorDescription: String?
        get() = CRT.errorString(errorCode)
}
