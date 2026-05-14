# PixelExtraParts Patches

`apply_patches.py` reads patch groups from `subpatches/` and ports the matching `changebe/` snapshots into the source tree.
Thermal HAL patches use `changebe/hardware/google/pixel/thermal/` snapshots and patch the stock `hardware/google/pixel/thermal/` files in place.

Common commands:

```bash
python3 packages/apps/PixelExtraParts/patches/apply_patches.py --list
python3 packages/apps/PixelExtraParts/patches/apply_patches.py --check
python3 packages/apps/PixelExtraParts/patches/apply_patches.py --apply
```

If a framework snapshot no longer matches the target tree, the launcher stops with a manual porting hint instead of guessing.
