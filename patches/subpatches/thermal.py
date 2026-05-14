from __future__ import annotations

from patchlib import SnapshotPatch


THERMAL_TARGET_ROOT = "hardware/google/pixel/thermal"
THERMAL_CHANGE_ROOT = "packages/apps/PixelExtraParts/changebe/hardware/google/pixel/thermal"


THERMAL_RUNTIME_FILES = [
    "thermal-helper.cpp",
    "thermal-helper.h",
    "utils/power_files.cpp",
    "utils/power_files.h",
    "utils/powerhal_helper.cpp",
    "utils/thermal_files.h",
    "utils/thermal_info.cpp",
    "utils/thermal_info.h",
    "utils/thermal_predictions_helper.cpp",
    "utils/thermal_predictions_helper.h",
    "utils/thermal_stats_helper.cpp",
    "utils/thermal_stats_helper.h",
    "utils/thermal_throttling.cpp",
    "utils/thermal_throttling.h",
    "utils/thermal_watcher.cpp",
    "utils/thermal_watcher.h",
]


def _patch_id(relative_path: str) -> str:
    return "thermal-" + relative_path.replace("/", "-").replace(".", "-").replace("_", "-")


def _original_snapshot(relative_path: str) -> str:
    directory, _, file_name = relative_path.rpartition("/")
    stem, _, extension = file_name.rpartition(".")
    original_name = f"{stem}Original.{extension}"
    if directory:
        return f"{THERMAL_CHANGE_ROOT}/{directory}/{original_name}"
    return f"{THERMAL_CHANGE_ROOT}/{original_name}"


def _thermal_patch(relative_path: str) -> SnapshotPatch:
    return SnapshotPatch(
        patch_id=_patch_id(relative_path),
        title=f"PixelParts runtime thermal reload for {relative_path}",
        target=f"{THERMAL_TARGET_ROOT}/{relative_path}",
        original=_original_snapshot(relative_path),
        modified=f"{THERMAL_CHANGE_ROOT}/{relative_path}",
        manual_hint=(
            f"Port {THERMAL_CHANGE_ROOT}/{relative_path} into "
            f"{THERMAL_TARGET_ROOT}/{relative_path}, then rerun the patcher."
        ),
    )


def get_patches() -> list:
    return [_thermal_patch(relative_path) for relative_path in THERMAL_RUNTIME_FILES]