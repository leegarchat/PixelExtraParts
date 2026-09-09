#
# This policy configuration will be used by all products that
# inherit from Lineage
#

ifeq ($(TARGET_COPY_OUT_VENDOR), vendor)
ifeq ($(BOARD_VENDORIMAGE_FILE_SYSTEM_TYPE),)
TARGET_USES_PREBUILT_VENDOR_SEPOLICY ?= true
endif
endif

SYSTEM_EXT_PUBLIC_SEPOLICY_DIRS += \
    device/lineage/sepolicy/common/public

SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += \
    device/lineage/sepolicy/common/private

ifeq ($(filter tokay caiman komodo comet tegu frankel blazer mustang rango, $(LINEAGE_BUILD)),)
SYSTEM_EXT_PUBLIC_SEPOLICY_DIRS += \
    device/lineage/sepolicy/mosey/system_ext/public

SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += \
    device/lineage/sepolicy/mosey/system_ext/private
endif

ifeq ($(TARGET_USES_PREBUILT_VENDOR_SEPOLICY), true)
SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += \
    device/lineage/sepolicy/common/dynamic \
    device/lineage/sepolicy/common/system
else
BOARD_VENDOR_SEPOLICY_DIRS += \
    device/lineage/sepolicy/common/dynamic \
    device/lineage/sepolicy/common/vendor
endif

# Include atv rules on atv product
ifeq ($(PRODUCT_IS_ATV), true)
include device/lineage/sepolicy/atv/sepolicy.mk
endif
