# Glue-only build: JNI bridge -> liblspbridge.so.
# The bridge does NOT link LSPlant/Dobby at build time (no DT_NEEDED).
# At runtime it binds to an already-loaded foreign engine first
# (Vector/LSPosed export the LSPlant + Dobby entry points) and falls back
# to dlopen()ing our own /system prebuilts. Headers below are API-only.
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

# --- Dobby (inline hook backend, Apache-2.0): headers only (API types).
# The binary is bound at runtime (foreign engine or our own prebuilt).
# Official Vector-shipped binary (source incompatible with NDK r29 clang).
add_library(dobby_headers INTERFACE)
target_include_directories(dobby_headers INTERFACE
    "${CMAKE_CURRENT_SOURCE_DIR}/third_party/dobby/include"
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
# No DT_NEEDED on LSPlant/Dobby: bound at runtime via dlopen/dlsym.
# --allow-shlib-undefined keeps the link working with function pointers.
target_link_libraries(lspbridge PRIVATE
    dobby_headers
    log
    dl
)
target_compile_options(lspbridge PRIVATE -Oz -fvisibility=hidden)
target_link_options(lspbridge PRIVATE -Wl,--gc-sections -Wl,--allow-shlib-undefined)
set_target_properties(lspbridge PROPERTIES OUTPUT_NAME lspbridge)
