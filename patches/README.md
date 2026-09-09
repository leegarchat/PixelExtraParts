# PixelExtraParts Patches

`apply_patches.py` auto-discovers patches from `files/` (populated from a `bakFiles` snapshot):

- `files/modified/<rel>` — modified tree files
- `files/original/<rel>` — clean HEAD copies
- `files/new/<rel>` — new (untracked) files to create
- `files/patches/<rel>.patch` — unified diffs for manual apply (`patch -p1 < files/patches/...`)
- `files/source_snapshot.json` — provenance (which snapshot was imported)

Common commands:

```bash
python3 packages/apps/PixelExtraParts/patches/apply_patches.py --list
python3 packages/apps/PixelExtraParts/patches/apply_patches.py --check
python3 packages/apps/PixelExtraParts/patches/apply_patches.py --apply
python3 packages/apps/PixelExtraParts/patches/apply_patches.py --apply --only frameworks/base/core/java/android/app/Activity.java
```

Refresh `files/` after manual edits + new `sync_tree.py -s` snapshot:

```bash
python3 packages/apps/PixelExtraParts/patches/sync_from_bakfiles.py --list
python3 packages/apps/PixelExtraParts/patches/sync_from_bakfiles.py --snapshot latest --prune
python3 packages/apps/PixelExtraParts/patches/sync_from_bakfiles.py --snapshot 20260905_085323 --regen-patches --prune
```

Apply order per file: structural `original -> modified` port, fallback to unified `files/patches/*.patch` via `patch -p0`, new files are copied from `files/new`.
If a snapshot no longer matches the target tree, the launcher stops with a manual porting hint instead of guessing.
