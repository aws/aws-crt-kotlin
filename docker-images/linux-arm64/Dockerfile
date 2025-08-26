FROM dockcross/manylinux2014-aarch64:20240104-6eda627
ENV DEFAULT_DOCKCROSS_IMAGE aws-crt-kotlin/linux-arm64:latest

RUN curl -LJo konan-toolchain.tar.gz https://download.jetbrains.com/kotlin/native/aarch64-unknown-linux-gnu-gcc-8.3.0-glibc-2.25-kernel-4.9-2.tar.gz
# Remove the cross compile root from the original image and put our own one based
# on the K/N toolchain.
RUN mv /usr/xcc/aarch64-unknown-linux-gnu /tmp/original && \
    mkdir -p /usr/xcc/aarch64-unknown-linux-gnu && \
    tar -xzf konan-toolchain.tar.gz -C /usr/xcc/aarch64-unknown-linux-gnu --strip-components=1 && \
    cp /tmp/original/Toolchain.cmake /usr/xcc/aarch64-unknown-linux-gnu && \
    rm -rf /tmp/original
