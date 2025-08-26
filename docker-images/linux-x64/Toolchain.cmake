set(CMAKE_SYSTEM_NAME Linux)
set(CMAKE_CROSSCOMPILING FALSE)
set(CMAKE_SYSTEM_VERSION 1)
set(CMAKE_SYSTEM_PROCESSOR x86_64)

set(MANYLINUX2014 TRUE)

set(cross_triple "x86_64-unknown-linux-gnu")
set(cross_root /usr/xcc/${cross_triple})

set(CMAKE_C_COMPILER $ENV{CC})
set(CMAKE_CXX_COMPILER $ENV{CXX})
set(CMAKE_Fortran_COMPILER $ENV{FC})

set(CMAKE_CXX_FLAGS "-I ${cross_root}/include/")

set(CMAKE_FIND_ROOT_PATH ${cross_root} ${cross_root}/${cross_triple})
set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY BOTH)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE BOTH)
set(CMAKE_SYSROOT ${cross_root}/${cross_triple}/sysroot)
