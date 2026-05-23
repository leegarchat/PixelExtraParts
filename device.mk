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
    system/framework/PineInject.jar \
    system/lib64/libpine.so \
    system/lib64/libaapt2.so \
    system/framework/oat/arm64/PineInject.odex \
    system/framework/oat/arm64/PineInject.vdex

PRODUCT_PACKAGES += \
    PixelCustomPartsSystem \
    init.pixelextraparts.rc \
    ambient_extend_hook_addon \
    gcam_photo_torch_addon \
    icon_manager_settings_addon \
    ims_carrier_config_addon \
    launcher_hooks_addon \
    settings_homepage_item_addon \
    systemui_hooks_addon \
    PineInject \
    libpine

# PixelExtraParts thermal profile copy rules
ifeq ($(strip $(VENDOR_PATH)),)
$(error PixelExtraParts requires VENDOR_PATH to generate thermal config copy rules)
endif

PIXEL_EXTRA_PARTS_THERMAL_RESULT := $(shell python3 $(PIXEL_EXTRA_PARTS_PATH)/ThermalConfigs/generate_thermal_configs.py --quiet --vendor-path $(VENDOR_PATH) --device-codename $(DEVICE_CODENAME) --init-rc $(PIXEL_EXTRA_PARTS_PATH)/init.pixelextraparts.rc $(if $(strip $(THERMAL_CUSTOM_JSON_PATH)),--thermal-json $(THERMAL_CUSTOM_JSON_PATH),) 2>&1)

ifneq ($(findstring PixelExtraPartsThermalError:,$(PIXEL_EXTRA_PARTS_THERMAL_RESULT)),)
$(warning $(PIXEL_EXTRA_PARTS_THERMAL_RESULT))
else
$(call inherit-product, $(PIXEL_EXTRA_PARTS_PATH)/ThermalConfigs/ThermalConfigCopyRules.mk)
endif


# PixelExtraParts sepolicy
BOARD_VENDOR_SEPOLICY_DIRS += \
    $(PIXEL_EXTRA_PARTS_PATH)/sepolicy/vendor

SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += \
    $(PIXEL_EXTRA_PARTS_PATH)/sepolicy/system_ext/private
