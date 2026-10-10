# Glue-only build: Dobby (static) + JNI bridge -> liblspbridge.so.
# LSPlant core itself is the official prefab binary
# (third_party/lsplant/arm64-v8a/liblsplant.so, 6.4, LGPL-3.0),
# linked at build time (DT_NEEDED) and shipped alongside.
#
# Usage:
#   cmake -B out -G Ninja \
#     -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
#     -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-34 \
#     -DCMAKE_BUILD_TYPE=MinSizeRel
#   cmake --build out
cmake_minimum_required(VERSION 3.22)
project(pixel_lspbridge)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# --- Dobby (inline hook backend, Apache-2.0): official Vector-shipped
# binary (source incompatible with NDK r29 clang); headers kept for API.
add_library(dobby_static SHARED IMPORTED)
set_target_properties(dobby_static PROPERTIES
    IMPORTED_LOCATION "${CMAKE_CURRENT_SOURCE_DIR}/third_party/dobby/arm64-v8a/libdobby.so"
    INTERFACE_INCLUDE_DIRECTORIES "${CMAKE_CURRENT_SOURCE_DIR}/third_party/dobby/include"
)

# --- JNI glue + ART ELF resolver -> liblspbridge.so ---
add_library(lspbridge SHARED
    bridge/bridge.cpp
    bridge/art_elf.cpp
)
target_include_directories(lspbridge PRIVATE
    bridge
    third_party/lsplant/include
)
target_include_directories(lspbridge PRIVATE
    $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/third_party/dobby/include>
)
# Official LSPlant 6.4 arm64 prebuilt (prefab).
add_library(lsplant_prebuilt SHARED IMPORTED)
set_target_properties(lsplant_prebuilt PROPERTIES
    IMPORTED_LOCATION "${CMAKE_CURRENT_SOURCE_DIR}/third_party/lsplant/arm64-v8a/liblsplant.so"
)
target_link_libraries(lspbridge PRIVATE
    lsplant_prebuilt
    dobby_static
    log
    dl
)
target_compile_options(lspbridge PRIVATE -Oz -fvisibility=hidden)
target_link_options(lspbridge PRIVATE -Wl,--gc-sections)
set_target_properties(lspbridge PROPERTIES OUTPUT_NAME lspbridge)
