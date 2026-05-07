import argparse
import re
import sys
from pathlib import Path

OFFSETS = {
    "stock": 0,
    "soft": 5,
    "medium": 9,
    "hard": 15,
    "off": 90,
}

TARGETS_SOC = (
    "VIRTUAL-SKIN",
    "VIRTUAL-SKIN-HINT",
    "VIRTUAL-SKIN-CPU-LIGHT-ODPM",
    "VIRTUAL-SKIN-CPU-MID",
    "VIRTUAL-SKIN-CPU-HIGH",
    "VIRTUAL-SKIN-CPU-GPU",
    "VIRTUAL-SKIN-SOC",
    "VIRTUAL-SKIN-GPU",
    "VIRTUAL-SKIN-SPEAKER",
)

TARGETS_BATTERY = (
    "VIRTUAL-SKIN-CHARGE-PERSIST",
    "VIRTUAL-SKIN-CHARGE-WIRED",
    "VIRTUAL-SKIN-CHARGE",
    "VIRTUAL-SKIN-CHARGE-WLC",
)

CONFIG_DIR_NAME = "configs"
COPY_RULES_FILENAME = "ThermalConfigCopyRules.mk"
BASE_CONFIG_NAME = "thermal_info_config"
BASE_CONFIG_FILENAME = f"{BASE_CONFIG_NAME}.json"
THERMAL_AVAILABLE_PROP = "persist.sys.pixelparts.thermal_available"
THERMAL_CONFIG_PROP = "persist.sys.pixelparts.thermal_config"

PARTITION_COPY_OUT = {
    "vendor": "$(TARGET_COPY_OUT_VENDOR)",
    "product": "$(TARGET_COPY_OUT_PRODUCT)",
    "system": "$(TARGET_COPY_OUT_SYSTEM)",
    "system_ext": "$(TARGET_COPY_OUT_SYSTEM_EXT)",
    "odm": "$(TARGET_COPY_OUT_ODM)",
    "vendor_dlkm": "$(TARGET_COPY_OUT_VENDOR_DLKM)",
    "system_dlkm": "$(TARGET_COPY_OUT_SYSTEM_DLKM)",
}

PARTITION_NAMES = tuple(PARTITION_COPY_OUT.keys())
THERMAL_BLOCK_START = "# PixelExtraParts thermal config switch"
THERMAL_BLOCK_END = "# End PixelExtraParts thermal config switch"


class ThermalConfigError(Exception):
    pass


def parse_args():
    parser = argparse.ArgumentParser(description="Generate PixelExtraParts thermal profile configs.")
    parser.add_argument("--vendor-path", required=True, help="Vendor tree path, for example vendor/google/husky")
    parser.add_argument("--thermal-json", help="Optional explicit source thermal_info_config.json path")
    parser.add_argument("--device-codename", help="Device codename used as generated config filename suffix")
    parser.add_argument("--init-rc", required=True, help="PixelExtraParts init rc path to update")
    parser.add_argument("--quiet", action="store_true", help="Only print make-friendly status/error lines")
    return parser.parse_args()


def find_android_root(start_path):
    current_path = start_path.resolve()
    for _ in range(10):
        if (current_path / "vendor").exists() and (current_path / "device").exists():
            return current_path
        if current_path.parent == current_path:
            break
        current_path = current_path.parent
    raise ThermalConfigError("Android source root was not found")


SCRIPT_DIR = Path(__file__).resolve().parent
ANDROID_ROOT = find_android_root(SCRIPT_DIR)
REL_SCRIPT_PATH = SCRIPT_DIR.relative_to(ANDROID_ROOT)


def resolve_source_path(path_value):
    path = Path(path_value)
    if path.is_absolute():
        return path
    return ANDROID_ROOT / path


def to_root_relative(path):
    path = path.resolve()
    try:
        return path.relative_to(ANDROID_ROOT)
    except ValueError as error:
        raise ThermalConfigError(f"Path is outside Android source root: {path}") from error


def read_text(path):
    return path.read_text(encoding="utf-8", errors="ignore")


def parse_vendor_copy_rules(vendor_path):
    rules = []
    rule_pattern = re.compile(
        r"(?P<src>[^\s:]+thermal_info_config\.json)\s*:\s*(?P<dst>\$\(TARGET_COPY_OUT_[A-Z_]+\)/[^\s\\]+thermal_info_config\.json)"
    )

    for makefile in sorted(vendor_path.glob("*.mk")):
        content = read_text(makefile)
        for match in rule_pattern.finditer(content):
            source = resolve_source_path(match.group("src"))
            destination = match.group("dst")
            rules.append((source.resolve(), destination))

    return rules


def destination_dir_from_rule(destination):
    destination_path = destination.rsplit("/", 1)[0]
    if destination_path:
        return destination_path
    raise ThermalConfigError(f"Invalid thermal destination in vendor makefile: {destination}")


def infer_destination_dir(source_rel_path, vendor_rel_path, vendor_rules):
    source_abs = (ANDROID_ROOT / source_rel_path).resolve()
    for rule_source, rule_destination in vendor_rules:
        if source_abs == rule_source:
            return destination_dir_from_rule(rule_destination)

    try:
        path_parts = source_rel_path.relative_to(vendor_rel_path).parts
    except ValueError:
        path_parts = source_rel_path.parts

    if "proprietary" in path_parts:
        path_parts = path_parts[path_parts.index("proprietary") + 1:]

    for index, part in enumerate(path_parts):
        if part in PARTITION_COPY_OUT:
            relative_destination_parts = path_parts[index + 1:-1]
            destination = PARTITION_COPY_OUT[part]
            if relative_destination_parts:
                destination += "/" + "/".join(relative_destination_parts)
            return destination

    raise ThermalConfigError(
        f"Could not infer destination partition for {source_rel_path}; set THERMAL_CUSTOM_JSON_PATH to a path with a partition segment or add a vendor mk copy rule"
    )


def find_default_source(vendor_path, vendor_rel_path, vendor_rules):
    candidates = sorted(vendor_path.rglob(BASE_CONFIG_FILENAME))
    if not candidates:
        return None

    for rule_source, _ in vendor_rules:
        for candidate in candidates:
            if candidate.resolve() == rule_source:
                return candidate

    preferred_suffixes = (
        Path("proprietary/vendor/etc") / BASE_CONFIG_FILENAME,
        Path("vendor/etc") / BASE_CONFIG_FILENAME,
    )

    for suffix in preferred_suffixes:
        for candidate in candidates:
            try:
                candidate_rel = candidate.relative_to(vendor_path)
            except ValueError:
                continue
            if candidate_rel == suffix:
                return candidate

    return candidates[0]


def process_hot_threshold_values(values, offset):
    new_items = []

    for item in values.split(","):
        item = item.strip()
        if not item:
            continue

        if re.match(r"^-?\d+(\.\d+)?$", item):
            value = float(item)
            new_items.append(str(round(value + offset, 1)))
        else:
            new_items.append(item)

    return ", ".join(new_items)


def patch_file_content(content, targets_soc, offset_soc, targets_battery, offset_battery):
    replacements = []
    name_pattern = re.compile(r'"Name"\s*:\s*"([^"]+)"')
    threshold_pattern = re.compile(r'"HotThreshold"\s*:\s*\[(.*?)\]', re.DOTALL)

    for name_match in name_pattern.finditer(content):
        sensor_name = name_match.group(1)
        start_pos = name_match.end()

        current_offset = 0
        if sensor_name in targets_soc:
            current_offset = offset_soc
        elif sensor_name in targets_battery:
            current_offset = offset_battery

        if current_offset == 0:
            continue

        threshold_match = threshold_pattern.search(content, pos=start_pos)
        if not threshold_match:
            continue

        chunk_between = content[start_pos:threshold_match.start()]
        if chunk_between.count("}") > chunk_between.count("{"):
            continue

        replacements.append((
            threshold_match.start(1),
            threshold_match.end(1),
            process_hot_threshold_values(threshold_match.group(1), current_offset),
        ))

    result_content = content
    for start, end, replacement in sorted(replacements, key=lambda item: item[0], reverse=True):
        result_content = result_content[:start] + replacement + result_content[end:]

    return result_content


def write_copy_rules(generated_base_names, destination_dir):
    copy_rules_path = SCRIPT_DIR / COPY_RULES_FILENAME
    with copy_rules_path.open("w", encoding="utf-8") as makefile:
        makefile.write("# Auto-generated thermal config copy rules\n")
        makefile.write(f"# Generated by script in: {REL_SCRIPT_PATH}\n\n")

        if not generated_base_names:
            makefile.write("# Thermal config generation is disabled for this target.\n")
            return

        makefile.write("PRODUCT_COPY_FILES += \\\n")
        for base_name in generated_base_names[:-1]:
            source = f"{REL_SCRIPT_PATH}/{CONFIG_DIR_NAME}/{base_name}_$(DEVICE_CODENAME).json"
            destination = f"{destination_dir}/{base_name}.json"
            makefile.write(f"    {source}:{destination} \\\n")

        last_base_name = generated_base_names[-1]
        last_source = f"{REL_SCRIPT_PATH}/{CONFIG_DIR_NAME}/{last_base_name}_$(DEVICE_CODENAME).json"
        last_destination = f"{destination_dir}/{last_base_name}.json"
        makefile.write(f"    {last_source}:{last_destination}\n")


def strip_old_thermal_block(content):
    lines = content.splitlines()
    new_lines = []
    index = 0

    while index < len(lines):
        stripped = lines[index].strip()

        if stripped == THERMAL_BLOCK_START:
            index += 1
            while index < len(lines) and lines[index].strip() != THERMAL_BLOCK_END:
                index += 1
            if index < len(lines):
                index += 1
            continue

        normalized = stripped.lstrip("# ").strip()
        if normalized == "on property:persist.sys.pixelparts.thermal_config=*":
            index += 1
            while index < len(lines):
                next_normalized = lines[index].strip().lstrip("# ").strip()
                if next_normalized.startswith("setprop vendor.thermal.config") or next_normalized.startswith("restart vendor.thermal-hal") or not next_normalized:
                    index += 1
                    continue
                break
            continue

        if stripped == "on init" and index + 1 < len(lines) and THERMAL_AVAILABLE_PROP in lines[index + 1]:
            index += 2
            continue

        new_lines.append(lines[index])
        index += 1

    while new_lines and not new_lines[-1].strip():
        new_lines.pop()

    return "\n".join(new_lines)


def update_init_rc(init_rc_path, enabled):
    content = read_text(init_rc_path) if init_rc_path.exists() else ""
    content = strip_old_thermal_block(content)

    availability = "true" if enabled else "false"
    if enabled:
        switch_lines = [
            "on property:persist.sys.pixelparts.thermal_config=*",
            "    setprop vendor.thermal.config ${persist.sys.pixelparts.thermal_config}",
            "    restart vendor.thermal-hal",
        ]
    else:
        switch_lines = [
            "# Thermal configs were not generated for this target.",
            "# on property:persist.sys.pixelparts.thermal_config=*",
            "#     setprop vendor.thermal.config ${persist.sys.pixelparts.thermal_config}",
            "#     restart vendor.thermal-hal",
        ]

    block = [
        THERMAL_BLOCK_START,
        "on init",
        f"    setprop {THERMAL_AVAILABLE_PROP} {availability}",
        "",
        *switch_lines,
        THERMAL_BLOCK_END,
    ]

    updated_content = content + "\n\n" + "\n".join(block) + "\n"
    init_rc_path.write_text(updated_content, encoding="utf-8")


def generate_configs(source_path, device_codename, destination_dir):
    base_content = read_text(source_path)
    config_dir = SCRIPT_DIR / CONFIG_DIR_NAME
    config_dir.mkdir(parents=True, exist_ok=True)

    generated_base_names = []
    for soc_name, soc_offset in OFFSETS.items():
        for battery_name, battery_offset in OFFSETS.items():
            if soc_name == "stock" and battery_name == "stock":
                continue

            new_content = patch_file_content(base_content, TARGETS_SOC, soc_offset, TARGETS_BATTERY, battery_offset)
            soc_part = f"_soc_{soc_name}" if soc_name != "stock" else ""
            battery_part = f"_battery_{battery_name}" if battery_name != "stock" else ""
            base_name = f"{BASE_CONFIG_NAME}{soc_part}{battery_part}"
            target_path = config_dir / f"{base_name}_{device_codename}.json"
            target_path.write_text(new_content, encoding="utf-8")
            generated_base_names.append(base_name)

    generated_base_names = sorted(set(generated_base_names))
    write_copy_rules(generated_base_names, destination_dir)
    return generated_base_names


def run(args):
    vendor_path = resolve_source_path(args.vendor_path)
    if not vendor_path.exists():
        raise ThermalConfigError(f"VENDOR_PATH does not exist: {args.vendor_path}")

    vendor_rel_path = to_root_relative(vendor_path)
    vendor_rules = parse_vendor_copy_rules(vendor_path)
    init_rc_path = resolve_source_path(args.init_rc)
    device_codename = args.device_codename or vendor_path.name

    if args.thermal_json:
        source_path = resolve_source_path(args.thermal_json)
        if not source_path.exists():
            raise ThermalConfigError(f"THERMAL_CUSTOM_JSON_PATH does not exist: {args.thermal_json}")
    else:
        source_path = find_default_source(vendor_path, vendor_rel_path, vendor_rules)
        if source_path is None:
            write_copy_rules([], "$(TARGET_COPY_OUT_VENDOR)/etc")
            update_init_rc(init_rc_path, enabled=False)
            return f"PixelExtraPartsThermal=disabled vendor_path={args.vendor_path}"

    source_rel_path = to_root_relative(source_path)
    destination_dir = infer_destination_dir(source_rel_path, vendor_rel_path, vendor_rules)
    generated_files = generate_configs(source_path, device_codename, destination_dir)
    update_init_rc(init_rc_path, enabled=True)
    return f"PixelExtraPartsThermal=enabled source={source_rel_path} destination={destination_dir} generated={len(generated_files)}"


def main():
    args = parse_args()
    try:
        result = run(args)
    except ThermalConfigError as error:
        print(f"PixelExtraPartsThermalError: {error}")
        return 1

    if result and not args.quiet:
        print(result)
    return 0


if __name__ == "__main__":
    sys.exit(main())
