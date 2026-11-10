#!/usr/bin/env python3
"""PEP weave: build-time patch applicator (strict, no fuzz).

Reads vanilla sources from the live tree, applies unified-diff specs,
writes results into PEP's weave_gen/ (git-ignored). Any context mismatch
fails LOUDLY (nonzero exit + PEP_WEAVE_ERROR) so base drift never goes
silent. Pure stdlib, no `patch(1)` dependency for hermeticity.
"""
import re
import sys
from pathlib import Path

HUNK_RE = re.compile(r"^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@")

# Weave specs are anchored on Android 17 (Cinnamon Bun). Trees from other
# releases (AOSPA, Rising16, ...) are not covered: the applicator refuses
# them loudly instead of mis-applying.
PEP_SUPPORTED_SDK = 37


def apply_unified_diff(base_lines, diff_text, label="spec", target=None):
    """Apply unified diff to base_lines (list of lines WITH endings).

    Content-exact, position-flexible: each hunk's context must match
    byte-for-byte at exactly one location (searched near the recorded
    offset first). Returns new list of lines. Raises ValueError on any
    mismatch (loud failure, never silent mis-application).
    """
    hunks = []
    lines = diff_text.splitlines()
    i = 0
    while i < len(lines):
        m = HUNK_RE.match(lines[i])
        if not m:
            i += 1
            continue
        old_start = int(m.group(1))
        old_count = int(m.group(2)) if m.group(2) is not None else 1
        new_count = int(m.group(4)) if m.group(4) is not None else 1
        header = lines[i].strip()
        i += 1
        old_block, new_block = [], []
        while i < len(lines) and not HUNK_RE.match(lines[i]):
            ln = lines[i]
            if ln.startswith("\\"):
                i += 1
                continue
            if not ln or ln[0] not in (" ", "-", "+"):
                raise ValueError(f"{label}: bad hunk line: {ln!r}")
            if ln[0] in (" ", "-"):
                old_block.append(ln[1:])
            if ln[0] in (" ", "+"):
                new_block.append(ln[1:])
            i += 1
        hunks.append((old_start, old_count, new_count, old_block, new_block, header))

    if not hunks:
        raise ValueError(f"{label}: no hunks found")

    # work on content without line endings, track trailing newline separately
    base = [l.rstrip("\n") for l in base_lines]
    base_has_nl = not base_lines or base_lines[-1].endswith("\n")
    out = list(base)
    offset = 0  # cumulative shift from already-applied hunks
    for hi, (old_start, old_count, new_count, old_block, new_block, header) in enumerate(hunks):
        if len(old_block) != old_count:
            raise ValueError(
                f"{label} hunk {hi}: context has {len(old_block)} lines, header says {old_count}"
            )
        where = f"target={target} " if target else ""
        guess = old_start - 1 + offset
        pos = None
        # 1) exact recorded position (adjusted by accumulated offset)
        if guess >= 0 and out[guess:guess + len(old_block)] == old_block:
            pos = guess
        else:
            # 2) unique match anywhere (base drifted above this hunk)
            matches = [
                p
                for p in range(len(out) - len(old_block) + 1)
                if out[p:p + len(old_block)] == old_block
            ]
            if len(matches) == 1:
                pos = matches[0]
            elif not matches:
                first = next((ln for ln in old_block if ln.strip()), "")
                raise ValueError(
                    f"{label} {where}hunk {hi} ({header}): context NOT FOUND "
                    f"in live vanilla ({len(base)} base lines, {len(old_block)} "
                    f"expected lines, recorded at base line ~{old_start}, first "
                    f"expected line: {first.strip()[:100]!r}). Base drifted — "
                    "rebase the spec."
                )
            else:
                raise ValueError(
                    f"{label} {where}hunk {hi} ({header}): context matches "
                    f"{len(matches)} places, ambiguous."
                )
        out[pos:pos + len(old_block)] = new_block
        offset += len(new_block) - len(old_block)

    text = "\n".join(out)
    if base_has_nl:
        text += "\n"
    return text


def detect_tree_sdk(top):
    """Best-effort tree SDK probe: max VERSION_CODES constant (excluding
    CUR_DEVELOPMENT) in frameworks/base Build.java. Returns int or None."""
    try:
        text = (top / "frameworks/base/core/java/android/os/Build.java").read_text()
    except OSError:
        return None
    start = text.find("public static class VERSION_CODES {")
    if start < 0:
        return None
    end = text.find("public static class VERSION_CODES_FULL", start)
    seg = text[start:] if end < 0 else text[start:end]
    vals = [
        int(v)
        for _, v in re.findall(r"public static final int ([A-Z_0-9]+) = (\d+);", seg)
        if _.strip() != "CUR_DEVELOPMENT"
    ]
    return max(vals) if vals else None


def check_sdk(top, sdk_arg):
    """Enforce the A17-only gate. Returns None on success, error string on failure."""
    if sdk_arg and sdk_arg.strip():
        try:
            sdk = int(sdk_arg.strip())
        except ValueError:
            return (
                f"PEP_WEAVE_ERROR: bad --sdk value {sdk_arg!r}, expected integer "
                f"{PEP_SUPPORTED_SDK} (Android 17)"
            )
        if sdk != PEP_SUPPORTED_SDK:
            return (
                f"PEP_WEAVE_ERROR: unsupported PLATFORM_SDK_VERSION={sdk}, weave specs "
                f"are anchored on SDK {PEP_SUPPORTED_SDK} (Android 17). "
                f"AOSPA/Rising16 (non-A17) trees are not covered."
            )
        return None
    detected = detect_tree_sdk(top)
    if detected is None:
        return (
            "PEP_WEAVE_ERROR: cannot verify tree SDK "
            "(frameworks/base/core/java/android/os/Build.java unreadable); "
            f"weave specs require SDK {PEP_SUPPORTED_SDK} (Android 17). "
            "Pass --sdk explicitly or rebase the specs."
        )
    if detected != PEP_SUPPORTED_SDK:
        return (
            f"PEP_WEAVE_ERROR: unsupported tree SDK={detected}, weave specs are "
            f"anchored on SDK {PEP_SUPPORTED_SDK} (Android 17). "
            f"AOSPA/Rising16 (non-A17) trees are not covered."
        )
    return None


def rom_variant_path(spec, rom):
    """Conventional per-ROM variant: specs/<rom>/<rel>.patch next to specs/<rel>.patch."""
    marker = "/weave/specs/"
    i = spec.find(marker)
    if i < 0:
        return None
    return spec[: i + len(marker)] + rom + "/" + spec[i + len(marker):]


def variant_chain(top, entry, rom):
    """Ordered spec candidates for --rom. Without --rom (or --rom evo) the
    chain is just the default spec (legacy behavior). Otherwise: the
    ROM-specific variant first, then the shared aosp variant, then the
    default (evo) spec as last resort. Unknown ROMs therefore try
    [<rom>, aosp, default]. Strict apply decides: a variant either matches
    byte-for-byte or is skipped loudly at the end, never fuzzed.
    """
    default = entry.get("spec")
    if not rom or rom == "evo":
        return [default] if default else []
    fallbacks = entry.get("fallbacks") or {}
    cands = []
    for cand in (
        rom_variant_path(default, rom) if default else None,
        fallbacks.get(rom),
        fallbacks.get("aosp"),
        rom_variant_path(default, "aosp") if default else None,
        default,
    ):
        if cand and cand not in cands:
            cands.append(cand)
    # Drop conventional paths that do not exist on disk (explicit manifest
    # fallbacks are kept: a dangling pointer must fail loudly, not silently).
    return [
        c
        for c in cands
        if c == default or c in fallbacks.values() or (top / c).is_file()
    ]


def process_entry(top, entry, rom):
    """Apply one manifest entry, trying the ROM variant chain. Returns
    (status, applied_spec). Raises ValueError with the full tried-list when
    every variant fails."""
    rel, out_rel = entry["rel"], entry["out"]
    base_p = top / rel
    try:
        base_lines = base_p.read_text().splitlines(keepends=True)
    except OSError as ex:
        raise ValueError(f"PEP_WEAVE_ERROR: missing file: {base_p} (entry {entry})")
    cands = variant_chain(top, entry, rom)
    errors = []
    for cand in cands:
        spec_p = top / cand
        try:
            diff_text = spec_p.read_text()
        except OSError as ex:
            errors.append(f"{cand}: unreadable ({ex})")
            continue
        try:
            new_text = apply_unified_diff(
                base_lines, diff_text, label=cand, target=rel
            )
        except ValueError as ex:
            errors.append(str(ex))
            continue
        out_p = top / out_rel
        if out_p.exists() and out_p.read_text() == new_text:
            return "unchanged", cand
        out_p.parent.mkdir(parents=True, exist_ok=True)
        out_p.write_text(new_text)
        return "updated", cand
    first = errors[0] if errors else "no candidates"
    tried = "; ".join(cands) if cands else "(none)"
    rom_name = rom or "evo(default)"
    conventional = (
        rom_variant_path(entry.get("spec") or "", rom or "aosp") or "soong/weave/specs/<rom>/<rel>.patch"
    )
    raise ValueError(
        f"PEP_WEAVE_ERROR: {rel}: no variant applied for rom '{rom_name}' "
        f"(tried: {tried}). First failure: {first}. "
        f"Rebase: regenerate the unified diff against the '{rom_name}' tree vanilla "
        f"and save it as {conventional} (or extend manifest \"fallbacks\"), e.g. "
        f"diff -u <vanilla {rel}> <modified {rel}> > <spec>, then rebuild."
    )


def main(argv):
    # usage: weave_apply.py --top <repo-root> --manifest <json> [--rom <rom>] [--sdk <n>]
    # manifest: [{"rel": "frameworks/base/...", "spec": "packages/.../weave/specs/...",
    #             "out": "packages/.../weave_gen/...", "type": "patch|copy",
    #             "fallbacks": {"<rom>": "<variant spec>", ...} (optional)}, ...]
    # --rom tries the per-ROM variant chain (strict, no fuzz); without --rom
    # only the default (evo) spec is used. --sdk enforces the A17 gate
    # explicitly; when empty/absent the tree SDK is probed from Build.java.
    import json

    top = manifest = None
    rom = None
    sdk_arg = None
    args = list(argv)
    while args:
        a = args.pop(0)
        if a == "--top":
            top = Path(args.pop(0))
        elif a == "--manifest":
            manifest = Path(args.pop(0))
        elif a == "--rom":
            rom = args.pop(0).strip().lower() or None
        elif a == "--sdk":
            sdk_arg = args.pop(0)
    if not top or not manifest:
        print("PEP_WEAVE_ERROR: usage --top <root> --manifest <json> [--rom <rom>] [--sdk <n>]")
        return 1
    sdk_err = check_sdk(top, sdk_arg)
    if sdk_err:
        print(sdk_err)
        return 1
    try:
        entries = json.loads(manifest.read_text())
    except Exception as e:
        print(f"PEP_WEAVE_ERROR: bad manifest: {e}")
        return 1
    changed = 0
    for e in entries:
        try:
            if e.get("type", "patch") == "copy":
                src, dst = top / e["rel"], top / e["out"]
                data = src.read_bytes()
                if not dst.exists() or dst.read_bytes() != data:
                    dst.parent.mkdir(parents=True, exist_ok=True)
                    dst.write_bytes(data)
                    changed += 1
            else:
                st, _applied = process_entry(top, e, rom)
                if st == "updated":
                    changed += 1
        except FileNotFoundError as ex:
            print(f"PEP_WEAVE_ERROR: missing file: {ex.filename} (entry {e})")
            return 1
        except ValueError as ex:
            print(f"{ex}")
            return 1
        except Exception as ex:
            print(f"PEP_WEAVE_ERROR: {type(ex).__name__}: {ex} (entry {e})")
            return 1
    rom_tag = rom or "evo(default)"
    print(f"PEP_WEAVE_OK changed={changed} total={len(entries)} rom={rom_tag}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
