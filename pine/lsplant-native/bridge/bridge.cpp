// JNI glue: LSPlant backend for the PixelExtraParts Xposed-compat shim.
//
// Java peer: org.pixel.customparts.manager.lsplant.LsplantBridge
//   private static native boolean nativeInit();
//   private static native Object nativeDoHook(Object target, Object hooker, Object callback);
//   private static native boolean nativeUnHook(Object target);
//   private static native void nativeDeoptimize(Object method);
//
// This library is glue only: it does NOT link LSPlant/Dobby at build time
// (no DT_NEEDED on them). At runtime it binds to an ALREADY-LOADED engine
// first (Vector/LSPosed zygisk lib exports the LSPlant + Dobby entry points),
// and only if none is present it loads our own /system prebuilts. This avoids
// merging two LSPlant/Dobby cores into one process — the native conflict.
#include <jni.h>

#include <string>
#include <string_view>
#include <vector>

#include <dlfcn.h>
#include <link.h>

#include "art_elf.h"
#include "dobby.h"
#include <android/log.h>
#include "lsplant.hpp"

namespace {

// Mangled LSPlant entry points (inline namespace v2 — must match the
// engine we bind to; both ours and Vector v2.2 export exactly these).
constexpr const char* kSymInit =
    "_ZN7lsplant2v24InitEP7_JNIEnvRKNS0_8InitInfoE";
constexpr const char* kSymHook =
    "_ZN7lsplant2v24HookEP7_JNIEnvP8_jobjectS4_S4_";
constexpr const char* kSymUnHook =
    "_ZN7lsplant2v26UnHookEP7_JNIEnvP8_jobject";
constexpr const char* kSymDeoptimize =
    "_ZN7lsplant2v210DeoptimizeEP7_JNIEnvP8_jobject";
constexpr const char* kSymDobbyHook = "DobbyHook";
constexpr const char* kSymDobbyDestroy = "DobbyDestroy";

struct EngineApi {
    void* handle = nullptr;
    std::string origin;
    bool foreign = false;
    bool (*Init)(JNIEnv*, const lsplant::InitInfo&) = nullptr;
    jobject (*Hook)(JNIEnv*, jobject, jobject, jobject) = nullptr;
    bool (*UnHook)(JNIEnv*, jobject) = nullptr;
    void (*Deoptimize)(JNIEnv*, jobject) = nullptr;
    int (*DobbyHook)(void*, void*, void**) = nullptr;
    int (*DobbyDestroy)(void*) = nullptr;

    bool valid() const {
        return handle && Init && Hook && UnHook && Deoptimize && DobbyHook && DobbyDestroy;
    }
};

EngineApi g_engine;

bool BindHandle(void* handle, const char* origin, bool foreign, EngineApi& out) {
    EngineApi api;
    api.handle = handle;
    api.origin = origin ? origin : "?";
    api.foreign = foreign;
    api.Init = reinterpret_cast<decltype(api.Init)>(dlsym(handle, kSymInit));
    api.Hook = reinterpret_cast<decltype(api.Hook)>(dlsym(handle, kSymHook));
    api.UnHook = reinterpret_cast<decltype(api.UnHook)>(dlsym(handle, kSymUnHook));
    api.Deoptimize =
        reinterpret_cast<decltype(api.Deoptimize)>(dlsym(handle, kSymDeoptimize));
    api.DobbyHook =
        reinterpret_cast<decltype(api.DobbyHook)>(dlsym(handle, kSymDobbyHook));
    api.DobbyDestroy = reinterpret_cast<decltype(api.DobbyDestroy)>(
        dlsym(handle, kSymDobbyDestroy));
    if (!api.valid()) {
        return false;
    }
    out = api;
    return true;
}

bool IsOwnLib(std::string_view path) {
    // Our prebuilts ship under /system; anything already loaded from there
    // is ours (or platform) — never treat it as a foreign engine.
    if (path.substr(0, 8) == "/system/") return true;
    auto base = path.substr(path.find_last_of('/') + 1);
    return base == "liblsplant.so" || base == "libdobby.so" ||
           base == "liblspbridge.so" || base == "libc++_shared.so";
}

int CollectCb(dl_phdr_info* info, size_t, void* data) {
    if (info && info->dlpi_name && info->dlpi_name[0] != '\0') {
        static_cast<std::vector<std::string>*>(data)->emplace_back(info->dlpi_name);
    }
    return 0;
}

// Finds an already-loaded LSPlant engine that is NOT ours. Returns true and
// fills `out` (handle kept open — do not dlclose).
bool ProbeForeignEngine(EngineApi& out) {
    std::vector<std::string> paths;
    dl_iterate_phdr(CollectCb, &paths);
    for (const auto& path : paths) {
        if (IsOwnLib(path)) continue;
        void* h = dlopen(path.c_str(), RTLD_LAZY | RTLD_NOLOAD);
        if (!h) continue;
        if (BindHandle(h, path.c_str(), true, out)) {
            __android_log_print(ANDROID_LOG_INFO, "LsplantBridge",
                                "bound foreign LSPlant engine: %s", path.c_str());
            return true;
        }
        dlclose(h);
    }
    return false;
}

bool LoadOwnEngine(EngineApi& out) {
    // Same resolution Java's loadLibrary would do (/system/lib64).
    void* hPlant = dlopen("liblsplant.so", RTLD_LAZY);
    void* hDobby = dlopen("libdobby.so", RTLD_LAZY);
    if (!hPlant || !hDobby) {
        __android_log_print(ANDROID_LOG_ERROR, "LsplantBridge",
                            "own engine load failed: lsplant=%p dobby=%p", hPlant,
                            hDobby);
        return false;
    }
    // LSPlant entry points live in liblsplant, Dobby's in libdobby.
    EngineApi api;
    api.handle = hPlant;
    api.origin = "liblsplant.so";
    api.foreign = false;
    api.Init = reinterpret_cast<decltype(api.Init)>(dlsym(hPlant, kSymInit));
    api.Hook = reinterpret_cast<decltype(api.Hook)>(dlsym(hPlant, kSymHook));
    api.UnHook =
        reinterpret_cast<decltype(api.UnHook)>(dlsym(hPlant, kSymUnHook));
    api.Deoptimize = reinterpret_cast<decltype(api.Deoptimize)>(
        dlsym(hPlant, kSymDeoptimize));
    api.DobbyHook = reinterpret_cast<decltype(api.DobbyHook)>(
        dlsym(hDobby, kSymDobbyHook));
    api.DobbyDestroy = reinterpret_cast<decltype(api.DobbyDestroy)>(
        dlsym(hDobby, kSymDobbyDestroy));
    if (!api.valid()) {
        __android_log_print(ANDROID_LOG_ERROR, "LsplantBridge",
                            "own engine symbols incomplete");
        return false;
    }
    __android_log_print(ANDROID_LOG_INFO, "LsplantBridge", "bound own engine");
    out = api;
    return true;
}

constexpr const char* kLibArt = "libart.so";

void* InlineHooker(void* target, void* hooker) {
    void* origin = nullptr;
    if (g_engine.DobbyHook && g_engine.DobbyHook(target, hooker, &origin) == 0) {
        return origin;
    }
    return nullptr;
}

bool InlineUnhooker(void* func) {
    return g_engine.DobbyDestroy && g_engine.DobbyDestroy(func) == 0;
}

// ART 17 removed ClassLinker::FixupStaticTrampolines* (all 3 overloads).
// LSPlant 6.4 requires hooking them at Init and aborts without them, yet
// NOTHING on ART 17 calls them either. Stand in with a hookable no-op so
// Init proceeds: Dobby patches the stub (never executed), RegisterNative
// and the rest of Init run normally.
extern "C" void lsp_fixup_stub(void) {}

void* ArtResolver(std::string_view symbol) {
    {
        std::string name(symbol);
        __android_log_print(ANDROID_LOG_DEBUG, "LsplantBridge",
                            "ArtResolver request: %s", name.c_str());
    }
    // Exact lookup: fast path first (may be called with heap std::string).
    std::string name(symbol);
    if (name == "_ZN3art11ClassLinker22FixupStaticTrampolinesEPNS_6ThreadENS_6ObjPtrINS_6mirror5ClassEEE" ||
        name == "_ZN3art11ClassLinker22FixupStaticTrampolinesENS_6ObjPtrINS_6mirror5ClassEEE" ||
        name == "_ZN3art11ClassLinker22FixupStaticTrampolinesEPNS_6mirror5ClassE") {
        __android_log_print(ANDROID_LOG_DEBUG, "LsplantBridge",
                            "ArtResolver: stub stand-in for removed %s", name.c_str());
        return reinterpret_cast<void*>(lsp_fixup_stub);
    }
    void* addr = artelf::ResolveExact(kLibArt, name.c_str());
    if (addr != nullptr) {
        return addr;
    }
    return artelf::ResolvePrefix(kLibArt, symbol);
}

void* ArtPrefixResolver(std::string_view prefix) {
    return artelf::ResolvePrefix(kLibArt, prefix);
}

bool g_init_ok = false;

bool EnsureInit(JNIEnv* env) {
    if (g_init_ok) {
        return true;
    }
    // Foreign engine first: if Vector/LSPosed already injected its LSPlant
    // core into this process, bind to it and never load our own prebuilts —
    // two cores in one process is the native conflict.
    if (!g_engine.valid()) {
        if (!ProbeForeignEngine(g_engine) && !LoadOwnEngine(g_engine)) {
            __android_log_print(ANDROID_LOG_ERROR, "LsplantBridge",
                                "no usable LSPlant engine (foreign or own)");
            return false;
        }
    }
    lsplant::InitInfo info{
        .inline_hooker = InlineHooker,
        .inline_unhooker = InlineUnhooker,
        .art_symbol_resolver = ArtResolver,
        .art_symbol_prefix_resolver = ArtPrefixResolver,
    };
    g_init_ok = g_engine.Init(env, info);
    if (!g_init_ok) {
        __android_log_print(ANDROID_LOG_ERROR, "LsplantBridge",
                            "engine Init failed (bound to %s, foreign=%d)",
                            g_engine.origin.c_str(), g_engine.foreign);
    }
    return g_init_ok;
}

bool ForeignEnginePresent() {
    if (g_engine.valid()) return g_engine.foreign;
    EngineApi api;
    if (ProbeForeignEngine(api)) {
        // Keep the bound handle — EnsureInit will reuse it.
        g_engine = api;
        return true;
    }
    return false;
}

}  // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_org_pixel_customparts_manager_lsplant_LsplantBridge_nativeInit(JNIEnv* env, jclass) {
    return EnsureInit(env) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jobject JNICALL
Java_org_pixel_customparts_manager_lsplant_LsplantBridge_nativeDoHook(JNIEnv* env, jclass,
                                                                      jobject target, jobject hooker,
                                                                      jobject callback) {
    if (!EnsureInit(env)) {
        return nullptr;
    }
    return g_engine.Hook(env, target, hooker, callback);
}

JNIEXPORT jboolean JNICALL
Java_org_pixel_customparts_manager_lsplant_LsplantBridge_nativeUnHook(JNIEnv* env, jclass,
                                                                     jobject target) {
    if (!EnsureInit(env)) {
        return JNI_FALSE;
    }
    return g_engine.UnHook(env, target) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_org_pixel_customparts_manager_lsplant_LsplantBridge_nativeDeoptimize(JNIEnv* env, jclass,
                                                                         jobject method) {
    if (!EnsureInit(env)) {
        return;
    }
    g_engine.Deoptimize(env, method);
}

JNIEXPORT jboolean JNICALL
Java_org_pixel_customparts_manager_lsplant_LsplantBridge_nativeForeignEnginePresent(
    JNIEnv*, jclass) {
    return ForeignEnginePresent() ? JNI_TRUE : JNI_FALSE;
}

}  // extern "C"
