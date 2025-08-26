/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.crt.io

import aws.sdk.kotlin.crt.Allocator
import aws.sdk.kotlin.crt.awsAssertOpSuccess
import aws.sdk.kotlin.crt.util.asAwsByteCursor
import aws.sdk.kotlin.crt.util.toKString
import kotlinx.cinterop.*
import libcrt.aws_uri
import libcrt.aws_uri_clean_up
import libcrt.aws_uri_init_parse

@OptIn(ExperimentalForeignApi::class)
internal actual fun parseUri(uri: String): Uri = memScoped {
    uri.encodeToByteArray().usePinned { pinned ->
        val uriCursor = pinned.asAwsByteCursor()
        val awsUri = alloc<aws_uri>()

        awsAssertOpSuccess(aws_uri_init_parse(awsUri.ptr, Allocator.Default, uriCursor)) {
            "aws_uri_init_parse()"
        }

        Uri.build {
            scheme = Protocol.createOrDefault(awsUri.scheme.toKString())
            host = awsUri.host_name.toKString()
            port = awsUri.port.toInt().takeIf { it > 0 }
            path = awsUri.path.toKString()
            parameters = awsUri.query_string.takeIf { it.len.toInt() > 0 }?.toKString()
            userInfo = awsUri.takeIf { it.user.len.toInt() > 0 }?.let {
                UserInfo(it.user.toKString(), it.password.toKString())
            }
        }.also {
            aws_uri_clean_up(awsUri.ptr)
        }
    }
}
