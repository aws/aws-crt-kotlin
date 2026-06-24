## AWS CRT Kotlin

Kotlin bindings to the AWS Common Runtime

[![License][apache-badge]][apache-url]

[apache-badge]: https://img.shields.io/badge/License-Apache%202.0-blue.svg
[apache-url]: LICENSE

## License

This project is licensed under the Apache-2.0 License.

## Building

CRT interfaces are subject to change.

### Docker

Building CRT for Kotlin/Native on Linux requires Docker images to be locally built and consumed. Before running the
Gradle build for this project, ensure that Docker is installed and run:

```sh
./docker-images/build-all.sh
```

If you encounter this error:

```
Unable to find image 'aws-crt-kotlin/linux-x64:latest' locally
docker: Error response from daemon: pull access denied for aws-crt-kotlin/linux-x64, repository does not exist or may require 'docker login': denied: requested access to the resource is denied.
See 'docker run --help'.
```

Then your Docker daemon may not be properly configured to allow non-root access. To allowlist your account for access to
the Docker daemon, run:

```sh
sudo usermod -aG docker $USER
```

### Git Submodules

This repository makes use of [Git submodules](https://git-scm.com/book/en/v2/Git-Tools-Submodules). The first time you
clone the repository, you will also need to initialize the submodules:

```sh
git submodule update --init --recursive
```

### Linux/Unix

#### Testing Different Linux Distros and Architectures

1. Build the test executable(s) for the architecture(s) you want to test. 

```sh
# build everything
./gradlew linuxTestBinaries

# build specific arch
./gradlew linuxX64TestBinaries
```

2. Use the `run-container-test.py` helper script to execute tests locally

```sh
OCI_EXE=docker python3 .github/scripts/run-container-test.py --distro al2 --arch x64 --test-bin-dir ./aws-crt-kotlin/build/bin
```

See the usage/help for different distributions provided: `python3 .github/scripts/run-container.py -h`

### OSX

#### Debugging simulator test issues

**Xcode does not support simulator tests for \<native target>**

```
* What went wrong:
Execution failed for task ':aws-crt-kotlin:iosX64Test'.
> Error while evaluating property 'device' of task ':aws-crt-kotlin:iosX64Test'.
   > Failed to calculate the value of task ':aws-crt-kotlin:iosX64Test' property 'device'.
      > Xcode does not support simulator tests for ios_x64. Check that requested SDK is installed.
```

Ensure that you have an appropriate simulator runtime installed.

e.g. to install `iOS` platform support including simulator runtimes:
```sh
xcodebuild -downloadPlatform iOS
```

List simulator runtimes with:

```sh
xcrun simctl list devices available
```

See also:
* https://developer.apple.com/documentation/xcode/installing-additional-simulator-runtimes
* https://www.iosdev.recipes/simctl/

### Windows

Building on Windows is slightly trickier and involves more prerequisites to configure the toolchain. There are slightly
different steps depending on whether you intend to build from an **MSYS2 MinGW64**
terminal or from a **PowerShell** terminal:

#### Common Windows steps (terminal agnostic)

* [Install Git](https://git-scm.com/install/)
  * You may also need to [configure SSH access to GitHub](https://docs.github.com/en/authentication/connecting-to-github-with-ssh).
* Install a distribution of JDK 17 or higher such as [Amazon Corretto](https://downloads.corretto.aws/)
  * Note that JDK 26+ is not supported yet due to the Kotlin Gradle plugin
* [Install MSYS2](https://www.msys2.org/#installation)
* Launch an **MSYS2 MinGW64** terminal from the Start menu
* Install the toolchain for MinGW64: `pacman -S mingw-w64-x86_64-cmake mingw-w64-x86_64-toolchain`

#### Steps to build using MSYS2 MinGW64

After following the [Common Windows steps (terminal agnostic)](#common-windows-steps-terminal-agnostic), in an **MSYS2
MinGW64** terminal:

* Add Git and the JDK to the `PATH` environment variable:
  * `echo 'export PATH="$PATH:/c/Program Files/Git/bin"' >> ~/.bashrc`

    **⚠️ Note**: The exact path may differ on your system depending on how you installed Git
  * `echo 'export PATH="$PATH:/c/Program Files/Amazon Corretto/jdk25.0.3_9/bin"' >> ~/.bashrc`

    **⚠️ Note**: The exact path may differ on your system depending on which JDK distribution/version you installed
  * `. ~/.bashrc`

    This refreshes the `PATH` environment variable with the preceding updates
* Clone and build **aws-crt-kotlin**:

  ```shell
  cd <path/to/workspace>
  git clone git@github.com:aws/aws-crt-kotlin.git
  cd aws-crt-kotlin
  git submodule update --init --recursive
  ./gradlew build
  ```

#### Steps to build using PowerShell

After following the [Common Windows steps (terminal agnostic)](#common-windows-steps-terminal-agnostic), in a
**PowerShell** terminal:

* Add Bash and MinGW to the `PATH` environment variable:
  * `[Environment]::SetEnvironmentVariable("PATH", $env:PATH + ";C:\msys64\usr\bin;C:\msys64\mingw64\bin", "Machine")`

    **⚠️ Note**: The exact path may differ on your system depending on how you installed MSYS2
* Configure `MINGW_PREFIX` environment variable:
  * `[Environment]::SetEnvironmentVariable("MINGW_PREFIX", "C:\msys64\mingw64", "Machine")`

    **⚠️ Note**: The exact path may differ on your system depending on how you installed MSYS2
* Close and restart the **PowerShell** terminal so that the environment variable updates take effect
* Clone and build **aws-crt-kotlin**:

  ```
  cd <path\to\workspace>
  git clone git@github.com:aws/aws-crt-kotlin.git
  cd aws-crt-kotlin
  git submodule update --init --recursive
  .\gradlew.bat build
  ```
