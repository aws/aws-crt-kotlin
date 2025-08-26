cmake_minimum_required(VERSION 3.21)
project(aws-crt-kotlin C)
message(STATUS "CMake ${CMAKE_VERSION}")
option(BUILD_DEPS "Builds aws common runtime dependencies as part of build. Turn off if you want to control your dependency chain." ON)
option(BUILD_SHARED_LIBS "Build shared library for FFI: default: OFF" OFF)

if (POLICY CMP0069)
    cmake_policy(SET CMP0069 NEW) # Enable LTO/IPO if available in the compiler
endif()

if (DEFINED CMAKE_PREFIX_PATH)
    file(TO_CMAKE_PATH "${CMAKE_PREFIX_PATH}" CMAKE_PREFIX_PATH)
endif()

if (NOT DEFINED CMAKE_INSTALL_LIBDIR)
    set(CMAKE_INSTALL_LIBDIR "lib")
endif()

if (UNIX AND NOT APPLE)
    set(FIND_LIBRARY_USE_LIB64_PATHS true)
endif()

# Multi-config generators like Xcode, Visual Studio, and Ninja don't always respect
# CMAKE_BUILD_TYPE. Set the available configuration to the build type.
if (${CMAKE_BUILD_TYPE} STREQUAL "Release")
    set(CMAKE_CONFIGURATION_TYPES ${CMAKE_BUILD_TYPE})
    message(STATUS "Setting CMAKE_CONFIGURATION_TYPES = ${CMAKE_CONFIGURATION_TYPES}")
endif()

# This is required in order to append /lib/cmake to each element in CMAKE_PREFIX_PATH
set(AWS_MODULE_DIR "/${CMAKE_INSTALL_LIBDIR}/cmake")
string(REPLACE ";" "${AWS_MODULE_DIR};" AWS_MODULE_PATH "${CMAKE_PREFIX_PATH}${AWS_MODULE_DIR}")
# Append that generated list to the module search path
list(APPEND CMAKE_MODULE_PATH ${AWS_MODULE_PATH})

set(ADDITIONAL_DEPS "")

if (BUILD_DEPS)
    list(APPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_LIST_DIR}/crt/aws-c-common/cmake")

    include(AwsFindPackage)

    set(IN_SOURCE_BUILD ON)
    set(SEARCH_LIBCRYPTO OFF CACHE BOOL "Let S2N use libcrypto from AWS-LC.")

    # Don't compile tests in subdirectories.
    # Turn off using `option` instead of `set`, or CTest will declare
    # it as an option later and override the existing variable.
    set(BUILD_TESTING OFF)

    # Disable BUILD_SHARED_LIBS for all CRT libs
    set(SHARED_FFI_LIB ${BUILD_SHARED_LIBS})
    set(BUILD_SHARED_LIBS OFF)

    # CRT Libraries
    add_subdirectory(crt/aws-c-common)
    if (UNIX AND NOT APPLE)
        if (NOT USE_OPENSSL)
            if (NOT (CMAKE_SYSTEM_PROCESSOR STREQUAL aarch64) AND
                NOT (CMAKE_SYSTEM_PROCESSOR STREQUAL armv7l))
                set(DISABLE_PERL ON)
            endif()
            if(CMAKE_C_COMPILER_ID MATCHES "GNU" AND CMAKE_C_COMPILER_VERSION VERSION_LESS "5.0")
                set(DISABLE_PERL OFF CACHE BOOL "Build with Perl to avoid using pre-compiled binary with AVX512")
                set(MY_ASSEMBLER_IS_TOO_OLD_FOR_512AVX ON CACHE BOOL "Disable AVX512 on old GCC that not supports it")
            endif()
            set(DISABLE_GO ON)
            set(SEARCH_LIBCRYPTO OFF)
            set(BUILD_LIBSSL OFF)
            list(APPEND ADDITIONAL_DEPS crypto)
            add_subdirectory(crt/aws-lc)
        else()
            set(SEARCH_LIBCRYPTO ON)
            # Find the system libcrypto and propagate its location to s2n's find script
            find_package(OpenSSL REQUIRED)
            set(LibCrypto_INCLUDE_DIR ${OPENSSL_INCLUDE_DIR})
            set(LibCrypto_LIBRARY ${OPENSSL_CRYPTO_LIBRARY})
            if (LibCrypto_LIBRARY MATCHES ".so$")
                set(LibCrypto_SHARED_LIBRARY ${LibCrypto_LIBRARY})
            else()
                set(LibCrypto_STATIC_LIBRARY ${LibCrypto_LIBRARY})
            endif()
        endif()
        add_subdirectory(crt/s2n)
        list(APPEND ADDITIONAL_DEPS s2n)
    endif()
    add_subdirectory(crt/aws-c-sdkutils)
    add_subdirectory(crt/aws-c-io)
    add_subdirectory(crt/aws-c-cal)
    add_subdirectory(crt/aws-c-compression)
    add_subdirectory(crt/aws-c-http)
    add_subdirectory(crt/aws-c-auth)
    add_subdirectory(crt/aws-checksums)
else()
    include(AwsFindPackage)
    set(IN_SOURCE_BUILD OFF)
endif()

include(AwsCFlags)
include(AwsSharedLibSetup)
include(AwsSanitizers)

aws_use_package(aws-c-common)
aws_use_package(aws-c-sdkutils)
aws_use_package(aws-c-io)
aws_use_package(aws-c-cal)
aws_use_package(aws-c-compression)
aws_use_package(aws-c-http)
aws_use_package(aws-c-auth)
aws_use_package(aws-checksums)

if (XCODE)
    # we only ever build for a single device/simulator and turning this on leaks into generator expressions like `$<TARGET_FILE:${tgt}>`
    # and breaks for ios/tvos/watchos builds
    set_property(GLOBAL PROPERTY XCODE_EMIT_EFFECTIVE_PLATFORM_NAME OFF)
endif()

# Create a single static library that combines all the individual libs
# We do this to work around issues with link order when including multiple
# `staticLibraries` in the cinterop file.
function(bundle_static_library name deps)
    set(tgt_full_name ${CMAKE_BINARY_DIR}/${CMAKE_STATIC_LIBRARY_PREFIX}${name}${CMAKE_STATIC_LIBRARY_SUFFIX})

    if(XCODE)
        find_program(lib_tool libtool)
        foreach(tgt IN LISTS deps)
            list(APPEND static_libs_full_names $<TARGET_FILE:${tgt}>)
        endforeach()

        add_custom_command(
            COMMAND 
                ${lib_tool} -static -o ${tgt_full_name} ${static_libs_full_names}
            OUTPUT 
                ${tgt_full_name}
            COMMENT 
                "Bundling ${name}"
            VERBATIM
            COMMAND_EXPAND_LISTS
        )

    elseif (CMAKE_C_COMPILER_ID MATCHES "^(Clang|GNU)$")
        # archive tool needs to support -M flag 
        file(WRITE ${CMAKE_BINARY_DIR}/${name}.ar.in "CREATE ${tgt_full_name}\n")

        foreach(tgt IN LISTS deps)
            message("tgt=${tgt}")
            file(APPEND ${CMAKE_BINARY_DIR}/${name}.ar.in "ADDLIB $<TARGET_FILE:${tgt}>\n")
        endforeach()

        file(APPEND ${CMAKE_BINARY_DIR}/${name}.ar.in "SAVE\n")
        file(APPEND ${CMAKE_BINARY_DIR}/${name}.ar.in "END\n")

        file(
            GENERATE
            OUTPUT ${CMAKE_BINARY_DIR}/${name}.ar
            INPUT ${CMAKE_BINARY_DIR}/${name}.ar.in
        )

        add_custom_command(
            COMMAND ${CMAKE_AR} -M < ${CMAKE_BINARY_DIR}/${name}.ar
            OUTPUT ${tgt_full_name}
            COMMENT "Bundling ${name}"
            VERBATIM
        )

    elseif (CMAKE_C_COMPILER_ID MATCHES "^MSVC$")
        foreach(tgt IN LISTS deps)
            list(APPEND static_libs_full_names $<TARGET_FILE:${tgt}>)
        endforeach()

        add_custom_command(
            COMMAND ${CMAKE_AR} /OUT:${tgt_full_name} ${static_libs_full_names}
            OUTPUT ${tgt_full_name}
            COMMENT "Bundling ${name}"
            VERBATIM
            COMMAND_EXPAND_LISTS
        )

    else()
        message(FATAL_ERROR "Unknown bundling for C compiler ${CMAKE_C_COMPILER_ID}!")
    endif()

    add_custom_target(bundling_target ALL DEPENDS ${tgt_full_name})
    foreach(tgt IN LISTS deps)
        message("adding dependency on ${tgt}")
        add_dependencies(bundling_target ${tgt})
    endforeach()

  add_library(${name} STATIC IMPORTED)
  set_target_properties(${name}
    PROPERTIES
      IMPORTED_LOCATION ${tgt_full_name}
      INTERFACE_INCLUDE_DIRECTORIES $<TARGET_PROPERTY:${name},INTERFACE_INCLUDE_DIRECTORIES>)
  add_dependencies(${name} bundling_target)

  install(FILES ${tgt_full_name} TYPE LIB)
endfunction()

list(APPEND STATIC_LIBS ${DEP_AWS_LIBS} ${ADDITIONAL_DEPS})
bundle_static_library(aws-crt-kotlin "${STATIC_LIBS}")

message("CMAKE_C_COMPILER_ID=${CMAKE_C_COMPILER_ID}")
