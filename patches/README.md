# PixelExtraParts Patches

`apply_patches.py` reads patch groups from `subpatches/` and ports the matching `changebe/` snapshots into the source tree.

Common commands:

```bash
python3 packages/apps/PixelExtraParts/patches/apply_patches.py --list
python3 packages/apps/PixelExtraParts/patches/apply_patches.py --check
python3 packages/apps/PixelExtraParts/patches/apply_patches.py --apply
```

Settings resources are bypassed by default because the saved XML uses a ROM-specific Settings category. To let the patcher manage them, either run once with `--apply-bypassed` or change the configurator state:

```bash
python3 packages/apps/PixelExtraParts/patches/apply_patches.py --configure-bypass settings-res off
python3 packages/apps/PixelExtraParts/patches/apply_patches.py --apply
```

If a framework snapshot no longer matches the target tree, the launcher stops with a manual porting hint instead of guessing.
