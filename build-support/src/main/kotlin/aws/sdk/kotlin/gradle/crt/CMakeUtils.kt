/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.crt

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Architecture
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File

fun KotlinNativeTarget.namedSuffix(prefix: String, uppercase: Boolean = false): String =
    prefix + if (uppercase) name.uppercase() else name

val KonanTarget.isSimulatorSdk: Boolean
    get() = when (this) {
        KonanTarget.IOS_SIMULATOR_ARM64, KonanTarget.IOS_X64,
        KonanTarget.TVOS_SIMULATOR_ARM64, KonanTarget.TVOS_X64,
        KonanTarget.WATCHOS_SIMULATOR_ARM64,
        KonanTarget.WATCHOS_X64,
        -> true
        else -> false
    }

// See https://cmake.org/cmake/help/latest/manual/cmake-toolchains.7.html#cross-compiling-for-ios-tvos-visionos-or-watchos
const val IOS_DEVICE_SDK = "iphoneos"
const val IOS_SIMULATOR_SDK = "iphonesimulator"
const val TVOS_DEVICE_SDK = "appletvos"
const val TVOS_SIMULATOR_SDK = "appletvsimulator"
const val WATCHOS_DEVICE_SDK = "watchos"
const val WATCHOS_SIMULATOR_SDK = "watchsimulator"

val KonanTarget.osxDeviceSdkName: String?
    get() = when (this) {
        KonanTarget.IOS_ARM64 -> IOS_DEVICE_SDK
        KonanTarget.IOS_SIMULATOR_ARM64, KonanTarget.IOS_X64 -> IOS_SIMULATOR_SDK
        KonanTarget.TVOS_ARM64 -> TVOS_DEVICE_SDK
        KonanTarget.TVOS_SIMULATOR_ARM64, KonanTarget.TVOS_X64 -> TVOS_SIMULATOR_SDK
        KonanTarget.WATCHOS_ARM32,
        KonanTarget.WATCHOS_ARM64,
        KonanTarget.WATCHOS_DEVICE_ARM64,
        -> WATCHOS_DEVICE_SDK
        KonanTarget.WATCHOS_SIMULATOR_ARM64,
        KonanTarget.WATCHOS_X64,
        -> WATCHOS_SIMULATOR_SDK
        else -> null
    }

val KonanTarget.osxSystemName: String?
    get() = when (family) {
        Family.IOS -> "iOS"
        Family.TVOS -> "tvOS"
        Family.WATCHOS -> "watchOS"
        else -> null
    }

val KonanTarget.osxArchitectureName
    get() = when (architecture) {
        Architecture.X64 -> "x86_64"
        Architecture.X86 -> "i386"
        Architecture.ARM64 -> "arm64"
        Architecture.ARM32 -> when (this) {
            KonanTarget.WATCHOS_ARM32 -> "armv7k"
            KonanTarget.WATCHOS_ARM64 -> "arm64_32"
            else -> null
        }
        else -> null
    }

fun Project.cmakeBuildDir(target: KotlinNativeTarget): File =
    project.rootProject.layout.buildDirectory.file(target.namedSuffix("cmake-build/")).get().asFile

fun Project.cmakeInstallDir(target: KotlinNativeTarget): File =
    project.rootProject.layout.buildDirectory.file(target.namedSuffix("crt-libs/")).get().asFile

val Project.cmakeLists: File
    get() = rootProject.projectDir.resolve("CMakeLists.txt")

val KotlinNativeTarget.cmakeConfigureTaskName: String
    get() = namedSuffix("cmakeConfigure", uppercase = true)

val KotlinNativeTarget.cmakeBuildTaskName: String
    get() = namedSuffix("cmakeBuild", uppercase = true)

val KotlinNativeTarget.cmakeInstallTaskName: String
    get() = namedSuffix("cmakeInstall", uppercase = true)

val File.slashPath: String
    get() = path.replace(File.separatorChar, '/')
