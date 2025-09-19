/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import aws.sdk.kotlin.gradle.kmp.NATIVE_ENABLED
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
import java.nio.file.Files
import java.nio.file.Paths

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

    if (NATIVE_ENABLED && HostManager.hostIsMingw) {
        mingwX64 {
            val mingwHome = findMingwHome()
            val defPath = layout.buildDirectory.file("cinterop/winver.def")

            // Dynamically construct def file because of dynamic mingw paths
            val defFileTask by tasks.registering {
                outputs.file(defPath)

                val mingwLibs = Paths.get(mingwHome, "lib").toString().replace("\\", "\\\\") // Windows path shenanigans

                doLast {
                    Files.writeString(
                        defPath.get().asFile.toPath(),
                        """
                            package = aws.smithy.kotlin.native.winver
                            headers = windows.h
                            compilerOpts = \
                                -DUNICODE \
                                -DWINVER=0x0601 \
                                -D_WIN32_WINNT=0x0601 \
                                -DWINAPI_FAMILY=3 \
                                -DOEMRESOURCE \
                                -Wno-incompatible-pointer-types \
                                -Wno-deprecated-declarations
                            libraryPaths = $mingwLibs
                            staticLibraries = libversion.a
                        """.trimIndent(),
                    )
                }
            }
            compilations["main"].cinterops {
                create("winver") {
                    val mingwIncludes = Paths.get(mingwHome, "include").toString()
                    includeDirs(mingwIncludes)
                    definitionFile.set(defPath)

                    // Ensure that the def file is written first
                    tasks[interopProcessingTaskName].dependsOn(defFileTask)
                }
            }

            // TODO clean up
            val compilerArgs = listOf(
                "-Xverbose-phases=linker", // Enable verbose linking phase from the compiler
                "-linker-option",
                "-v",
            )
            compilerOptions.freeCompilerArgs.addAll(compilerArgs)
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
    if (this is Test) useJUnitPlatform()

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

private fun findMingwHome(): String =
    System.getenv("MINGW_PREFIX")?.takeUnless { it.isBlank() }
        ?: typedProp("mingw.prefix")
        ?: throw IllegalStateException(
            "Cannot determine MinGW prefix location. Please verify MinGW is installed correctly " +
                    "and that either the `MINGW_PREFIX` environment variable or the `mingw.prefix` Gradle property is set.",
        )

