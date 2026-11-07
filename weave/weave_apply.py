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


def apply_unified_diff(base_lines, diff_text, label="spec"):
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
        hunks.append((old_start, old_count, new_count, old_block, new_block))

    if not hunks:
        raise ValueError(f"{label}: no hunks found")

    # work on content without line endings, track trailing newline separately
    base = [l.rstrip("\n") for l in base_lines]
    base_has_nl = not base_lines or base_lines[-1].endswith("\n")
    out = list(base)
    offset = 0  # cumulative shift from already-applied hunks
    for hi, (old_start, old_count, new_count, old_block, new_block) in enumerate(hunks):
        if len(old_block) != old_count:
            raise ValueError(
                f"{label} hunk {hi}: context has {len(old_block)} lines, header says {old_count}"
            )
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
                raise ValueError(
                    f"{label} hunk {hi} (@@ -{old_start}): context NOT FOUND in base "
                    f"({len(old_block)} lines). Base drifted — rebase the spec."
                )
            else:
                raise ValueError(
                    f"{label} hunk {hi}: context matches {len(matches)} places, ambiguous."
                )
        out[pos:pos + len(old_block)] = new_block
        offset += len(new_block) - len(old_block)

    text = "\n".join(out)
    if base_has_nl:
        text += "\n"
    return text


def process_one(top, rel, spec, out_rel):
    base_p = top / rel
    spec_p = top / spec
    out_p = top / out_rel
    base_text = base_p.read_text()
    diff_text = spec_p.read_text()
    new_text = apply_unified_diff(
        base_text.splitlines(keepends=True), diff_text, label=spec
    )
    if out_p.exists() and out_p.read_text() == new_text:
        return "unchanged"
    out_p.parent.mkdir(parents=True, exist_ok=True)
    out_p.write_text(new_text)
    return "updated"


def main(argv):
    # usage: weave_apply.py --top <repo-root> --manifest <json>
    # manifest: [{"rel": "frameworks/base/...", "spec": "packages/.../weave/specs/...",
    #             "out": "packages/.../weave_gen/...", "type": "patch|copy"}, ...]
    import json

    top = manifest = None
    args = list(argv)
    while args:
        a = args.pop(0)
        if a == "--top":
            top = Path(args.pop(0))
        elif a == "--manifest":
            manifest = Path(args.pop(0))
    if not top or not manifest:
        print("PEP_WEAVE_ERROR: usage --top <root> --manifest <json>")
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
                st = process_one(top, e["rel"], e["spec"], e["out"])
                if st == "updated":
                    changed += 1
        except FileNotFoundError as ex:
            print(f"PEP_WEAVE_ERROR: missing file: {ex.filename} (entry {e})")
            return 1
        except ValueError as ex:
            print(f"PEP_WEAVE_ERROR: {ex}")
            return 1
        except Exception as ex:
            print(f"PEP_WEAVE_ERROR: {type(ex).__name__}: {ex} (entry {e})")
            return 1
    print(f"PEP_WEAVE_OK changed={changed} total={len(entries)}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
