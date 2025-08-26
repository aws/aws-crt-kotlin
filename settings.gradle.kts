/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        maven {
            name = "kotlinRepoTools"
            url = java.net.URI("https://d2gys1nrxnjnyg.cloudfront.net/releases")
            content {
                includeGroupByRegex("""aws\.sdk\.kotlin.*""")
            }
        }
    }
}

includeBuild("./build-support")

rootProject.name = "aws-crt-kotlin-parent"

include(":aws-crt-kotlin")
