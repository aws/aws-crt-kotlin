/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import aws.sdk.kotlin.gradle.dsl.configureLinting
import aws.sdk.kotlin.gradle.publishing.SonatypeCentralPortalPublishTask
import aws.sdk.kotlin.gradle.publishing.SonatypeCentralPortalWaitForPublicationTask
import aws.sdk.kotlin.gradle.util.typedProp
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

buildscript {
    repositories {
        mavenCentral()
        mavenLocal()
    }
    // NOTE: buildscript classpath for the root project is the parent classloader for all subprojects.
    // Anything included in the root buildscript classpath is added to the classpath for all projects!
    dependencies {
        // Add our custom gradle build logic to buildscript classpath
        classpath(libs.aws.kotlin.repo.tools.build.support)
    }
}

plugins {
    alias(libs.plugins.kotlinx.binary.compatibility.validator)
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.aws.kotlin.repo.tools.kmp)
}

allprojects {
    // Enables running `./gradlew allDeps` to get a comprehensive list of dependencies for every subproject
    tasks.register<DependencyReportTask>("allDeps") { }
}

subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
            freeCompilerArgs.add("-Xjdk-release=1.8")
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
    }
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile> {
        compilerOptions {
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
    }
}

if (project.typedProp<Boolean>("kotlinWarningsAsErrors") == true) {
    allprojects {
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            compilerOptions.allWarningsAsErrors = true
        }
    }
}

// Publishing
tasks.register<SonatypeCentralPortalPublishTask>("publishToCentralPortal") { }
tasks.register<SonatypeCentralPortalWaitForPublicationTask>("waitForCentralPortalPublication") { }

// Code Style
val lintPaths = listOf(
    "**/*.{kt,kts}",
    "!crt/**",
)

configureLinting(lintPaths)
