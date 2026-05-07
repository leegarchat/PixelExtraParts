#!/usr/bin/env python3

"""PixelExtraParts source patch launcher."""

from __future__ import annotations

import argparse
import importlib
import json
import sys
from pathlib import Path
from typing import Iterable


sys.dont_write_bytecode = True

PATCHES_DIR = Path(__file__).resolve().parent
DEFAULT_ROOT = Path(__file__).resolve().parents[4]
DEFAULT_CONFIG = PATCHES_DIR / "config.json"


def _load_patch_modules() -> list:
    sys.path.insert(0, str(PATCHES_DIR))
    modules = []
    for module_file in sorted((PATCHES_DIR / "subpatches").glob("*.py")):
        if module_file.name == "__init__.py":
            continue
        modules.append(importlib.import_module(f"subpatches.{module_file.stem}"))
    return modules


def load_patches() -> list:
    patches = []
    for module in _load_patch_modules():
        if not hasattr(module, "get_patches"):
            raise RuntimeError(f"Subpatch module {module.__name__} does not expose get_patches()")
        patches.extend(module.get_patches())
    return patches


def load_config(path: Path) -> dict:
    if not path.exists():
        return {"version": 1, "bypass_paths": [], "patches": {}}
    with path.open("r", encoding="utf-8") as config_file:
        data = json.load(config_file)
    data.setdefault("version", 1)
    data.setdefault("bypass_paths", [])
    data.setdefault("patches", {})
    return data


def save_config(path: Path, config: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as config_file:
        json.dump(config, config_file, indent=2, ensure_ascii=True)
        config_file.write("\n")


def configure_bypass(config_path: Path, patch_id: str, enabled: bool) -> None:
    config = load_config(config_path)
    patch_config = config.setdefault("patches", {}).setdefault(patch_id, {})
    patch_config["mode"] = "bypass" if enabled else "apply"
    save_config(config_path, config)
    state = "bypass" if enabled else "apply"
    print(f"Configured {patch_id}: mode={state}")


def print_patch_list(patches: Iterable, config: dict, root: Path) -> None:
    from patchlib import PatchContext

    context = PatchContext(
        root=root,
        config=config,
        apply=False,
        apply_bypassed=False,
        verbose=False,
    )
    for patch in patches:
        bypass = "bypass" if context.should_bypass(patch) else "apply"
        print(f"{patch.id:40} {bypass:7} {patch.title}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Apply PixelExtraParts source patches from changebe snapshots.",
    )
    parser.add_argument(
        "--root",
        type=Path,
        default=DEFAULT_ROOT,
        help="Android source tree root. Defaults to the detected workspace root.",
    )
    parser.add_argument(
        "--config",
        type=Path,
        default=DEFAULT_CONFIG,
        help="Patch configurator JSON.",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Dry-run mode. This is also the default when --apply is not passed.",
    )
    parser.add_argument(
        "--apply",
        action="store_true",
        help="Write changes. Without this flag the launcher only reports what would happen.",
    )
    parser.add_argument(
        "--apply-bypassed",
        action="store_true",
        help="Apply patches even when their config mode is bypass.",
    )
    parser.add_argument(
        "--list",
        action="store_true",
        help="List known patches and effective modes.",
    )
    parser.add_argument(
        "--configure-bypass",
        nargs=2,
        metavar=("PATCH_ID", "on|off"),
        help="Set a patch mode in config.json. 'on' means bypass, 'off' means apply.",
    )
    parser.add_argument(
        "--verbose",
        action="store_true",
        help="Print extra details for failed patches.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    config_path = args.config.resolve()

    if args.configure_bypass:
        patch_id, state = args.configure_bypass
        normalized = state.lower()
        if normalized not in {"on", "off", "true", "false", "1", "0"}:
            print("--configure-bypass state must be on/off", file=sys.stderr)
            return 2
        configure_bypass(config_path, patch_id, normalized in {"on", "true", "1"})
        return 0

    config = load_config(config_path)
    patches = load_patches()
    root = args.root.resolve()

    if args.list:
        print_patch_list(patches, config, root)
        return 0

    from patchlib import PatchContext

    context = PatchContext(
        root=root,
        config=config,
        apply=args.apply,
        apply_bypassed=args.apply_bypassed,
        verbose=args.verbose,
    )

    results = [patch.run(context) for patch in patches]
    for result in results:
        print(result.format())

    failed = [result for result in results if result.status == "failed"]
    if failed:
        print("\nManual action required:", file=sys.stderr)
        for result in failed:
            print(f"- {result.id}: {result.manual_hint}", file=sys.stderr)
            if args.verbose and result.details:
                print(result.details, file=sys.stderr)
        return 1

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
