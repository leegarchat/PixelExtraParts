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

// ---- In-memory ELF symbol lookup --------------------------------------
// Zygisk loaders (zygisksu) may map the engine manually, outside the
// dynamic loader: present in /proc/self/maps but absent from
// dl_iterate_phdr, and dlopen(path) may fail. Resolve the entry points by
// parsing the mapped ELF. All reads go through /proc/self/mem with pread:
// a direct pointer dereference of a guard/unmapped page would SIGSEGV the
// process (observed SEGV_ACCERR in launcher), pread just returns an error.

#include <elf.h>
#include <fcntl.h>
#include <time.h>
#include <unistd.h>

int g_memFd = -1;

bool SafeRead(uintptr_t addr, void* buf, size_t len) {
    if (g_memFd < 0 || len == 0 || len > (1u << 20)) return false;
    size_t off = 0;
    while (off < len) {
        ssize_t r =
            pread(g_memFd, (char*)buf + off, len - off, (off_t)(addr + off));
        if (r <= 0) return false;
        off += (size_t)r;
    }
    return true;
}

struct MemElf {
    uintptr_t base = 0;
    uintptr_t strtab = 0;
    uintptr_t symtab = 0;
    size_t strsz = 0;
    uint32_t nbucket = 0;
    uintptr_t bucket = 0;   // file-offset-independent: runtime address
    uintptr_t chain = 0;
    uint32_t ngnbucket = 0, symoffset = 0;
    uintptr_t gnbucket = 0;
    uintptr_t gnchain = 0;
    bool gnuHash = false;
    bool sysvHash = false;
};

static uint32_t ElfHash(const char* name) {
    uint32_t h = 0, g;
    while (*name) {
        h = (h << 4) + (uint8_t)*name++;
        g = h & 0xf0000000;
        h ^= g;
        h ^= g >> 24;
    }
    return h;
}

static uint32_t GnuHash(const char* name) {
    uint32_t h = 5381;
    while (*name) h += (h << 5) + (uint8_t)*name++;
    return h;
}

bool MemElfOpen(uintptr_t base, MemElf& out) {
    Elf64_Ehdr eh;
    if (!SafeRead(base, &eh, sizeof(eh))) return false;
    if (eh.e_ident[0] != 0x7f || eh.e_ident[1] != 'E' || eh.e_ident[2] != 'L' ||
        eh.e_ident[3] != 'F' || eh.e_ident[4] != ELFCLASS64) {
        return false;
    }
    if (eh.e_phnum == 0 || eh.e_phnum > 32) return false;
    Elf64_Phdr ph[32];
    if (!SafeRead(base + eh.e_phoff, ph, eh.e_phnum * sizeof(ph[0])))
        return false;
    uintptr_t dynAddr = 0;
    for (int i = 0; i < eh.e_phnum; i++) {
        if (ph[i].p_type == PT_DYNAMIC) {
            dynAddr = base + ph[i].p_vaddr;
            break;
        }
    }
    if (!dynAddr) return false;
    MemElf e;
    e.base = base;
    for (int i = 0; i < 40; i++) {
        Elf64_Dyn d;
        if (!SafeRead(dynAddr + i * sizeof(d), &d, sizeof(d))) return false;
        if (d.d_tag == DT_NULL) break;
        switch (d.d_tag) {
            case DT_STRTAB: e.strtab = base + d.d_un.d_ptr; break;
            case DT_SYMTAB: e.symtab = base + d.d_un.d_ptr; break;
            case DT_STRSZ: e.strsz = d.d_un.d_val; break;
            case DT_HASH: {
                uint32_t h[2];
                if (!SafeRead(base + d.d_un.d_ptr, h, sizeof(h))) break;
                e.nbucket = h[0];
                if (e.nbucket == 0 || e.nbucket > 100000) break;
                e.bucket = base + d.d_un.d_ptr + 8;
                e.chain = e.bucket + (uintptr_t)e.nbucket * 4;
                e.sysvHash = true;
                break;
            }
            case DT_GNU_HASH: {
                uint32_t h[4];
                if (!SafeRead(base + d.d_un.d_ptr, h, sizeof(h))) break;
                e.ngnbucket = h[0];
                e.symoffset = h[1];
                uint32_t bloomSize = h[2];
                if (e.ngnbucket == 0 || e.ngnbucket > 100000 ||
                    bloomSize > 1024) {
                    break;
                }
                uintptr_t bloom =
                    base + d.d_un.d_ptr + 16;
                e.gnbucket = bloom + (uintptr_t)bloomSize * sizeof(uintptr_t);
                e.gnchain = e.gnbucket + (uintptr_t)e.ngnbucket * 4;
                e.gnuHash = true;
                break;
            }
        }
    }
    if (!e.strtab || !e.symtab || e.strsz == 0 || e.strsz > (1u << 24))
        return false;
    if (!e.gnuHash && !e.sysvHash) return false;
    out = e;
    return true;
}

bool MemElfReadSym(const MemElf& e, uint32_t idx, Elf64_Sym& out) {
    return SafeRead(e.symtab + (uintptr_t)idx * sizeof(out), &out, sizeof(out));
}

bool MemElfStreq(const MemElf& e, uint32_t st_name, const char* name) {
    if (st_name >= e.strsz) return false;
    // Read a bounded chunk; the NUL must be inside the string table.
    char buf[256];
    size_t want = e.strsz - st_name;
    if (want > sizeof(buf)) want = sizeof(buf);
    if (!SafeRead(e.strtab + st_name, buf, want)) return false;
    size_t i = 0;
    while (i < want && name[i] && buf[i] && name[i] == buf[i]) i++;
    if (i < want && name[i] == buf[i]) return true;  // both NUL
    // Name longer than the chunk: compare the tail directly.
    while (name[i]) {
        char c;
        if (!SafeRead(e.strtab + st_name + i, &c, 1) || c != name[i])
            return false;
        i++;
        if (i > 1024) return false;
    }
    char c;
    return SafeRead(e.strtab + st_name + i, &c, 1) && c == '\0';
}

void* MemElfLookup(const MemElf& e, const char* name) {
    if (e.gnuHash) {
        uint32_t h = GnuHash(name);
        uint32_t n = 0;
        if (!SafeRead(e.gnbucket + (uintptr_t)(h % e.ngnbucket) * 4, &n, 4) ||
            n == 0) {
            return nullptr;
        }
        for (int guard = 0; guard < 100000; guard++) {
            uint32_t c = 0, idx = n - e.symoffset;
            if (!SafeRead(e.gnchain + (uintptr_t)idx * 4, &c, 4)) return nullptr;
            Elf64_Sym s;
            if (MemElfReadSym(e, n, s) && ((c ^ h) >> 1) == 0 &&
                s.st_shndx != SHN_UNDEF && MemElfStreq(e, s.st_name, name)) {
                return reinterpret_cast<void*>(e.base + s.st_value);
            }
            n++;
            if (c & 1) break;
        }
        return nullptr;
    }
    if (e.sysvHash) {
        uint32_t n = 0;
        if (!SafeRead(e.bucket + (uintptr_t)(ElfHash(name) % e.nbucket) * 4, &n,
                      4)) {
            return nullptr;
        }
        for (int guard = 0; n != 0 && guard < 100000; guard++) {
            Elf64_Sym s;
            if (!MemElfReadSym(e, n, s)) return nullptr;
            if (s.st_shndx != SHN_UNDEF && MemElfStreq(e, s.st_name, name)) {
                return reinterpret_cast<void*>(e.base + s.st_value);
            }
            if (!SafeRead(e.chain + (uintptr_t)n * 4, &n, 4)) return nullptr;
        }
    }
    return nullptr;
}

// Scans /proc/self/maps (sees manually-mmapped libs too) and tries to bind
// the six engine symbols from each candidate mapping. Only /data/* and
// memfd mappings are candidates (platform /system+/apex can never host a
// foreign engine); the header page must be readable.
bool ProbeForeignEngineMaps(EngineApi& out) {
    FILE* f = fopen("/proc/self/maps", "r");
    if (!f) return false;
    g_memFd = open("/proc/self/mem", O_RDONLY);
    if (g_memFd < 0) {
        fclose(f);
        return false;
    }
    bool found = false;
    char line[1024];
    std::string lastPath;
    while (fgets(line, sizeof(line), f)) {
        uintptr_t start = 0;
        char perms[5] = "";
        char path[768] = "";
        if (sscanf(line, "%lx-%*x %4s %*x %*s %*d %767[^\n]", &start, perms,
                   path) < 2) {
            continue;
        }
        std::string p = path;
        while (!p.empty() && (p[0] == ' ' || p[0] == '\t')) p.erase(0, 1);
        if (p.empty() || p[0] == '[') {
            lastPath.clear();
            continue;
        }
        if (p == lastPath) continue;  // one base per library file
        lastPath = p;
        if (IsOwnLib(p)) continue;
        // Candidate origins only: zygisk/modules live under /data,
        // file-less engines under memfd.
        bool candidate = (p.compare(0, 6, "/data/") == 0) ||
                         (p.compare(0, 7, "/memfd:") == 0);
        if (!candidate) continue;
        // Header page must be mapped readable (guard pages fault on access).
        if (perms[0] != 'r') continue;
        MemElf elf;
        if (!MemElfOpen(start, elf)) continue;
        EngineApi api;
        api.handle = reinterpret_cast<void*>(start);
        api.origin = p + " (mem)";
        api.foreign = true;
        api.Init = reinterpret_cast<decltype(api.Init)>(
            MemElfLookup(elf, kSymInit));
        api.Hook = reinterpret_cast<decltype(api.Hook)>(
            MemElfLookup(elf, kSymHook));
        api.UnHook = reinterpret_cast<decltype(api.UnHook)>(
            MemElfLookup(elf, kSymUnHook));
        api.Deoptimize = reinterpret_cast<decltype(api.Deoptimize)>(
            MemElfLookup(elf, kSymDeoptimize));
        api.DobbyHook = reinterpret_cast<decltype(api.DobbyHook)>(
            MemElfLookup(elf, kSymDobbyHook));
        api.DobbyDestroy = reinterpret_cast<decltype(api.DobbyDestroy)>(
            MemElfLookup(elf, kSymDobbyDestroy));
        if (api.valid()) {
            __android_log_print(ANDROID_LOG_INFO, "LsplantBridge",
                                "bound foreign LSPlant engine: %s", p.c_str());
            fclose(f);
            close(g_memFd);
            g_memFd = -1;
            out = api;
            found = true;
            return true;
        }
    }
    fclose(f);
    close(g_memFd);
    g_memFd = -1;
    return found;
}

// Finds an already-loaded LSPlant engine that is NOT ours. Returns true and
// fills `out` (handle kept open — do not dlclose).
bool ProbeForeignEngine(EngineApi& out) {
    // Path 1: /proc/self/maps + in-memory ELF parse. Sees manually-mmapped
    // engines (zygisk) that the dynamic loader doesn't know about.
    if (ProbeForeignEngineMaps(out)) return true;
    // Path 2: loader-known objects via dlopen(NOLOAD) + dlsym. Only
    // /data/* and memfd mappings can host a foreign engine — dlopen takes
    // the loader lock, so never touch platform libraries here.
    std::vector<std::string> paths;
    dl_iterate_phdr(CollectCb, &paths);
    for (const auto& path : paths) {
        if (IsOwnLib(path)) continue;
        bool candidate = (path.compare(0, 6, "/data/") == 0) ||
                         (path.compare(0, 7, "/memfd:") == 0);
        if (!candidate) continue;
        void* h = dlopen(path.c_str(), RTLD_LAZY | RTLD_NOLOAD);
        if (!h) continue;
        if (BindHandle(h, path.c_str(), true, out)) {
            __android_log_print(ANDROID_LOG_INFO, "LsplantBridge",
                                "bound foreign LSPlant engine: %s", path.c_str());
            return true;
        }
        dlclose(h);
    }
    // Last resort: global scope (covers RTLD_GLOBAL loads whose path probe
    // missed, e.g. memfd-backed mappings).
    void* gInit = dlsym(RTLD_DEFAULT, kSymInit);
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
    struct timespec t0, t1;
    clock_gettime(CLOCK_MONOTONIC, &t0);
    EngineApi api;
    bool hit = ProbeForeignEngine(api);
    clock_gettime(CLOCK_MONOTONIC, &t1);
    long ms = (t1.tv_sec - t0.tv_sec) * 1000 + (t1.tv_nsec - t0.tv_nsec) / 1000000;
    __android_log_print(ANDROID_LOG_INFO, "LsplantBridge",
                        "probe done: foreign=%d in %ldms", hit, ms);
    if (hit) {
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
