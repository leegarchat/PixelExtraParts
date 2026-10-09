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
    for (int i = 0; i < info->dlpi_phnum; ++i) {
        const auto& ph = info->dlpi_phdr[i];
        if (ph.p_type == PT_LOAD && out->bias == 0) {
            out->bias = info->dlpi_addr + ph.p_vaddr - ph.p_offset;
            // Keep the lowest vaddr bias: recompute properly below.
            out->bias = info->dlpi_addr;
            for (int j = 0; j < info->dlpi_phnum; ++j) {
                const auto& q = info->dlpi_phdr[j];
                if (q.p_type == PT_LOAD) {
                    uintptr_t b = info->dlpi_addr + q.p_vaddr - q.p_offset;
                    if (b < out->bias) {
                        out->bias = b;
                    }
                }
            }
        }
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

bool ParseDynamic(const ElfW(Dyn)* dyn, uintptr_t bias, DynSym* out) {
    for (const ElfW(Dyn)* d = dyn; d->d_tag != DT_NULL; ++d) {
        uintptr_t v = static_cast<uintptr_t>(d->d_un.d_ptr);
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
                out->gnu_bloom = reinterpret_cast<const ElfW(Addr)*>(v + 16);
                out->gnu_bucket = reinterpret_cast<const uint32_t*>(
                    reinterpret_cast<uintptr_t>(out->gnu_bloom) +
                    out->gnu_maskwords * sizeof(ElfW(Addr)));
                // maskwords is at h[2]; re-read properly:
                out->gnu_maskwords = h[2];
                out->gnu_shift2 = h[3];
                out->gnu_bucket = reinterpret_cast<const uint32_t*>(
                    reinterpret_cast<uintptr_t>(h) + 16 +
                    out->gnu_maskwords * sizeof(ElfW(Addr)));
                out->gnu_chain = out->gnu_bucket + out->gnu_nbucket;
                break;
            }
            default:
                break;
        }
    }
    // DT_* addresses in a shared object are already relocated (absolute).
    (void) bias;
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
        if (ds.syms[i].st_value == 0) {
            continue;
        }
        const char* name = SymName(ds, i);
        bool hit = exact ? (prefix == name) : (strncmp(name, prefix.data(), prefix.size()) == 0);
        if (hit) {
            return reinterpret_cast<void*>(static_cast<uintptr_t>(ds.syms[i].st_value));
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

void* ResolveExact(const char* lib_name, const char* symbol) {
    void* handle = dlopen(lib_name, RTLD_NOLOAD | RTLD_NOW);
    if (handle != nullptr) {
        void* sym = dlsym(handle, symbol);
        dlclose(handle);
        if (sym != nullptr) {
            return sym;
        }
    }
    return SearchLoaded(lib_name, symbol, true);
}

void* ResolvePrefix(const char* lib_name, std::string_view prefix) {
    void* hit = SearchLoaded(lib_name, prefix, false);
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
