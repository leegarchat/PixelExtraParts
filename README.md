# PixelExtraParts (PEP)

**English** | [Русский](README_RU.md)

A system customization package for Android ROMs built from source: a privileged settings app (`org.pixel.customparts`), runtime hooks via Pine injection, thermal profiles, source snapshot patches, and an external addon SDK.

> How it works under the hood (architecture, hooks, settings, patcher, thermal, sepolicy) is a separate document: **[docs/PROJECT.md](docs/PROJECT.md)**. This file covers integration and maintenance only.

---

<a id="must-read"></a>
## ⚠️ Read first: mandatory steps

Skipping even one of these gets you "it built fine, but nothing works" or a build failure.

| # | Step | If you skip it |
|---|------|----------------|
| 1 | **Apply the source patches** ([how](#patches)) | Every option is there but nothing changes (especially launcher). The build still succeeds — which is exactly what misleads you |
| 2 | **Remove the stock thermal HAL from the vendor image** ([how](#thermal)) | HAL conflict → possible bootloop: the stock HAL can't read a custom config, and there is no fallback |
| 3 | **Check sepolicy against your base** ([how](#sepolicy)) | `neverallow ... violated by allow ...` midway through the build |
| 4 | **Set `VENDOR_PATH` and `DEVICE_CODENAME` before `inherit-product device.mk`** ([how](#connect)) | `PixelExtraParts requires VENDOR_PATH...` |
| 5 | **Run `sync_tree.py` only from the tree root** ([how](#sync)) | Broken snapshots, junk in `bakFiles` |

Quick self-check:

```bash
# from the Android source tree root:
python3 packages/apps/PixelExtraParts/patches/apply_patches.py --check   # patches in place?
grep -rn "android.hardware.thermal-service.pixel" device/<vendor>/<codename>/  # own HAL present, stock removed?
```

---

<a id="connect"></a>
## 1. Integration

```bash
# Clone STRICTLY into this path:
git clone <repo> packages/apps/PixelExtraParts
```

In your device/product makefile, **before** `inherit-product`:

```makefile
DEVICE_CODENAME := shiba
VENDOR_PATH := vendor/google/shiba
# optional, if thermal_info_config.json is in a non-standard location:
# THERMAL_CUSTOM_JSON_PATH := vendor/.../thermal_info_config.json

$(call inherit-product, packages/apps/PixelExtraParts/device.mk)
```

| Variable | Required | Purpose |
|---|---|---|
| `DEVICE_CODENAME` | Yes | Suffix for generated thermal config names |
| `VENDOR_PATH` | Yes | Locating `thermal_info_config.json` and copy rules; without it the build errors out |
| `THERMAL_CUSTOM_JSON_PATH` | No | Explicit path to `thermal_info_config.json` if autodetection fails |

What `device.mk` does on its own:

- adds to `PRODUCT_PACKAGES`: the `PixelCustomPartsSystem` app, `init.pixelextraparts.rc`, `PineInject`, `libpine`, and 8 addons;
- allowlists artifact paths (`system_ext/etc/pixelparts/addons/*.jar`, `system/framework/PineInject.jar`, `system/lib64/libpine.so`, etc.);
- runs the thermal config generator and includes `ThermalConfigCopyRules.mk`;
- includes `sepolicy/vendor` and `sepolicy/system_ext/{public,private}`.

---

<a id="patches"></a>
## 2. Source patches

The app is just a remote control: the hooks live in patched `frameworks/base`, `Settings`, `hardware/google/pixel/thermal`, `system/sepolicy`, `system/memory`. About 27 files + 1 new one (`DtwSystemService.java`). **All commands run from the tree root.**

```bash
python3 packages/apps/PixelExtraParts/patches/apply_patches.py --list    # what exists
python3 packages/apps/PixelExtraParts/patches/apply_patches.py --check   # what is already applied (dry-run)
python3 packages/apps/PixelExtraParts/patches/apply_patches.py --apply   # apply everything
python3 packages/apps/PixelExtraParts/patches/apply_patches.py --apply --only frameworks/base/core/java/android/app/Activity.java
```

Rules:

- New files from `patches/files/new/` are created automatically.
- If the tree drifted from the snapshot, the patcher **stops with a** `Manual action required: ...` **hint** instead of guessing. Port manually (`files/modified/` vs `files/original/`, or `patch -p1 < files/patches/...`) and re-run. If unsure — show both files to an AI.
- A single patch can be excluded: `--configure-bypass <patch-id> on|off` (IDs are shown by `--list`); apply despite the exclusion with `--apply-bypassed`.
- After your own edits + a fresh `sync_tree.py -s` snapshot, refresh the checked-in files:

```bash
python3 packages/apps/PixelExtraParts/patches/sync_from_bakfiles.py --snapshot latest --prune
```

Patcher engine internals and the full target list — [docs/PROJECT.md](docs/PROJECT.md#patch-system).

---

<a id="sync"></a>
## 3. Safe `repo sync` (sync_tree.py)

The script saves local changes into `bakFiles/snapshots/`, syncs the tree, and restores the changes. **Run only from the tree root** (paths resolve from CWD).

| Command | What it does |
|---|---|
| `sync_tree.py -s -j4` | Deep scan + snapshot + `repo sync` + patch restore |
| `sync_tree.py -s -d` | Snapshot only, no sync |
| `sync_tree.py -c` | View/apply patches from snapshots |
| `sync_tree.py -f` | Force sync: snapshot + revert, patches are NOT applied back |
| `sync_tree.py -r` | Revert to HEAD with a snapshot, no sync |
| `sync_tree.py -e` | Scanner exclusions |
| `-y` | Non-interactive mode |

Before the first `-s`, add your device tree, vendor tree, and private directories to exclusions (`-e`) — otherwise the scanner will pick up other people's changes. Example for Pixel 8 series: `device/google/shiba`, `vendor/google/shiba` (`out` and the project itself are excluded by default).

---

<a id="thermal"></a>
## 4. Thermal: two requirements

1. **Remove the stock thermal from the vendor image** in your device tree and build the HAL from source (`hardware/google/pixel/thermal`):

```makefile
PRODUCT_PACKAGES += android.hardware.thermal-service.pixel thermal_symlinks
PRODUCT_SOONG_NAMESPACES += hardware/google/pixel/thermal
```

Remove from the vendor image: `android.hardware.thermal-service.pixel{.rc,.xml,}`, `pixel-thermal-symlinks.rc`, `thermal-budget-interface-ndk`, `thermal_symlinks`.

> Why so strict: the stock HAL crashes with a path-conversion error on a custom config, and there is no fallback — the system may fail to boot after a reboot. If you can't fix the device tree right now, temporarily cut the thermal pieces out of `common/`, `device.mk`, and `init.pixelextraparts.rc`.

2. The generator (`ThermalConfigs/generate_thermal_configs.py`) is invoked from `device.mk` automatically: it finds `thermal_info_config.json` via `VENDOR_PATH` (or `THERMAL_CUSTOM_JSON_PATH`), emits `stock/soft/medium/hard/off` variants for SOC and battery into `configs/` + `ThermalConfigCopyRules.mk`. **Do not commit** generated files.

How thermal works internally (properties, `init.rc`, per-app profiles) — [docs/PROJECT.md](docs/PROJECT.md#thermal-internals).

---

<a id="sepolicy"></a>
## 5. Sepolicy

- `device.mk` includes `sepolicy/{vendor,system_ext/*}` on its own.
- The patches add exceptions to `system/sepolicy/private/{domain,coredomain}.te` (e.g. `-system_app` for `sysfs_batteryinfo` and `sysfs_leds`) plus `device/lineage/sepolicy`.

If the build fails with `neverallow ... violated by allow ...` — **first** run the PEP patch `--check`: the exception you need is most likely already in the patch set. Keep manual adjustments for your base in your own device tree, not in PEP. Full policy breakdown — [docs/PROJECT.md](docs/PROJECT.md#sepolicy-internals).

---

## 6. Build and update

```bash
lunch <target>
m PixelCustomPartsSystem PineInject libpine
```

- Manual update of the built APK on a device: `./pep_update.sh install` / `./pep_update.sh uninstall` (needs `adb root`, userdebug).
- OTA metadata for your own builds: `OTA/gen.sh <path_to_zip>`; release summary — `OTA/update_ota_json.py`.
- `Android.bp` targets, manifest, permissions — [docs/PROJECT.md](docs/PROJECT.md#build-internals).

---

## 7. FAQ

<details>
<summary><b>Built successfully, but options do nothing (icons, launcher...)</b></summary>

The patches are not applied. See [section 2](#patches): `--check`, then `--apply`. Critical for the launcher (`launcher_hooks` + `frameworks/base`).

</details>

<details>
<summary><b>`neverallow ... violated by allow ...` midway through the build</b></summary>

First `--check` the PEP patches — the exception may already be in the patch set. If your base differs, port it in your own device tree (see [section 5](#sepolicy)).

</details>

<details>
<summary><b>The patcher says `Manual action required`</b></summary>

The tree drifted from the snapshot. Apply the changes manually using the `files/original/` → `files/modified/` pair (or `files/patches/....patch`), then re-run `--apply`.

</details>

<details>
<summary><b>Boot problems after switching the thermal profile</b></summary>

Stock thermal HAL in the image. See [section 4](#thermal).

</details>

<details>
<summary><b>`repo sync` wiped my changes</b></summary>

Sync only via `sync_tree.py -s` from the tree root (see [section 3](#sync)). Revert without sync is `-r`.

</details>

---

## For developers

Short version; details — [docs/PROJECT.md](docs/PROJECT.md):

- New settings only via `SettingsKeys`/`SettingsCompat` (`Settings.Global`, `_pine` suffix; legacy `_xposed` is read-only). Don't hardcode keys.
- Visible text via `common/res/` resources (+ translations in `lang/`).
- Don't commit generated thermal configs or local files.
- When touching the patcher/thermal scripts, validate JSON/XML/Python.
- `command.txt` and `test.files/` are dev clutter, not for production.

## License

Project code plus several Android/Pine integration artifacts — check upstream files and imported prebuilts before redistributing binaries outside your ROM workflow.
