// See art_elf.h.
#include "art_elf.h"

#include <dlfcn.h>
#include <elf.h>
#include <fcntl.h>
#include <link.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>
#include <signal.h>
#include <time.h>
#include <setjmp.h>

#include <string>

namespace artelf {
namespace {

struct LibAddrs {
    uintptr_t bias = 0;          // load bias of the first PT_LOAD
    const ElfW(Dyn)* dynamic = nullptr;
    const char* path = nullptr;  // dlpi_name (may be empty)
};

int DlCallback(struct dl_phdr_info* info, size_t, void* data) {
    auto* out = static_cast<LibAddrs*>(data);
    if (info->dlpi_name == nullptr) {
        return 0;
    }
    std::string name(info->dlpi_name);
    const char* want = static_cast<const char*>(out->path);  // reused as wanted substring
    if (name.find(want) == std::string::npos) {
        return 0;
    }
    // dlpi_addr is the load bias (l_addr) of ET_DYN objects like libart.so.
    out->bias = static_cast<uintptr_t>(info->dlpi_addr);
    for (int i = 0; i < info->dlpi_phnum; ++i) {
        const auto& ph = info->dlpi_phdr[i];
        if (ph.p_type == PT_DYNAMIC) {
            out->dynamic = reinterpret_cast<const ElfW(Dyn)*>(info->dlpi_addr + ph.p_vaddr);
        }
    }
    out->path = info->dlpi_name;  // remember full path for file fallback
    return 1;  // stop
}

bool FindLib(const char* substr, LibAddrs* out) {
    out->bias = 0;
    out->dynamic = nullptr;
    out->path = substr;
    dl_iterate_phdr(DlCallback, out);
    return out->dynamic != nullptr;
}

struct DynSym {
    const ElfW(Sym)* syms = nullptr;
    const char* strs = nullptr;
    size_t strsz = 0;
    // Either SYSV hash or GNU hash (one of them is used).
    const uint32_t* bucket = nullptr;
    const uint32_t* chain = nullptr;
    uint32_t nbucket = 0;
    const uint32_t* gnu_bucket = nullptr;
    const uint32_t* gnu_chain = nullptr;
    uint32_t gnu_maskwords = 0;
    uint32_t gnu_shift2 = 0;
    const ElfW(Addr)* gnu_bloom = nullptr;
    uint32_t gnu_nbucket = 0;
    uint32_t gnu_symoffset = 0;
};

// Relocate a DT_* address tag to a runtime address.
//
// Empirically linkers disagree here: glibc presents DT_SYMTAB/DT_STRTAB/
// DT_HASH/DT_GNU_HASH already bias-inclusive in memory, while Bionic keeps
// the raw link-time values. Disambiguate by shape: real RVAs are tiny,
// relocated addresses are not.
static uintptr_t RelocateAddr(uintptr_t v, uintptr_t bias) {
    if (v != 0 && v < 0x100000) {
        v += bias;
    }
    return v;
}

bool ParseDynamic(const ElfW(Dyn)* dyn, uintptr_t bias, DynSym* out) {
    // DT_* d_ptr values of ET_DYN objects may be link-time RVAs (Bionic)
    // or already relocated (glibc): RelocateAddr handles both.
    for (const ElfW(Dyn)* d = dyn; d->d_tag != DT_NULL; ++d) {
        uintptr_t v = RelocateAddr(static_cast<uintptr_t>(d->d_un.d_ptr), bias);
        switch (d->d_tag) {
            case DT_SYMTAB:
                out->syms = reinterpret_cast<const ElfW(Sym)*>(v);
                break;
            case DT_STRTAB:
                out->strs = reinterpret_cast<const char*>(v);
                break;
            case DT_STRSZ:
                out->strsz = static_cast<size_t>(d->d_un.d_val);
                break;
            case DT_HASH:
                out->bucket = reinterpret_cast<const uint32_t*>(v);
                out->nbucket = out->bucket[0];
                out->chain = out->bucket + 2 + out->nbucket;
                break;
            case DT_GNU_HASH: {
                const uint32_t* h = reinterpret_cast<const uint32_t*>(v);
                out->gnu_nbucket = h[0];
                out->gnu_symoffset = h[1];
                out->gnu_maskwords = h[2];
                out->gnu_shift2 = h[3];
                out->gnu_bloom = reinterpret_cast<const ElfW(Addr)*>(
                    reinterpret_cast<uintptr_t>(h) + 16 +
                    out->gnu_maskwords * sizeof(ElfW(Addr)));
                out->gnu_bucket = reinterpret_cast<const uint32_t*>(
                    reinterpret_cast<uintptr_t>(h) + 16 +
                    out->gnu_maskwords * sizeof(ElfW(Addr)));
                // NOTE: bloom sits between header and buckets; bucket base
                // skips header(16) + bloom(maskwords words).
                out->gnu_bucket = reinterpret_cast<const uint32_t*>(
                    reinterpret_cast<uintptr_t>(out->gnu_bloom) +
                    out->gnu_maskwords * sizeof(ElfW(Addr)));
                out->gnu_chain = out->gnu_bucket + out->gnu_nbucket;
                break;
            }
            default:
                break;
        }
    }
    return out->syms != nullptr && out->strs != nullptr;
}

// Number of symbols when only GNU hash is available: walk chain maxes.
size_t GnuSymCount(const DynSym& ds) {
    uint32_t max = ds.gnu_symoffset;
    for (uint32_t i = 0; i < ds.gnu_nbucket; ++i) {
        uint32_t n = ds.gnu_bucket[i];
        if (n == 0) {
            continue;
        }
        if (n < ds.gnu_symoffset) {
            continue;
        }
        // Walk chain to its end (bit0 set terminates).
        uint32_t s = n;
        while (true) {
            if (s + 1 > max) {
                max = s + 1;
            }
            if ((ds.gnu_chain[s - ds.gnu_symoffset] & 1) != 0) {
                break;
            }
            ++s;
            if (s > ds.gnu_symoffset + 1000000) {
                break;  // corrupt table guard
            }
        }
    }
    return max;
}

size_t SysvSymCount(const DynSym& ds) {
    uint32_t max = 0;
    for (uint32_t i = 0; i < ds.nbucket; ++i) {
        for (uint32_t s = ds.bucket[2 + i]; s != 0; s = ds.chain[s]) {
            if (s + 1 > max) {
                max = s + 1;
            }
        }
    }
    return max;
}

const char* SymName(const DynSym& ds, size_t i) {
    uint32_t off = ds.syms[i].st_name;
    if (off >= ds.strsz) {
        return "";
    }
    return ds.strs + off;
}

// Search .dynsym of the loaded image.
void* SearchLoaded(const char* substr, std::string_view prefix, bool exact) {
    LibAddrs lib;
    if (!FindLib(substr, &lib)) {
        return nullptr;
    }
    DynSym ds;
    if (!ParseDynamic(lib.dynamic, lib.bias, &ds)) {
        return nullptr;
    }
    size_t count = ds.nbucket != 0 ? SysvSymCount(ds) : GnuSymCount(ds);
    std::string tmp_path(lib.path ? lib.path : "");
    for (size_t i = 1; i < count; ++i) {
        if (ds.syms[i].st_shndx == SHN_UNDEF) {
            continue;
        }
        if (ELF64_ST_BIND(ds.syms[i].st_info) == STB_LOCAL) {
            continue;  // mirror dlsym: never resolve local symbols
        }
        if (ds.syms[i].st_value == 0) {
            continue;
        }
        const char* name = SymName(ds, i);
        bool hit = exact ? (prefix == name) : (strncmp(name, prefix.data(), prefix.size()) == 0);
        if (hit) {
            // st_value is always a link-time RVA (symbol table data is never
            // relocated): the load bias applies on every libc.
            return reinterpret_cast<void*>(lib.bias + static_cast<uintptr_t>(ds.syms[i].st_value));
        }
    }
    return nullptr;
}

// Search .symtab of the on-disk file (unstripped debug tables, when kept).
void* SearchFileSymtab(const char* path, std::string_view prefix) {
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        return nullptr;
    }
    struct stat st;
    void* map = MAP_FAILED;
    void* found = nullptr;
    if (fstat(fd, &st) == 0 && st.st_size > static_cast<off_t>(sizeof(ElfW(Ehdr)))) {
        map = mmap(nullptr, static_cast<size_t>(st.st_size), PROT_READ, MAP_PRIVATE, fd, 0);
    }
    close(fd);
    if (map == MAP_FAILED) {
        return nullptr;
    }
    auto* eh = static_cast<const ElfW(Ehdr)*>(map);
    if (memcmp(eh->e_ident, ELFMAG, SELFMAG) != 0) {
        munmap(map, static_cast<size_t>(st.st_size));
        return nullptr;
    }
    auto* sh = reinterpret_cast<const ElfW(Shdr)*>(static_cast<const char*>(map) + eh->e_shoff);
    const ElfW(Sym)* syms = nullptr;
    size_t nsyms = 0;
    const char* strs = nullptr;
    for (int i = 0; i < eh->e_shnum; ++i) {
        if (sh[i].sh_type == SHT_SYMTAB) {
            syms = reinterpret_cast<const ElfW(Sym)*>(static_cast<const char*>(map) + sh[i].sh_offset);
            nsyms = sh[i].sh_size / sizeof(ElfW(Sym));
            const ElfW(Shdr)& strtab = sh[sh[i].sh_link];
            strs = static_cast<const char*>(map) + strtab.sh_offset;
            break;
        }
    }
    if (syms != nullptr && strs != nullptr) {
        // Resolve the library base to relocate STT values (they are absolute only if prelinked).
        LibAddrs lib;
        uintptr_t bias = 0;
        if (FindLib(path, &lib)) {
            // st_value in ET_DYN .symtab is relative to load bias.
            bias = lib.bias;
        }
        for (size_t i = 1; i < nsyms; ++i) {
            if (syms[i].st_shndx == SHN_UNDEF || syms[i].st_value == 0) {
                continue;
            }
            const char* name = strs + syms[i].st_name;
            if (strncmp(name, prefix.data(), prefix.size()) == 0) {
                found = reinterpret_cast<void*>(bias + syms[i].st_value);
                break;
            }
        }
    }
    munmap(map, static_cast<size_t>(st.st_size));
    return found;
}

}  // namespace

// ---- File-based resolver (primary): parse the on-disk ELF ----------
// Deterministic: table locations from file offsets, symbol RVAs rebased by
// the runtime load bias from dl_iterate_phdr. Bounded by the file size;
// no hash-table walks at all (linear scan over .dynsym).

namespace {

struct FileImage {
    const char* base = nullptr;
    size_t size = 0;
    int fd = -1;
};

bool MapFile(const char* path, FileImage* out) {
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        return false;
    }
    struct stat st;
    if (fstat(fd, &st) != 0 || st.st_size < static_cast<off_t>(sizeof(ElfW(Ehdr)))) {
        close(fd);
        return false;
    }
    void* map = mmap(nullptr, static_cast<size_t>(st.st_size), PROT_READ, MAP_PRIVATE, fd, 0);
    if (map == MAP_FAILED) {
        close(fd);
        return false;
    }
    out->base = static_cast<const char*>(map);
    out->size = static_cast<size_t>(st.st_size);
    out->fd = fd;
    return true;
}

void UnmapFile(FileImage* img) {
    if (img->base != nullptr) {
        munmap(const_cast<char*>(img->base), img->size);
        img->base = nullptr;
    }
    if (img->fd >= 0) {
        close(img->fd);
        img->fd = -1;
    }
}

template <typename T>
const T* At(const FileImage& img, size_t off) {
    if (off + sizeof(T) > img.size) {
        return nullptr;
    }
    return reinterpret_cast<const T*>(img.base + off);
}

// Runtime load bias of an already-loaded library (0 when absent).
uintptr_t LoadedBias(const char* substr) {
    LibAddrs lib;
    if (!FindLib(substr, &lib)) {
        return 0;
    }
    return lib.bias;
}

// Linear scan of .dynsym from the FILE. Returns RVA (not rebased).
bool ScanFileDynsym(const char* path, std::string_view prefix, bool exact,
                    uint32_t* rva_out) {
    FileImage img;
    if (!MapFile(path, &img)) {
        return false;
    }
    bool ok = false;
    const ElfW(Ehdr)* eh = At<ElfW(Ehdr)>(img, 0);
    if (eh != nullptr && memcmp(eh->e_ident, ELFMAG, SELFMAG) == 0 && eh->e_shoff != 0 &&
        eh->e_shentsize == sizeof(ElfW(Shdr))) {
        for (int pass = 0; pass < 2; ++pass) {
            const unsigned want = (pass == 0) ? SHT_DYNSYM : SHT_SYMTAB;
            for (int i = 0; i < eh->e_shnum; ++i) {
            const ElfW(Shdr)* sh = At<ElfW(Shdr)>(img, eh->e_shoff + i * sizeof(ElfW(Shdr)));
            if (sh == nullptr) {
                break;
            }
            if (sh->sh_type != want) {
                continue;
            }
            const ElfW(Shdr)* strsh = At<ElfW(Shdr)>(img, eh->e_shoff +
                                                          sh->sh_link * sizeof(ElfW(Shdr)));
            if (strsh == nullptr || strsh->sh_type != SHT_STRTAB) {
                break;
            }
            size_t count = sh->sh_size / sizeof(ElfW(Sym));
            for (size_t s = 1; s < count; ++s) {
                const ElfW(Sym)* sym = At<ElfW(Sym)>(img, sh->sh_offset + s * sizeof(ElfW(Sym)));
                if (sym == nullptr) {
                    break;
                }
                if (sym->st_shndx == SHN_UNDEF || sym->st_value == 0) {
                    continue;
                }
                // NOTE: no STB_LOCAL skip — .symtab is mostly LOCAL and
                // LSPlant needs HIDDEN ART internals dlsym can never see.
                size_t strtab_end = strsh->sh_offset + strsh->sh_size;
                if (strsh->sh_offset + sym->st_name >= strtab_end) {
                    continue;
                }
                const char* name = img.base + strsh->sh_offset + sym->st_name;
                // Bounded compare: never read past the strtab end.
                size_t maxlen = strtab_end - (strsh->sh_offset + sym->st_name);
                size_t namelen = strnlen(name, maxlen);
                bool hit = exact ? (namelen == prefix.size() &&
                                    memcmp(name, prefix.data(), namelen) == 0)
                                 : (namelen >= prefix.size() &&
                                    memcmp(name, prefix.data(), prefix.size()) == 0);
                if (hit) {
                    *rva_out = static_cast<uint32_t>(sym->st_value);
                    ok = true;
                    break;
                }
            }
            if (ok) {
                break;
            }
            }  // sections
        }  // passes
    }
    UnmapFile(&img);
    return ok;
}

}  // namespace

namespace {

// ---- Fault-guarded memory walk -------------------------------------
// The in-memory table walk is a last resort: table layouts differ across
// libc/libart builds, and a misparse must degrade to "not found", never
// to a dead process (boot-time crash loops otherwise).
thread_local sigjmp_buf g_walk_jmp;
thread_local volatile bool g_walk_armed = false;

void WalkSegvHandler(int) {
    if (g_walk_armed) {
        siglongjmp(g_walk_jmp, 1);
    }
}

struct WalkGuard {
    struct sigaction old_segv;
    struct sigaction old_bus;
    bool installed = false;

    WalkGuard() {
        struct sigaction sa;
        memset(&sa, 0, sizeof(sa));
        sa.sa_handler = WalkSegvHandler;
        sigemptyset(&sa.sa_mask);
        sa.sa_flags = 0;
        if (sigaction(SIGSEGV, &sa, &old_segv) == 0 &&
            sigaction(SIGBUS, &sa, &old_bus) == 0) {
            installed = true;
        }
    }
    ~WalkGuard() {
        if (installed) {
            sigaction(SIGSEGV, &old_segv, nullptr);
            sigaction(SIGBUS, &old_bus, nullptr);
        }
        g_walk_armed = false;
    }
};

void* GuardedSearchLoaded(const char* lib_name, std::string_view prefix, bool exact) {
    WalkGuard guard;
    if (!guard.installed) {
        return nullptr;
    }
    g_walk_armed = true;
    void* hit = nullptr;
    if (sigsetjmp(g_walk_jmp, 1) == 0) {
        hit = SearchLoaded(lib_name, prefix, exact);
    }
    g_walk_armed = false;
    return hit;
}

// File scan with retries: right after boot, apex files may not be visible
// in the process mount namespace yet (ENOENT for a short window).
bool ScanFileWithRetry(const char* path, std::string_view prefix, bool exact,
                       uint32_t* rva_out) {
    for (int i = 0; i < 5; ++i) {
        if (ScanFileDynsym(path, prefix, exact, rva_out)) {
            return true;
        }
        struct timespec ts = {0, 100 * 1000 * 1000};
        nanosleep(&ts, nullptr);
    }
    return false;
}

}  // namespace

void* ResolveExact(const char* lib_name, const char* symbol) {
    // Fast path: already-resolved exported symbols.
    void* handle = dlopen(lib_name, RTLD_NOLOAD | RTLD_NOW);
    if (handle != nullptr) {
        void* sym = dlsym(handle, symbol);
        dlclose(handle);
        if (sym != nullptr) {
            return sym;
        }
    }
    // Deterministic path: file .dynsym/.symtab scan + load bias,
    // with retries for the early-boot apex-visibility window.
    {
        LibAddrs lib;
        if (FindLib(lib_name, &lib) && lib.path != nullptr && lib.bias != 0) {
            uint32_t rva = 0;
            if (ScanFileWithRetry(lib.path, symbol, true, &rva) && rva != 0) {
                return reinterpret_cast<void*>(lib.bias + rva);
            }
        }
    }
    return GuardedSearchLoaded(lib_name, symbol, true);
}

void* ResolvePrefix(const char* lib_name, std::string_view prefix) {
    // Deterministic path first: file scan + load bias, with retries.
    {
        LibAddrs lib;
        if (FindLib(lib_name, &lib) && lib.path != nullptr && lib.bias != 0) {
            uint32_t rva = 0;
            if (ScanFileWithRetry(lib.path, prefix, false, &rva) && rva != 0) {
                return reinterpret_cast<void*>(lib.bias + rva);
            }
        }
    }
    void* hit = GuardedSearchLoaded(lib_name, prefix, false);
    if (hit != nullptr) {
        return hit;
    }
    // On-disk fallback for .symtab.
    LibAddrs lib;
    if (FindLib(lib_name, &lib) && lib.path != nullptr) {
        hit = SearchFileSymtab(lib.path, prefix);
    }
    return hit;
}

}  // namespace artelf
