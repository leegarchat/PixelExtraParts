// JNI glue: LSPlant backend for the PixelExtraParts Xposed-compat shim.
//
// Java peer: org.pixel.customparts.manager.lsplant.LsplantBridge
//   private static native boolean nativeInit();
//   private static native Object nativeDoHook(Object target, Object hooker, Object callback);
//   private static native boolean nativeUnHook(Object target);
//   private static native void nativeDeoptimize(Object method);
//
// This library links LSPlant (static) + Dobby (static) and exposes a single
// shared object, liblsplant.so, replacing Pine's libpine.so. Init wires
// Dobby as the inline backend and art_elf as the libart symbol resolver.
#include <jni.h>

#include <string_view>

#include "art_elf.h"
#include "dobby.h"
#include <android/log.h>
#include "lsplant.hpp"

namespace {

constexpr const char* kLibArt = "libart.so";

void* InlineHooker(void* target, void* hooker) {
    void* origin = nullptr;
    if (DobbyHook(target, hooker, &origin) == 0) {
        return origin;
    }
    return nullptr;
}

bool InlineUnhooker(void* func) {
    return DobbyDestroy(func) == 0;
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
    lsplant::InitInfo info{
        .inline_hooker = InlineHooker,
        .inline_unhooker = InlineUnhooker,
        .art_symbol_resolver = ArtResolver,
        .art_symbol_prefix_resolver = ArtPrefixResolver,
    };
    g_init_ok = lsplant::Init(env, info);
    return g_init_ok;
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
    return lsplant::Hook(env, target, hooker, callback);
}

JNIEXPORT jboolean JNICALL
Java_org_pixel_customparts_manager_lsplant_LsplantBridge_nativeUnHook(JNIEnv* env, jclass,
                                                                     jobject target) {
    if (!EnsureInit(env)) {
        return JNI_FALSE;
    }
    return lsplant::UnHook(env, target) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_org_pixel_customparts_manager_lsplant_LsplantBridge_nativeDeoptimize(JNIEnv* env, jclass,
                                                                         jobject method) {
    if (!EnsureInit(env)) {
        return;
    }
    (void) lsplant::Deoptimize(env, method);
}

}  // extern "C"
