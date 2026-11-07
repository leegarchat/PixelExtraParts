#
# PixelExtraParts
#

PIXEL_EXTRA_PARTS_PATH := packages/apps/PixelExtraParts

# PixelExtraParts runtime pieces (no APK)
PRODUCT_ARTIFACT_PATH_REQUIREMENT_ALLOWED_LIST += \
    system/framework/LsplantInject.jar \
    system/lib64/libc++_shared.so \
    system/lib64/liblsplant.so \
    system/lib64/libdobby.so \
    system/lib64/liblspbridge.so \
    system/framework/oat/arm64/LsplantInject.odex \
    system/framework/oat/arm64/LsplantInject.vdex

PRODUCT_PACKAGES += \
    init.pixelextraparts.rc \
    android.hardware.thermal-service.pixel.pep \
    LsplantInject \
    libc++_shared \
    liblsplant \
    libdobby \
    liblspbridge

# PixelExtraParts thermal profile copy rules
ifeq ($(strip $(VENDOR_PATH)),)
$(error PixelExtraParts requires VENDOR_PATH to generate thermal config copy rules)
endif

PIXEL_EXTRA_PARTS_THERMAL_RESULT := $(shell python3 $(PIXEL_EXTRA_PARTS_PATH)/config/thermal/generate_thermal_configs.py --quiet --vendor-path $(VENDOR_PATH) --device-codename $(DEVICE_CODENAME) --init-rc $(PIXEL_EXTRA_PARTS_PATH)/config/init.pixelextraparts.rc $(if $(strip $(THERMAL_CUSTOM_JSON_PATH)),--thermal-json $(THERMAL_CUSTOM_JSON_PATH),) 2>&1)

ifneq ($(findstring PixelExtraPartsThermalError:,$(PIXEL_EXTRA_PARTS_THERMAL_RESULT)),)
$(warning $(PIXEL_EXTRA_PARTS_THERMAL_RESULT))
else
$(call inherit-product, $(PIXEL_EXTRA_PARTS_PATH)/config/thermal/ThermalConfigCopyRules.mk)
endif


# Soong namespaces (plugin, filegroups, thermal fork).
PRODUCT_SOONG_NAMESPACES += \
    $(PIXEL_EXTRA_PARTS_PATH) \
    $(PIXEL_EXTRA_PARTS_PATH)/native/thermal_hal

# Soong plugin allowlist (build-time weave). Our bootstrap_go_package
# is not in Soong's hardcoded list; this is the documented escape hatch.
BUILD_BROKEN_PLUGIN_VALIDATION += soong-pixelparts-plugin

# PixelExtraParts build-time weave regen.
# Regenerates weave_gen/ (git-ignored) from live vanilla sources +
# weave/specs/*.patch BEFORE Soong parses file lists. Fails the build
# loudly on spec drift (never silent). Idempotent: writes only on change.
PEP_WEAVE_RES := $(shell python3 $(PIXEL_EXTRA_PARTS_PATH)/weave/weave_apply.py --top $(shell pwd) --manifest $(PIXEL_EXTRA_PARTS_PATH)/weave/manifest.json 2>&1)
ifneq ($(filter PEP_WEAVE_ERROR%,$(PEP_WEAVE_RES)),)
$(error PixelExtraParts weave regen failed: $(PEP_WEAVE_RES))
endif

# PixelExtraParts sepolicy
BOARD_VENDOR_SEPOLICY_DIRS += \
    $(PIXEL_EXTRA_PARTS_PATH)/policy/sepolicy/vendor

SYSTEM_EXT_PUBLIC_SEPOLICY_DIRS += \
    $(PIXEL_EXTRA_PARTS_PATH)/policy/sepolicy/system_ext/public

SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += \
    $(PIXEL_EXTRA_PARTS_PATH)/policy/sepolicy/system_ext/private

# Stock thermal HAL is removed here (plain include above runs this file
# immediately in global scope, after common.mk added stock). The pep fork
# uses its own output name (no shared stem: Soong emits edges for every
# in-scope module, so a shared path is fatal) + overrides:.
# labels: policy/sepolicy/vendor/file_contexts maps the pep binary to
# hal_thermal_default_exec (zuma labels only the stock path).
PRODUCT_PACKAGES := $(filter-out android.hardware.thermal-service.pixel,$(PRODUCT_PACKAGES))
