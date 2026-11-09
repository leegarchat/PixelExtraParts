#!/usr/bin/env bash
set -euo pipefail

PACKAGE="org.pixel.customparts"
USER_ID="0"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR"
APK_RELATIVE_PATH="out/target/product/shiba/system_ext/priv-app/PixelCustomPartsSystem/PixelCustomPartsSystem.apk"

usage() {
    cat <<EOF
Usage:
  $(basename "$0") install    Install the shiba APK as an update
  $(basename "$0") uninstall  Remove the update and clear PEP data
EOF
}

find_repo_root() {
    while [[ "$REPO_ROOT" != "/" ]]; do
        if [[ -f "$REPO_ROOT/$APK_RELATIVE_PATH" ]]; then
            return 0
        fi
        REPO_ROOT="$(dirname -- "$REPO_ROOT")"
    done

    echo "Could not find shiba APK above: $SCRIPT_DIR/$APK_RELATIVE_PATH" >&2
    exit 1
}

command -v adb >/dev/null 2>&1 || {
    echo "adb not found" >&2
    exit 1
}

[[ $# -eq 1 ]] || {
    usage >&2
    exit 2
}

case "$1" in
    install|uninstall) ;;
    *)
        usage >&2
        exit 2
        ;;
esac

find_repo_root
APK_PATH="$REPO_ROOT/$APK_RELATIVE_PATH"

adb wait-for-device
adb root >/dev/null 2>&1 || true

if ! adb shell id | grep -q 'uid=0'; then
    echo "adb root is not available; run this on a root-capable userdebug build" >&2
    exit 1
fi

if [[ "$1" == "install" ]]; then
    echo "APK: $APK_PATH"
    echo "Installing $PACKAGE update..."
    adb install -r -d "$APK_PATH"
    echo "Installed $PACKAGE."
    adb shell pm path "$PACKAGE"
    exit 0
fi

echo "Stopping $PACKAGE..."
adb shell am force-stop "$PACKAGE"

echo "Clearing user data..."
adb shell cmd package clear --user "$USER_ID" "$PACKAGE" >/dev/null || \
adb shell pm clear --user "$USER_ID" "$PACKAGE" >/dev/null

echo "Removing system-package update..."
if ! adb shell cmd package uninstall-system-updates "$PACKAGE" >/dev/null 2>&1; then
    adb shell pm uninstall-system-updates "$PACKAGE" >/dev/null
fi

adb shell cmd package install-existing --user "$USER_ID" "$PACKAGE" >/dev/null
adb shell pm enable --user "$USER_ID" "$PACKAGE" >/dev/null || true

if adb shell pm path "$PACKAGE" | grep -q '/data/app/'; then
    echo "Warning: /data/app update path is still present." >&2
    exit 1
fi

echo "Done: $PACKAGE update removed; system package preserved."
adb shell pm path "$PACKAGE"
