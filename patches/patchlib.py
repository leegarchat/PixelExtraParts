from __future__ import annotations

import difflib
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Sequence


STATUS_PREFIX = {
    "applied": "OK",
    "patched": "PATCHED",
    "would_patch": "PENDING",
    "bypassed": "BYPASS",
    "failed": "ERROR",
}


@dataclass
class PatchResult:
    id: str
    title: str
    status: str
    message: str
    target: str = ""
    details: str = ""
    manual_hint: str = ""

    def format(self) -> str:
        prefix = STATUS_PREFIX.get(self.status, self.status.upper())
        target = f" [{self.target}]" if self.target else ""
        return f"[{prefix}] {self.id}{target}: {self.message}"


@dataclass
class PatchContext:
    root: Path
    config: dict
    apply: bool
    apply_bypassed: bool
    verbose: bool = False

    def resolve(self, relative_path: str | Path) -> Path:
        return self.root / Path(relative_path)

    def should_bypass(self, patch: "BasePatch") -> bool:
        if self.apply_bypassed:
            return False

        patch_config = self.config.get("patches", {}).get(patch.id, {})
        mode = patch_config.get("mode")
        if mode == "apply":
            return False
        if mode == "bypass":
            return True

        source_path = Path(patch.source_path) if patch.source_path else None
        for bypass_path in self.config.get("bypass_paths", []):
            if source_path and _is_relative_to(source_path, Path(bypass_path)):
                return True

        return patch.bypass_by_default


class BasePatch:
    id: str
    title: str
    source_path: str | None = None
    bypass_by_default = False
    manual_hint = "Apply the matching changebe patch manually, then rerun the launcher."

    def run(self, context: PatchContext) -> PatchResult:
        raise NotImplementedError

    def result(
        self,
        status: str,
        message: str,
        target: str = "",
        details: str = "",
        manual_hint: str | None = None,
    ) -> PatchResult:
        return PatchResult(
            id=self.id,
            title=self.title,
            status=status,
            message=message,
            target=target,
            details=details,
            manual_hint=manual_hint if manual_hint is not None else self.manual_hint,
        )


@dataclass
class Hunk:
    old_block: list[str]
    new_block: list[str]
    old_range: tuple[int, int]
    new_range: tuple[int, int]


class SnapshotPatch(BasePatch):
    def __init__(
        self,
        patch_id: str,
        title: str,
        target: str,
        original: str,
        modified: str,
        original_replacements: Sequence[tuple[str, str]] = (),
        context_lines: int = 5,
        bypass_by_default: bool = False,
        manual_hint: str | None = None,
    ) -> None:
        self.id = patch_id
        self.title = title
        self.target = target
        self.original = original
        self.modified = modified
        self.source_path = modified
        self.original_replacements = list(original_replacements)
        self.context_lines = context_lines
        self.bypass_by_default = bypass_by_default
        if manual_hint:
            self.manual_hint = manual_hint

    def run(self, context: PatchContext) -> PatchResult:
        target_path = context.resolve(self.target)
        original_path = context.resolve(self.original)
        modified_path = context.resolve(self.modified)

        missing = [str(path) for path in (target_path, original_path, modified_path) if not path.exists()]
        if missing:
            return self.result(
                "failed",
                "required patch input is missing",
                self.target,
                details="\n".join(missing),
                manual_hint=f"Missing file(s): {', '.join(missing)}",
            )

        original_lines = _read_lines(original_path)
        modified_lines = _read_lines(modified_path)
        target_lines = _read_lines(target_path)
        original_lines = _replace_in_lines(original_lines, self.original_replacements)

        if target_lines == modified_lines:
            return self.result("applied", "target already matches modified snapshot", self.target)

        hunks = _build_hunks(original_lines, modified_lines, self.context_lines)
        if not hunks:
            return self.result("applied", "no snapshot differences", self.target)

        working_lines = list(target_lines)
        changed_hunks: list[Hunk] = []
        conflicts: list[str] = []

        for index, hunk in enumerate(hunks, start=1):
            if _contains_block(working_lines, hunk.new_block):
                continue

            if context.should_bypass(self):
                return self.result("bypassed", "patch is configured for bypass", self.target)

            matches = _find_block_indexes(working_lines, hunk.old_block)
            if len(matches) == 1:
                start = matches[0]
                working_lines = (
                    working_lines[:start]
                    + hunk.new_block
                    + working_lines[start + len(hunk.old_block) :]
                )
                changed_hunks.append(hunk)
                continue

            if len(matches) > 1:
                conflicts.append(
                    f"hunk {index}: old structure matched {len(matches)} locations "
                    f"around original lines {hunk.old_range[0]}-{hunk.old_range[1]}"
                )
            else:
                conflicts.append(
                    f"hunk {index}: neither modified nor original structure matched "
                    f"around original lines {hunk.old_range[0]}-{hunk.old_range[1]}"
                )

        if conflicts:
            return self.result(
                "failed",
                "snapshot drift; cannot apply safely",
                self.target,
                details="\n".join(conflicts),
                manual_hint=(
                    f"Review {self.original} -> {self.modified} and port the matching change "
                    f"into {self.target}, then rerun the patcher."
                ),
            )

        if not changed_hunks:
            return self.result("applied", "all modified structures are already present", self.target)

        if not context.apply:
            return self.result(
                "would_patch",
                f"would apply {len(changed_hunks)} structural hunk(s)",
                self.target,
            )

        target_path.write_text("".join(working_lines), encoding="utf-8")
        return self.result(
            "patched",
            f"applied {len(changed_hunks)} structural hunk(s)",
            self.target,
        )


class ActivityThreadInjectPatch(BasePatch):
    id = "framework-activity-thread-injection"
    title = "Inject PixelExtraParts Pine loader into ActivityThread"
    target = "frameworks/base/core/java/android/app/ActivityThread.java"
    source_path = "packages/apps/PixelExtraParts/changebe/frameworks/base/core/java/android/app/ActivityThread.java"
    constants_start = "    // --- [PixelParts] CONSTANTS ---"
    constants_end = "    // ------------------------------"
    injection_start = "            // --- [PixelParts] INJECTION START ---"
    injection_end = "            // --- [PixelParts] INJECTION END ---"
    constants_anchor = (
        "    @RavenwoodIgnore\n"
        "    private static DdmSyncStageUpdater newDdmSyncStageUpdater() {\n"
        "        return new DdmSyncStageUpdater();\n"
        "    }\n"
    )
    injection_anchor = (
        "            try {\n"
        "                timestampApplicationOnCreateNs = SystemClock.uptimeNanos();\n"
        "                mInstrumentation.callApplicationOnCreate(app);\n"
    )
    manual_hint = (
        "Port the PixelParts constants near newDdmSyncStageUpdater() and the injection block "
        "immediately before mInstrumentation.callApplicationOnCreate(app)."
    )

    def run(self, context: PatchContext) -> PatchResult:
        target_path = context.resolve(self.target)
        source_path = context.resolve(self.source_path)
        if not target_path.exists() or not source_path.exists():
            return self.result("failed", "ActivityThread target or source snippet is missing", self.target)

        source = source_path.read_text(encoding="utf-8")
        target = target_path.read_text(encoding="utf-8")
        constants = _extract_marked_block(source, self.constants_start, self.constants_end)
        injection = _extract_marked_block(source, self.injection_start, self.injection_end)

        has_constants_marker = self.constants_start in target or self.constants_end in target
        has_injection_marker = self.injection_start in target or self.injection_end in target
        constants_exact = constants in target
        injection_exact = injection in target

        if constants_exact and injection_exact:
            return self.result("applied", "ActivityThread injection is already present", self.target)

        if context.should_bypass(self):
            return self.result("bypassed", "patch is configured for bypass", self.target)

        if (has_constants_marker and not constants_exact) or (has_injection_marker and not injection_exact):
            return self.result(
                "failed",
                "existing PixelParts markers differ from the expected snippet",
                self.target,
                manual_hint=self.manual_hint,
            )

        updated = target
        if not constants_exact:
            constants_anchor_index = updated.find(self.constants_anchor)
            if constants_anchor_index < 0:
                return self.result(
                    "failed",
                    "constants anchor was not found",
                    self.target,
                    manual_hint=self.manual_hint,
                )
            insert_at = constants_anchor_index + len(self.constants_anchor)
            updated = updated[:insert_at] + constants + "\n" + updated[insert_at:]

        if not injection_exact:
            injection_anchor_index = updated.find(self.injection_anchor)
            if injection_anchor_index < 0:
                return self.result(
                    "failed",
                    "injection anchor was not found",
                    self.target,
                    manual_hint=self.manual_hint,
                )
            updated = updated[:injection_anchor_index] + injection + "\n" + updated[injection_anchor_index:]

        if not context.apply:
            return self.result("would_patch", "would insert ActivityThread snippets", self.target)

        target_path.write_text(updated, encoding="utf-8")
        return self.result("patched", "inserted ActivityThread snippets", self.target)


def _read_lines(path: Path) -> list[str]:
    return path.read_text(encoding="utf-8").splitlines(keepends=True)


def _replace_in_lines(lines: Iterable[str], replacements: Sequence[tuple[str, str]]) -> list[str]:
    replaced = []
    for line in lines:
        for old, new in replacements:
            line = line.replace(old, new)
        replaced.append(line)
    return replaced


def _build_hunks(original: list[str], modified: list[str], context_lines: int) -> list[Hunk]:
    matcher = difflib.SequenceMatcher(None, original, modified)
    hunks = []
    for group in matcher.get_grouped_opcodes(context_lines):
        old_start = group[0][1]
        old_end = group[-1][2]
        new_start = group[0][3]
        new_end = group[-1][4]
        hunks.append(
            Hunk(
                old_block=original[old_start:old_end],
                new_block=modified[new_start:new_end],
                old_range=(old_start + 1, old_end),
                new_range=(new_start + 1, new_end),
            )
        )
    return hunks


def _find_block_indexes(haystack: Sequence[str], needle: Sequence[str]) -> list[int]:
    if not needle or len(needle) > len(haystack):
        return []
    length = len(needle)
    return [index for index in range(len(haystack) - length + 1) if haystack[index : index + length] == list(needle)]


def _contains_block(haystack: Sequence[str], needle: Sequence[str]) -> bool:
    return bool(_find_block_indexes(haystack, needle))


def _extract_marked_block(text: str, start_marker: str, end_marker: str) -> str:
    start = text.find(start_marker)
    if start < 0:
        raise RuntimeError(f"start marker not found: {start_marker}")
    end = text.find(end_marker, start)
    if end < 0:
        raise RuntimeError(f"end marker not found: {end_marker}")
    line_end = text.find("\n", end)
    if line_end < 0:
        line_end = len(text)
    return text[start:line_end + 1]


def _is_relative_to(path: Path, parent: Path) -> bool:
    try:
        path.relative_to(parent)
        return True
    except ValueError:
        return False
