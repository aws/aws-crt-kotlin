## AWS CRT Kotlin

Kotlin bindings to the AWS Common Runtime

[![License][apache-badge]][apache-url]

[apache-badge]: https://img.shields.io/badge/License-Apache%202.0-blue.svg
[apache-url]: LICENSE

## License

This project is licensed under the Apache-2.0 License.

## Building

CRT interfaces are subject to change.

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
Ensure Docker is installed, then build the image using `./docker-images/build-all.sh`. 
Run a `./gradlew build` and the build and tests should be routed through to the container.
