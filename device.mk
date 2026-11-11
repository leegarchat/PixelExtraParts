#
# PixelExtraParts
#

PIXEL_EXTRA_PARTS_PATH := packages/apps/PixelExtraParts

# PixelExtraParts app and runtime pieces
PRODUCT_ARTIFACT_PATH_REQUIREMENT_ALLOWED_LIST += \
    system_ext/etc/pixelparts/addons/ambient_extend_hook.jar \
    system_ext/etc/pixelparts/addons/gcam_photo_torch.jar \
    system_ext/etc/pixelparts/addons/icon_manager_settings.jar \
    system_ext/etc/pixelparts/addons/ims_carrier_config.jar \
    system_ext/etc/pixelparts/addons/launcher_hooks.jar \
    system_ext/etc/pixelparts/addons/settings_homepage_item.jar \
    system_ext/etc/pixelparts/addons/systemui_hooks.jar \
    system_ext/etc/pixelparts/addons/ram_purge.jar \
    system_ext/etc/pixelparts/addons/yandexmaps_fast.jar \
    system_ext/etc/pixelparts/addons/flashnotify.jar \
    system_ext/etc/pixelparts/addons/settings_icon_style_override.jar \
    system/framework/LsplantInject.jar \
    system/lib64/libc++_shared.so \
    system/lib64/liblsplant.so \
    system/lib64/libdobby.so \
    system/lib64/liblspbridge.so \
    system/lib64/libaapt2.so \
    system/framework/oat/arm64/LsplantInject.odex \
    system/framework/oat/arm64/LsplantInject.vdex

PRODUCT_PACKAGES += \
    PixelCustomPartsSystem \
    init.pixelextraparts.rc \
    android.hardware.thermal-service.pixel.pep \
    thermal_symlinks_pep \
    ambient_extend_hook_addon \
    gcam_photo_torch_addon \
    icon_manager_settings_addon \
    settings_icon_style_override_addon \
    ims_carrier_config_addon \
    launcher_hooks_addon \
    settings_homepage_item_addon \
    systemui_hooks_addon \
    ram_purge_addon \
    yandexmaps_fast_addon \
    flashnotify_addon \
    LsplantInject \
    libc++_shared \
    liblsplant \
    libdobby \
    liblspbridge

# PixelExtraParts thermal profile copy rules
ifeq ($(strip $(VENDOR_PATH)),)
$(error PixelExtraParts requires VENDOR_PATH to generate thermal config copy rules)
endif

PIXEL_EXTRA_PARTS_THERMAL_RESULT := $(shell python3 $(PIXEL_EXTRA_PARTS_PATH)/thermal_hal/config/generate_thermal_configs.py --quiet --vendor-path $(VENDOR_PATH) --device-codename $(DEVICE_CODENAME) $(if $(strip $(THERMAL_CUSTOM_JSON_PATH)),--thermal-json $(THERMAL_CUSTOM_JSON_PATH),) 2>&1)

ifneq ($(findstring PixelExtraPartsThermalError:,$(PIXEL_EXTRA_PARTS_THERMAL_RESULT)),)
$(warning $(PIXEL_EXTRA_PARTS_THERMAL_RESULT))
else
$(call inherit-product, $(PIXEL_EXTRA_PARTS_PATH)/thermal_hal/config/ThermalConfigCopyRules.mk)
endif


# Soong namespaces (plugin, filegroups, thermal fork).
PRODUCT_SOONG_NAMESPACES += \
    $(PIXEL_EXTRA_PARTS_PATH) \
    $(PIXEL_EXTRA_PARTS_PATH)/thermal_hal

# Soong plugin allowlist (build-time weave). Our bootstrap_go_package
# is not in Soong's hardcoded list; this is the documented escape hatch.
BUILD_BROKEN_PLUGIN_VALIDATION += soong-pixelparts-plugin

# PixelExtraParts build-time weave regen.
# Regenerates soong/weave_gen/ (git-ignored) from live vanilla sources +
# soong/weave/specs/*.patch BEFORE Soong parses file lists. Fails the build
# loudly on spec drift (never silent). Idempotent: writes only on change.
#
# Multi-ROM: vendor trees drift around the same PEP insertions, so divergent
# files carry per-ROM variants under soong/weave/specs/<rom>/<rel>.patch
# (see manifest "fallbacks"). The target ROM is detected from vendor make
# variables (PEP_TARGET_ROM=<rom> wins as a manual override); weave_apply.py
# then tries the whole chain for --rom strictly (no fuzz): the ROM-specific
# variant first, the shared aosp variant next, the default (evo) spec last.
# Unknown ROMs walk [<rom>, aosp, default] and fail loudly if nothing matches.
ifdef PEP_TARGET_ROM
PEP_ROM := $(strip $(PEP_TARGET_ROM))
else ifdef EVO_BUILD_TYPE
PEP_ROM := evo
else ifdef CR_VERSION
PEP_ROM := crdroid
else ifdef RISING_VERSION
PEP_ROM := rising
else ifdef LINEAGE_VERSION
PEP_ROM := lineage
else
PEP_ROM := aosp
endif
# Version gate: weave specs are anchored on Android 17 (SDK 37). Non-A17
# trees (AOSPA, Rising16, ...) are not covered — fail loudly here when the
# platform version is already known (weave_apply.py re-checks the live tree
# itself, so an empty PLATFORM_SDK_VERSION at this point is not fatal).
PEP_EXPECTED_SDK := 37
ifneq ($(strip $(PLATFORM_SDK_VERSION)),)
ifneq ($(strip $(PLATFORM_SDK_VERSION)),$(PEP_EXPECTED_SDK))
$(error PixelExtraParts weave supports Android 17 (SDK $(PEP_EXPECTED_SDK)) only: PLATFORM_SDK_VERSION=$(PLATFORM_SDK_VERSION) rom=$(PEP_ROM). AOSPA/Rising16 trees are not covered.)
endif
endif
PEP_WEAVE_RES := $(shell python3 $(PIXEL_EXTRA_PARTS_PATH)/soong/weave/weave_apply.py --top $(shell pwd) --manifest $(PIXEL_EXTRA_PARTS_PATH)/soong/weave/manifest.json --rom $(PEP_ROM) --sdk '$(PLATFORM_SDK_VERSION)' 2>&1)
ifneq ($(filter PEP_WEAVE_ERROR%,$(PEP_WEAVE_RES)),)
$(error PixelExtraParts weave regen failed: fix the spec drift below, then rebuild. $(PEP_WEAVE_RES))
endif

# PixelExtraParts sepolicy
BOARD_VENDOR_SEPOLICY_DIRS += \
    $(PIXEL_EXTRA_PARTS_PATH)/policy/sepolicy/vendor

SYSTEM_EXT_PUBLIC_SEPOLICY_DIRS += \
    $(PIXEL_EXTRA_PARTS_PATH)/policy/sepolicy/system_ext/public

SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += \
    $(PIXEL_EXTRA_PARTS_PATH)/policy/sepolicy/system_ext/private

# Stock thermal removal: Soong overrides (thermal_hal/Android.bp).
#
# Make-side filter-outs CANNOT work here, harness-proven: inherit-product
# only appends @inherit: markers (merged later in import-nodes, each node in
# an emptied scope), so no position in this file ever observes vendor-added
# entries -- proven by $(warning) probes (this file sees only PEP entries +
# markers). Enforcement is the pep cc_binary's
# overrides: [stock binary, stock VINTF xml, thermal-budget-interface-ndk]
# (its only consumer was the stock binary) + pep sh_binary's
# overrides: [thermal_symlinks], applied order-independently at install-set
# assembly. All four were direct PRODUCT_PACKAGES entries, so transitive
# removal was blocked -- explicit overrides were required.
# The two stock init rc copies (PRODUCT_COPY_FILES, unreachable by overrides)
# survive: benign -- init keeps the first `service vendor.thermal-hal`
# definition and the pep rc sorts before the stock one; the orphaned stock
# symlinks rc logs one missing-binary error at boot while the pep flow runs
# on its own service. If that line annoys: prepend empty shims for those two
# dest paths at the very TOP of lineage_shiba.mk (before all inherits,
# first-wins).
