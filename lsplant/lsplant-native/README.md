# LSPlant native backend (JNI glue + engine binding)

## Why
LSPlant native engine (last upstream: Nov 2025, Android 15-era ART
assumptions) crashes randomly on Android 17 ART: native deaths in every
hooked process, including inside LSPlant own bridge. Upstream LSPlant is
dormant; the Tine fork author states the GC problem can only be mitigated,
never cured in that design. LSPosed/LSPlant is maintained (Android 5-17)
and resolves ART symbols dynamically instead of hardcoding layouts.

## Layout
- `bridge/bridge.cpp` — JNI glue for
  `org.pixel.customparts.manager.lsplant.LsplantBridge`
  (`nativeInit`/`nativeDoHook`/`nativeUnHook`/`nativeDeoptimize`).
- `bridge/art_elf.{h,cpp}` — minimal libart.so symbol resolver (exact via
  `dlopen(RTLD_NOLOAD)+dlsym`, prefix via manual ELF walk over .dynsym,
  on-disk .symtab fallback).
- `third_party/lsplant/` — official LSPlant **6.4** arm64 prebuilt
  (`org.lsposed.lsplant:lsplant:6.4`, Maven Central) + public headers.
  LGPL-3.0, source: https://github.com/LSPosed/LSPlant
- `third_party/dobby/` — inline-hook backend binary (arm64) shipped by
  Vector/LSPosed canary + `dobby.h` from jmpews/Dobby sources.
  Apache-2.0, source: https://github.com/jmpews/Dobby
- `out/<abi>/liblspbridge.so` — build staging (not committed).

## Build
Needs NDK r28+ (tested r29), CMake 3.28+, ninja:
`./build_lsplant.sh` (env: `ANDROID_NDK`, `ABI`, `PLATFORM`).

## Install into prebuilts
After a successful build + device soak test, copy the three .so files to
`lsplant/libs/lsplant/arm64-v8a/` (liblsplant.so, libdobby.so,
liblspbridge.so) — they ship to `/system/lib64` via `cc_prebuilt_*`
modules in `Android.bp` / `device.mk`.

## License notes
- LSPlant core: LGPL-3.0 — shipped as a separate unmodified shared
  library; source available at the URL above.
- Dobby: Apache-2.0.
- `bridge/` + headers usage: ours.

- `lsplant/libs/lsplant/arm64-v8a/libc++_shared.so`: NDK r29 shared STL,
  required at runtime by the prefab liblsplant.so (the platform itself
  carries no libc++_shared). Shipped to /system/lib64 alongside.
