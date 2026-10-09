// Minimal libart.so symbol resolver for the LSPlant backend.
//
// Covers LSPlant's ArtSymbolResolver (exact name) and ArtSymbolPrefixResolver
// (first match) across .dynsym (in-memory, always present) and .symtab
// (on-disk file fallback, when the in-memory image keeps section headers).
#pragma once

#include <cstddef>
#include <string_view>

namespace artelf {

// Exact symbol lookup via the already-loaded library. Never dlopens with load.
void* ResolveExact(const char* lib_name, const char* symbol);

// First symbol whose name starts with `prefix`, searching .dynsym of the
// loaded image first, then .symtab of the on-disk file. Returns nullptr.
void* ResolvePrefix(const char* lib_name, std::string_view prefix);

}  // namespace artelf
