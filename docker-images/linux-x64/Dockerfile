# We need glibc <= 2.19, see https://youtrack.jetbrains.com/issue/KT-47061
# TODO - explore creating a custom image using the same toolchain K/N uses
# see https://github.com/JetBrains/kotlin/tree/master/kotlin-native/tools/toolchain_builder
# OR via 
FROM dockcross/manylinux2014-x64:20240104-6eda627
ENV DEFAULT_DOCKCROSS_IMAGE aws-crt-kotlin/linux-x64:latest

RUN curl -LJo konan-toolchain.tar.gz https://download.jetbrains.com/kotlin/native/x86_64-unknown-linux-gnu-gcc-8.3.0-glibc-2.19-kernel-4.9-2.tar.gz

# Reset the toolchain and environment based on the K/N toolchain
RUN rm -rf /opt/rh/devtoolset-10 && \
    mkdir -p /usr/xcc/x86_64-unknown-linux-gnu && \
    tar -xzf konan-toolchain.tar.gz -C /usr/xcc/x86_64-unknown-linux-gnu --strip-components=1 && \
    rm konan-toolchain.tar.gz

ENV XCC_PREFIX /usr/xcc
ENV CROSS_TRIPLE x86_64-unknown-linux-gnu
ENV CROSS_ROOT ${XCC_PREFIX}/${CROSS_TRIPLE}
ENV PATH ${PATH}:${CROSS_ROOT}/bin

ENV AS=${CROSS_TRIPLE}-as \
    AR=${CROSS_TRIPLE}-ar \
    CC=${CROSS_TRIPLE}-gcc \
    CPP=${CROSS_TRIPLE}-cpp \
    CXX=${CROSS_TRIPLE}-g++ \
    LD=${CROSS_TRIPLE}-ld \
    FC=${CROSS_TRIPLE}-gfortran \
    STRIP=${CROSS_TRIPLE}-strip \
    OBJCOPY=${CROSS_TRIPLE}-objcopy

ENV QEMU_LD_PREFIX "${CROSS_ROOT}/${CROSS_TRIPLE}/sysroot"
ENV QEMU_SET_ENV "LD_LIBRARY_PATH=${CROSS_ROOT}/lib:${QEMU_LD_PREFIX}"

COPY docker-images/linux-x64/Toolchain.cmake ${CROSS_ROOT}/../lib/
ENV CMAKE_TOOLCHAIN_FILE ${CROSS_ROOT}/../lib/Toolchain.cmake
