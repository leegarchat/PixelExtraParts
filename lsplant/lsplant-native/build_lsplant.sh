#!/bin/bash
# One-shot NDK build of liblsplant.so (LSPlant + Dobby + JNI glue).
# Output (staging): pine/lsplant-native/out/<abi>/liblsplant.so
# Install into prebuilts (pine/libs/lsplant/) is a separate manual step.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NDK="${ANDROID_NDK:-${ANDROID_NDK_HOME:-/opt/android-sdk/ndk/29.0.14206865}}"
ABI="${ABI:-arm64-v8a}"
PLATFORM="${PLATFORM:-android-34}"
BUILD_DIR="$SCRIPT_DIR/out/$ABI"

if [[ ! -d "$NDK" ]]; then
    echo "NDK not found at $NDK (set ANDROID_NDK)" >&2
    exit 1
fi

cmake -B "$BUILD_DIR" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$ABI" \
    -DANDROID_PLATFORM="$PLATFORM" \
    -DANDROID_STL=c++_shared \
    -DCMAKE_BUILD_TYPE=MinSizeRel \
    -S "$SCRIPT_DIR"
cmake --build "$BUILD_DIR"
echo "OK: $(find "$BUILD_DIR" -name liblsplant.so)"
