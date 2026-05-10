from __future__ import annotations

from patchlib import SnapshotPatch


CHANGE_ROOT = "packages/apps/PixelExtraParts/changebe/frameworks/base/core/java/android"
SETTINGS_LIB_ROOT = "packages/apps/PixelExtraParts/changebe/frameworks/base/packages/SettingsLib/src/com/android/settingslib"


def get_patches() -> list:
    return [
        SnapshotPatch(
            patch_id="framework-activity",
            title="Activity custom transition hooks",
            target="frameworks/base/core/java/android/app/Activity.java",
            original=f"{CHANGE_ROOT}/app/ActivityOriginal.java",
            modified=f"{CHANGE_ROOT}/app/Activity.java",
            manual_hint=(
                "Port the Activity.java changes from PixelExtraParts/changebe into "
                "frameworks/base/core/java/android/app/Activity.java."
            ),
        ),
        SnapshotPatch(
            patch_id="framework-activity-thread",
            title="ActivityThread PixelExtraParts Pine injection",
            target="frameworks/base/core/java/android/app/ActivityThread.java",
            original=f"{CHANGE_ROOT}/app/ActivityThreadOriginal.java",
            modified=f"{CHANGE_ROOT}/app/ActivityThread.java",
            manual_hint=(
                "Port the ActivityThread.java changes from PixelExtraParts/changebe into "
                "frameworks/base/core/java/android/app/ActivityThread.java."
            ),
        ),
        SnapshotPatch(
            patch_id="framework-application-package-manager-icons",
            title="ApplicationPackageManager PixelParts icon overrides",
            target="frameworks/base/core/java/android/app/ApplicationPackageManager.java",
            original=f"{CHANGE_ROOT}/app/ApplicationPackageManagerOriginal.java",
            modified=f"{CHANGE_ROOT}/app/ApplicationPackageManager.java",
            manual_hint=(
                "Port the ApplicationPackageManager.java icon override backend from "
                "PixelExtraParts/changebe into frameworks/base/core/java/android/app/"
                "ApplicationPackageManager.java."
            ),
        ),
        SnapshotPatch(
            patch_id="framework-launcher-activity-info-icons",
            title="LauncherActivityInfo PixelParts icon overrides",
            target="frameworks/base/core/java/android/content/pm/LauncherActivityInfo.java",
            original=f"{CHANGE_ROOT}/content/pm/LauncherActivityInfoOriginal.java",
            modified=f"{CHANGE_ROOT}/content/pm/LauncherActivityInfo.java",
            manual_hint=(
                "Port the LauncherActivityInfo.java PixelParts icon override hook from "
                "PixelExtraParts/changebe into frameworks/base/core/java/android/content/"
                "pm/LauncherActivityInfo.java."
            ),
        ),
        SnapshotPatch(
            patch_id="framework-settingslib-icon-shape",
            title="SettingsLib PixelParts icon shape controls",
            target="frameworks/base/packages/SettingsLib/src/com/android/settingslib/Utils.java",
            original=f"{SETTINGS_LIB_ROOT}/UtilsOriginal.java",
            modified=f"{SETTINGS_LIB_ROOT}/Utils.java",
            manual_hint=(
                "Port the SettingsLib Utils.java PixelParts icon shape controls from "
                "PixelExtraParts/changebe into frameworks/base/packages/SettingsLib/src/"
                "com/android/settingslib/Utils.java."
            ),
        ),
        SnapshotPatch(
            patch_id="framework-edge-effect",
            title="EdgeEffect custom overscroll hooks",
            target="frameworks/base/core/java/android/widget/EdgeEffect.java",
            original=f"{CHANGE_ROOT}/widget/EdgeEffectOriginal.java",
            modified=f"{CHANGE_ROOT}/widget/EdgeEffect.java",
            manual_hint=(
                "Port the EdgeEffect.java changes from PixelExtraParts/changebe into "
                "frameworks/base/core/java/android/widget/EdgeEffect.java."
            ),
        ),
        SnapshotPatch(
            patch_id="framework-magnifier",
            title="Magnifier custom setting hooks",
            target="frameworks/base/core/java/android/widget/Magnifier.java",
            original=f"{CHANGE_ROOT}/widget/MagnifierOriginal.java",
            modified=f"{CHANGE_ROOT}/widget/Magnifier.java",
            manual_hint=(
                "Port the Magnifier.java changes from PixelExtraParts/changebe into "
                "frameworks/base/core/java/android/widget/Magnifier.java."
            ),
        ),
        SnapshotPatch(
            patch_id="framework-window-back-dispatcher",
            title="WindowOnBackInvokedDispatcher predictive back hook",
            target="frameworks/base/core/java/android/window/WindowOnBackInvokedDispatcher.java",
            original=f"{CHANGE_ROOT}/window/WindowOnBackInvokedDispatcherOriginal.java",
            modified=f"{CHANGE_ROOT}/window/WindowOnBackInvokedDispatcher.java",
            manual_hint=(
                "Port the WindowOnBackInvokedDispatcher.java changes from PixelExtraParts/changebe "
                "into frameworks/base/core/java/android/window/WindowOnBackInvokedDispatcher.java."
            ),
        ),
    ]
