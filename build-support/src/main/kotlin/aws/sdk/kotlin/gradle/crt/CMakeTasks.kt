/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.gradle.crt

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File

/**
 * See [CMAKE_BUILD_TYPE](https://cmake.org/cmake/help/latest/variable/CMAKE_BUILD_TYPE.html)
 */
enum class CMakeBuildType {
    Debug,
    RelWithDebInfo,
    Release,
}

/**
 * Configure CMake tasks for building and installing CRT locally for a given Kotlin/Native target.
 *
 * This function sets up the following tasks:
 * * cmakeConfigure<NativeTargetName>   -- e.g. cmakeConfigureLinuxX64
 * * cmakeBuild<NativeTargetName>       -- e.g. cmakeBuildLinuxX64
 * * cmakeInstall<NativeTargetName>     -- e.g. cmakeInstallLinuxX64
 *
 * CMake tasks may or may not run inside of a docker container depending on the native target being built.
 * All linux targets are built in a container.
 *
 * @param knTarget the native target to build CRT for
 * @param buildType the [CMakeBuildType] to build for CMake build type. Defaults to `RelWithDebInfo` since end users
 * can always strip the binary of all debug info.
 * @return the `cmakeInstall` task for the target which can be used to wire up additional task dependency relationships
*/
fun Project.configureCrtCMakeBuild(
    knTarget: KotlinNativeTarget,
    buildType: CMakeBuildType = CMakeBuildType.RelWithDebInfo,
): TaskProvider<Task> {
    verifyGitSubmodulesInitialized(this)

    val (cmakeInvocationExe, _) = cmakeInvocation(this, knTarget)
    if (!cmakeInvocationExe.startsWith("./")) {
        verifyOnPath(cmakeInvocationExe)
    }

    val cmakeConfigure = registerCmakeConfigureTask(knTarget, buildType)

    val cmakeBuild = registerCmakeBuildTask(knTarget, buildType)
    cmakeBuild.configure {
        dependsOn(cmakeConfigure)
    }

    val cmakeInstall = registerCmakeInstallTask(knTarget, buildType)
    cmakeInstall.configure {
        dependsOn(cmakeBuild)
    }

    // only enable cmake* tasks if that target is enabled
    val hm = HostManager()
    listOf(cmakeConfigure, cmakeBuild, cmakeInstall).forEach { task ->
        task.configure {
            onlyIf {
                hm.isEnabled(knTarget.konanTarget)
            }
        }
    }

    // TODO - add separate `cleanCMake<KN-Target>` tasks and make the parent `clean` task depend on the individuals
    tasks.named<Delete>("clean") {
        delete(project.rootProject.layout.buildDirectory.dir("cmake-build"))
        delete(project.rootProject.layout.buildDirectory.dir("crt-libs"))
    }

    return cmakeInstall
}

private fun Project.registerCmakeConfigureTask(
    knTarget: KotlinNativeTarget,
    buildType: CMakeBuildType,
): TaskProvider<Task> {
    val cmakeBuildDir = project.cmakeBuildDir(knTarget)
    val installDir = project.cmakeInstallDir(knTarget)

    val relativeBuildDir = cmakeBuildDir.relativeTo(project.rootDir).slashPath
    val relativeInstallDir = installDir.relativeTo(project.rootDir).slashPath
    val cmakeLists = project.rootProject.projectDir.resolve("CMakeLists.txt")

    return project.tasks.register(knTarget.cmakeConfigureTaskName) {
        group = "ffi"

        inputs.property("buildType", buildType.toString())
        inputs.file(cmakeLists)
        outputs.file(cmakeBuildDir.resolve("CMakeCache.txt"))

        doLast {
            val args = mutableListOf(
                "-B$relativeBuildDir",
                "-DCMAKE_BUILD_TYPE=$buildType",
                "-DCMAKE_INSTALL_PREFIX=$relativeInstallDir",
                "-DCMAKE_EXPORT_COMPILE_COMMANDS=ON",
                "-DBUILD_DEPS=ON",
                "-DBUILD_TESTING=OFF",
            )

            if (HostManager.hostIsMac && knTarget.konanTarget.family.isAppleFamily) {
                args.add("-GXcode")

                // FIXME - What should the min target for ios be? Does it matter for our build? DCMAKE_OSX_DEPLOYMENT_TARGET
                knTarget.konanTarget.osxArchitectureName?.let {
                    args.add("-DCMAKE_OSX_ARCHITECTURES=$it")
                }

                knTarget.konanTarget.osxSystemName?.let {
                    args.add("-DCMAKE_SYSTEM_NAME=$it")
                }

                // Xcode allows switching between device and simulator (via -sdk) even if we only configure one.
                // Unfortunately this breaks during install as there is no way to override and pass `-sdk` for
                // install like there is for `--build`. For simulator devices we set the name explicitly to
                // ensure the correct directory is searched.
                if (knTarget.konanTarget.isSimulatorSdk) {
                    args.add("-DCMAKE_OSX_SYSROOT=${knTarget.konanTarget.osxDeviceSdkName}")
                }
            }

            // FIXME? Compiling s2n-tls on GitHub Actions Ubuntu image (without Docker / cross-compilation) has errors like:
            // In function ‘s2n_hash_algorithms_init’
            // error: implicit declaration of function ‘EVP_MD_free’;
            // See https://github.com/aws/s2n-tls/blob/529b01a8363962a4e3809c9d9ee34fdd098fb0ba/tests/features/S2N_LIBCRYPTO_SUPPORTS_PROVIDERS.c#L29
            // and https://github.com/aws/s2n-tls/blob/529b01a8363962a4e3809c9d9ee34fdd098fb0ba/crypto/s2n_hash.c#L85

            // executed from root build dir which is where CMakeLists.txt is
            // We _could_ use the undocumented -H flag but that will be harder to make work inside docker
            args.add(".")

            runCmake(project, knTarget, args, name)
        }
    }
}

private fun Project.registerCmakeBuildTask(
    knTarget: KotlinNativeTarget,
    buildType: CMakeBuildType,
): TaskProvider<Task> {
    val cmakeBuildDir = project.cmakeBuildDir(knTarget)
    val relativeBuildDir = cmakeBuildDir.relativeTo(project.rootDir).slashPath

    return project.tasks.register(knTarget.cmakeBuildTaskName) {
        group = "ffi"

        inputs.property("buildType", buildType.toString())
        inputs.file(project.cmakeLists)
        inputs.files(
            fileTree("$rootDir/crt").matching {
                include(listOf("**/CMakeLists.txt", "**/*.c", "**/*.h"))
            },
        )

        outputs.dir(cmakeBuildDir)

        doLast {
            val coresPlusOne = (Runtime.getRuntime().availableProcessors().toInt() + 1).toString()

            val args = mutableListOf(
                "--build",
                relativeBuildDir,
                "--config",
                buildType.toString(),
                "--parallel",
                System.getProperty("org.gradle.workers.max", coresPlusOne),
            )

            val osxSdk = knTarget.konanTarget.osxDeviceSdkName
            if (osxSdk != null) {
                // see https://cmake.org/cmake/help/latest/manual/cmake-toolchains.7.html#switching-between-device-and-simulator
                // assumes Xcode generator
                args.add("--")
                args.add("-sdk")
                args.add(osxSdk)
            }

            runCmake(project, knTarget, args, name)
        }
    }
}

private fun Project.registerCmakeInstallTask(
    knTarget: KotlinNativeTarget,
    buildType: CMakeBuildType,
): TaskProvider<Task> {
    val cmakeBuildDir = project.cmakeBuildDir(knTarget)
    val relativeBuildDir = cmakeBuildDir.relativeTo(project.rootDir).slashPath
    val installDir = project.cmakeInstallDir(knTarget)

    return project.tasks.register(knTarget.cmakeInstallTaskName) {
        group = "ffi"

        inputs.file(project.cmakeLists)
        outputs.dir(installDir)

        doLast {
            val args = mutableListOf(
                "--install",
                relativeBuildDir,
                "--config",
                buildType.toString(),
            )
            runCmake(project, knTarget, args, name)
        }
    }
}

/**
 * Konan targets for which a container will be used execute CMake tasks. Targets that do not appear in this list or are
 * disabled by `aws.sdk.kotlin.crt.disableCrossCompile` will be compiled without using a container (i.e., in the same
 * shell executing Gradle).
 */
private val containerCompileTargets = setOf(
    KonanTarget.LINUX_X64,
    KonanTarget.LINUX_ARM64,
)

/**
 * Konan targets which require explicitly running CMake inside of Bash
 */
private val requiresExplicitBash = setOf(
    KonanTarget.MINGW_X64,
)

private fun cmakeInvocation(
    project: Project,
    target: KotlinNativeTarget,
    cmakeArgs: List<String>? = null,
): Pair<String, List<String>> {
    val disableContainerTargets = (project.properties["aws.crt.disableContainerTargets"] as? String ?: "")
        .split(',')
        .map { it.trim() }
        .toSet()

    val useContainer = target.konanTarget in containerCompileTargets &&
        target.konanTarget.name !in disableContainerTargets

    val exeArgs = cmakeArgs.orEmpty().toMutableList()
    val exeName = if (useContainer) {
        // cross compiling via dockcross - set the docker exe to cmake
        val containerScriptArgs = listOf("--args", "--pull=missing", "--", "cmake")
        exeArgs.addAll(0, containerScriptArgs)
        val script = "dockcross-" + target.konanTarget.name.replace("_", "-")
        validateCrossCompileScriptsAvailable(project, script)

        if (target.konanTarget in requiresExplicitBash) {
            exeArgs.add(0, "./$script")
            "bash"
        } else {
            "./$script"
        }
    } else {
        "cmake"
    }

    return exeName to exeArgs
}

private fun runCmake(
    project: Project,
    target: KotlinNativeTarget,
    cmakeArgs: List<String>,
    logName: String,
) {
    val (exeName, exeArgs) = cmakeInvocation(project, target, cmakeArgs)
    val commandLine = "$exeName ${exeArgs.joinToString(separator = " ")}"
    project.logger.info(commandLine)

    val logDir = project.rootProject.layout.buildDirectory.dir("cmake-logs").get().asFile
    logDir.mkdirs()
    val logFile = logDir.resolve("$logName.log")

    val execOps = project.serviceOf<ExecOperations>()

    // Stream stdout + stderr (interleaved, preserving ordering) directly to the per-task log file
    // so we don't buffer potentially large CMake/compiler output in memory.
    val result = logFile.outputStream().buffered().use { logStream ->
        execOps.exec {
            workingDir(project.rootDir)
            executable(exeName)
            args(exeArgs)
            standardOutput = logStream
            errorOutput = logStream
            // Don't throw on a non-zero exit; we surface diagnostics ourselves before failing the build.
            isIgnoreExitValue = true
        }
    }

    if (result.exitValue != 0) {
        project.logger.error(
            buildString {
                appendLine("CRT external command failed (exit code ${result.exitValue}): $commandLine")
                appendLine("Full output log: ${logFile.absolutePath}")
                appendLine("--- last $LOG_TAIL_LINES lines of ${logFile.name} ---")
                append(logFile.tailLines(LOG_TAIL_LINES))
            },
        )
        // Reproduce the default exec behaviour: fail the build with an ExecException.
        result.assertNormalExitValue()
    } else {
        project.logger.lifecycle("CRT external command succeeded: $commandLine\nOutput log: ${logFile.absolutePath}")
    }
}

/**
 * Number of trailing lines of a failed command's log file to echo to the Gradle console.
 */
private const val LOG_TAIL_LINES = 50

/**
 * Returns the last [n] lines of this file, keeping at most [n] lines in memory at a time.
 */
private fun File.tailLines(n: Int): String {
    val ring = ArrayDeque<String>(n)
    useLines { lines ->
        lines.forEach { line ->
            if (ring.size == n) ring.removeFirst()
            ring.addLast(line)
        }
    }
    return ring.joinToString(System.lineSeparator())
}

private fun validateCrossCompileScriptsAvailable(project: Project, script: String) {
    val scriptFile = project.rootProject.file(script)
    if (!scriptFile.exists()) {
        val message = """
        dockcross script: `$scriptFile` does not exist! Try re-building the relevant docker image(s) and generating
        the cross compile scripts.
        
        e.g. `./docker-images/build-all.sh`
        """.trimIndent()
        error(message)
    }
}

/**
 * Verifies that the Git submodules containing the AWS Common Runtime C sources are checked out. The submodules are
 * declared in `.gitmodules` at the repository root; an uninitialized submodule leaves an empty directory behind, so
 * each declared path is checked for existence and non-empty contents.
 */
private fun verifyGitSubmodulesInitialized(project: Project) {
    val rootDir = project.rootProject.projectDir
    val gitModulesFile = rootDir.resolve(".gitmodules")

    // If there's no .gitmodules there's nothing to verify (e.g. building from a source archive).
    if (!gitModulesFile.isFile) return

    val uninitializedPaths = gitModulesFile
        .readLines()
        .map { it.trim() }
        .filter { it.startsWith("path =") }
        .map { it.substringAfter("path =").trim() }
        .filter { path ->
            val dir = rootDir.resolve(path)
            val contents = dir.listFiles()
            !dir.isDirectory || contents == null || contents.isEmpty()
        }

    if (uninitializedPaths.isNotEmpty()) {
        throw IllegalStateException(
            buildString {
                appendLine("The following required Git submodule(s) are missing or uninitialized:")
                uninitializedPaths.forEach { appendLine("- $it") }
                appendLine()
                appendLine("Initialize and update all submodules by running this command from the repository root:")
                appendLine()
                appendLine("    git submodule update --init --recursive")
                appendLine()
                appendLine("See the project README.md for more information about prerequisites for lthis project.")
            },
        )
    }
}

private fun verifyOnPath(executable: String) {
    if (findOnPath(executable) == null) {
        throw IllegalStateException(
            "Cannot find required executable `$executable` on the system `PATH`. Please verify it is installed " +
                "correctly and is accessible in the terminal. See the project README.md for more information about " +
                "prerequisites for building this project."
        )
    }
}

private fun findOnPath(executable: String): File? {
    val extensions = when {
        HostManager.hostIsMingw -> listOf("", ".exe", ".cmd", ".bat") // Windows executables may have these extensions
        else -> listOf("") // No automatic extensions for Linux or macOS
    }

    return System
        .getenv("PATH")
        ?.split(File.pathSeparator)
        ?.map(::File)
        ?.flatMap { dir -> extensions.map { ext -> File(dir, "$executable$ext") } }
        ?.firstOrNull { it.isFile && it.canExecute() }
}
