# Changelog

## [0.12.1] - 02/16/2026

## [0.12.0] - 01/20/2026

### Features
* [#207](https://github.com/aws/aws-crt-kotlin/issues/207) Add reference counting to CRT shutdown process

## [0.11.7] - 11/04/2025

## [0.11.6] - 10/10/2025

## [0.11.5] - 09/22/2025

## [0.11.4] - 09/10/2025

## [0.11.2] - 09/09/2025

## [0.11.1] - 09/08/2025

## [0.11.0] - 09/03/2025

### Features
* [#229](https://github.com/awslabs/aws-sdk-kotlin/issues/229) ⚠️ **IMPORTANT**: Add support for Kotlin/Native

### Miscellaneous
* Upgrade to Gradle 9.0.0

## [0.10.3] - 08/12/2025

## [0.10.2] - 07/24/2025

## [0.10.1] - 07/21/2025

## [0.10.0] - 07/15/2025

### Features
* Upgrade to Kotlin 2.2.0

### Fixes
* [#1295](https://github.com/smithy-lang/smithy-kotlin/issues/1295) Enable building this project on JDK21 by setting -Xjdk-release flag

## [0.9.2] - 06/18/2025

### Fixes
* Update the set of supported `TlsCipherPreference` values to reflect the latest capabilities/support from CRT

## [0.9.1] - 01/28/2025

### Fixes
* Upgrade to latest version of CRT to pick up header signing changes

## [0.9.0] - 01/15/2025

### Miscellaneous
* Upgrade to Kotlin 2.1.0

## [0.8.10] - 10/16/2024

### Miscellaneous
* Upgrade to Kotlin 2.0.21
* Upgrade aws-crt-java to 0.31.3

## [0.8.9] - 09/18/2024

### Miscellaneous
* Upgrade to kotlinx.coroutines 1.9.0

## [0.8.8] - 08/06/2024

### Miscellaneous
* Upgrade to Kotlin 2.0.10
* Upgrade to aws-crt-java 0.30.5

## [0.8.7] - 07/25/2024

### Miscellaneous
* Upgrade to Kotlin 2.0.0

## [0.8.6] - 06/28/2024

### Miscellaneous
* Upgrade to aws-crt-java v0.29.25
* Upgrade to ktlint v1.3.0

## [0.8.5] - 02/09/2024

### Features
* Add SIGV4_S3EXPRESS signing algorithm and AwsSigningConfig.toBuilder function

## [0.8.4] - 01/10/2024

### Features
* [#893](https://github.com/smithy-lang/smithy-kotlin/issues/893) Surface HTTP connection manager metrics

## [0.8.3] - 01/05/2024

### Features
* [#893](https://github.com/smithy-lang/smithy-kotlin/issues/893) Enable access to metrics for HTTP streams

## [0.8.2] - 11/17/2023

### Miscellaneous
* Upgrade dependencies to their latest versions, notably Kotlin 1.9.20

## [0.8.0] - 10/23/2023

### Miscellaneous
* Bump minor version for internal versioning compatibility

## [0.7.4] - 10/23/2023
### Miscellaneous
* Upgrade Gradle's heap and metaspace memory allocations   

## [0.7.3] - 10/19/2023

### Miscellaneous
* Upgrade to Kotlin 1.9.10
* Upgrade to aws-crt-java 0.27.4

## [0.7.1] - 09/14/2023

### Miscellaneous
* bump aws-crt-java to 0.26.1

## [0.7.0] - 08/10/2023

### Miscellaneous
* Upgrade Kotlin to 1.8.22
* Upgrade kotlinx.coroutines to 1.7.3

## [0.6.8] - 02/09/2023

### Miscellaneous
* Update Java CRT dependency to 0.21.7
* Upgrade Kotlin to 1.8.10 and third party dependencies to their latest versions

## [0.6.7] - 12/15/2022

### Features
* [#759](https://github.com/smithy-lang/smithy-kotlin/issues/759) Add `writeChunk` method to HttpStream

## [0.6.6] - 11/22/2022

### Features
*  Implement signChunkTrailer

## [0.6.5] - 10/13/2022

### Fixes
* [#715](https://github.com/awslabs/aws-sdk-kotlin/issues/715) Enable intra-repo links in API ref docs

### Miscellaneous
* Update/clarify changelog and add commit instructions in the Contributing Guidelines

## [0.6.4] - 08/18/2022

### Fixes
* [#55](https://github.com/awslabs/aws-crt-kotlin/issues/55) Upgrade aws-crt-java dependency to fix Mac dlopen issue
* [#601](https://github.com/awslabs/aws-sdk-kotlin/issues/601) Remove incorrect `.` at end of license identifier header in source files.

### Miscellaneous
* Upgrade ktlint to 0.46.1.
* Upgrade Kotlin version to 1.7.10

## [0.5.4] - 03/24/2022

### New features
* expose chunked signing [#47](https://github.com/awslabs/aws-crt-kotlin/pull/47)

## [0.5.3] - 02/17/2022

### Miscellaneous
* bump aws-crt-java and coroutines to latest [#45](https://github.com/awslabs/aws-crt-kotlin/pull/45)

## [0.5.2] - 01/06/2022

### New features
* upgrade to Kotlin 1.6.10 [#42](https://github.com/awslabs/aws-crt-kotlin/pull/42)

