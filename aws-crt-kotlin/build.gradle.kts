/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import aws.sdk.kotlin.gradle.crt.CMakeBuildType
import aws.sdk.kotlin.gradle.crt.cmakeInstallDir
import aws.sdk.kotlin.gradle.crt.configureCrtCMakeBuild
import aws.sdk.kotlin.gradle.dsl.configurePublishing
import aws.sdk.kotlin.gradle.kmp.configureIosSimulatorTasks
import aws.sdk.kotlin.gradle.kmp.configureKmpTargets
import aws.sdk.kotlin.gradle.util.typedProp
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.aws.kotlin.repo.tools.kmp)
    id("crt-build-support")
}

val sdkVersion: String by project
group = "aws.sdk.kotlin.crt"
version = sdkVersion
description = "Kotlin Multiplatform bindings for AWS Common Runtime"

// See: https://kotlinlang.org/docs/reference/opt-in-requirements.html#opting-in-to-using-api
val optinAnnotations = listOf("kotlin.RequiresOptIn", "kotlinx.cinterop.ExperimentalForeignApi")

// KMP configuration from build plugin
configureKmpTargets()

kotlin {
    explicitApi()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(libs.crt.java)

                // FIXME - temporary integration with CompletableFuture while we work out a POC on the jvm target
                implementation(libs.kotlinx.coroutines.jdk8)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.debug)
                implementation(libs.mockserver.netty)
            }
        }
    }

    sourceSets.all {
        optinAnnotations.forEach { languageSettings.optIn(it) }
    }

    // create a single "umbrella" cinterop will all the aws-c-* API's we want to consume
    // see: https://github.com/JetBrains/kotlin-native/issues/2423#issuecomment-466300153
    targets.withType<KotlinNativeTarget> {
        val knTarget = this

        val targetInstallDir = project.cmakeInstallDir(knTarget)
        val headerDir = targetInstallDir.resolve("include")
        val libDir = targetInstallDir.resolve("lib")

        compilations["main"].cinterops {
            val interopDir = "$projectDir/native/interop"
            logger.info("configuring crt cinterop for: ${knTarget.name}")
            val interopSettings = create("aws-crt") {
                defFile("$interopDir/crt.def")
                includeDirs(headerDir)
                compilerOpts("-L${libDir.absolutePath}")
                extraOpts("-libraryPath", libDir.absolutePath)
            }
            val interopTaskName = interopSettings.interopProcessingTaskName

            if (!knTarget.isBuildableOnHost) {
                logger.warn("Kotlin/Native target $knTarget is enabled but not buildable on host ${HostManager.host}, disabling cinterop")
                tasks.named(interopTaskName).configure {
                    onlyIf { false }
                }
            } else {
                logger.info("Configuring Kotlin/Native target $knTarget: ${knTarget.name}")
                val cmakeInstallTask = configureCrtCMakeBuild(knTarget, CMakeBuildType.Release)
                // cinterop tasks processes header files which requires the corresponding CMake build/install to run
                tasks.named(interopTaskName).configure {
                    dependsOn(cmakeInstallTask)
                }
            }
        }
    }
}

configureIosSimulatorTasks()

// Publishing
configurePublishing("aws-crt-kotlin")

val linuxTargets: List<String> = listOf(
    "linuxX64",
    "linuxArm64",
)

// create a summary task that compiles all cross platform test binaries
tasks.register("linuxTestBinaries") {
    linuxTargets.map {
        tasks.named("${it}TestBinaries")
    }.forEach { testTask ->
        dependsOn(testTask)
    }
}

// run tests on specific JVM version
val testJavaVersion = typedProp<String>("test.java.version")?.let {
    JavaLanguageVersion.of(it)
}?.also {
    println("configuring tests to run with jdk $it")
}

if (testJavaVersion != null) {
    tasks.withType<Test> {
        val toolchains = project.extensions.getByType<JavaToolchainService>()
        javaLauncher.set(
            toolchains.launcherFor {
                languageVersion.set(testJavaVersion)
            },
        )
    }
}

tasks.withType<AbstractTestTask> {
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        showStackTraces = true
        showExceptions = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Returns whether this target can be built on the current host
private val KotlinNativeTarget.isBuildableOnHost: Boolean
    get() = run {
        val family = konanTarget.family
        return if (HostManager.hostIsMac) {
            family in setOf(Family.OSX, Family.IOS, Family.TVOS, Family.WATCHOS)
        } else if (HostManager.hostIsLinux) {
            family == Family.LINUX
        } else if (HostManager.hostIsMingw) {
            family == Family.MINGW
        } else {
            throw Exception("Unsupported host: ${HostManager.host}")
        }
    }
