#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
sync_tree.py — умная синхронизация дерева исходников Android с сохранением
локальных изменений (микро-патчей) и менеджер снимков.

Режимы работы:
  (без флагов)   синхронизация по списку файлов из bakFiles/original
  -s, --smart    глубокий сканер всех git-проектов дерева (по манифесту repo)
  -d, --diff     (только с -s) сделать только слепок изменений, без repo sync
  -c, --change   интерактивный менеджер снимков и патчей
  -f, --forcesync  полный force sync с принудительным откатом изменений,
                   без последующего применения патчей (слепок делается ВСЕГДА:
                   без -s — по bakFiles/original, с -s — глубокий сканер);
                   если по original/ ничего не найдено — предложит глубокий -s скан
  -r, --revert   ТОЛЬКО откат локальных изменений к HEAD, без repo sync
                   (слепок делается ВСЕГДА как страховка, патчи обратно НЕ
                   накатываются; без -s — по bakFiles/original, с -s — глубокий скан)
   -e, --except   UI управления исключениями smart-сканера
  -j, --jobs N   количество потоков repo sync
  -y, --yes      неинтерактивный режим (автоподтверждение всех вопросов)

Структура bakFiles/:
  original/                 чистые HEAD-копии отслеживаемых файлов (*.bp/*.mk -> +.bak)
  smart_exceptions.json     пользовательские исключения smart-сканера
  snapshots/<ts>/
    modified/               бэкап ТЕКУЩИХ изменённых файлов (*.bp/*.mk -> +.bak)
    original/               чистые HEAD-копии на момент слепка (*.bp/*.mk -> +.bak)
    patches/<rel>.patch     unified diff (a/<rel> -> b/<rel>)
    new_files/              untracked-файлы, временно убранные из дерева
    original_prev/          прежнее содержимое bakFiles/original (при smart-очистке)
    manifest.json           описание снимка и результаты
"""

from __future__ import annotations

import os
import sys
import json
import shutil
import signal
import argparse
import subprocess
import tempfile
import threading
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from datetime import datetime
from typing import Optional, Tuple, List, Dict, Set

# ============================================================
# 1. Аварийное завершение (SIGINT / SIGTERM)
# ============================================================
ACTIVE_TEMP_PATHS: Set[Path] = set()
_res_lock = threading.Lock()


def emergency_cleanup(signum, frame):
    with _res_lock:
        for tmp in list(ACTIVE_TEMP_PATHS):
            shutil.rmtree(tmp, ignore_errors=True)
    os._exit(130)


signal.signal(signal.SIGINT, emergency_cleanup)
signal.signal(signal.SIGTERM, emergency_cleanup)

# ============================================================
# 2. Зависимости (rich с самоустановкой)
# ============================================================
try:
    from rich.console import Console
    from rich.progress import Progress, SpinnerColumn, TextColumn, BarColumn
    from rich.panel import Panel
    from rich.table import Table
    from rich.syntax import Syntax
    from rich.prompt import Confirm, Prompt
except ImportError:
    print("[*] Установка интерфейсной библиотеки 'rich'...")
    subprocess.check_call([sys.executable, "-m", "pip", "install", "rich", "--quiet"])
    from rich.console import Console
    from rich.progress import Progress, SpinnerColumn, TextColumn, BarColumn
    from rich.panel import Panel
    from rich.table import Table
    from rich.syntax import Syntax
    from rich.prompt import Confirm, Prompt

console = Console()

# ============================================================
# 3. Пути, константы, базовые хелперы
# ============================================================
ROOT_DIR = Path(".").resolve()
BAK_ROOT = Path("bakFiles")
ORIG_DIR = BAK_ROOT / "original"
SNAP_ROOT = BAK_ROOT / "snapshots"
NEW_FILES_LIST = BAK_ROOT / "newFiles.txt"
EXCEPTIONS_FILE = BAK_ROOT / "smart_exceptions.json"

DEFAULT_EXCEPTIONS = ["out", "bakFiles", ".repo"]
SKIP_SUFFIXES = (".rej", ".orig", ".patch", ".bak", ".pyc")
STORAGE_BAK_SUFFIXES = (".bp", ".mk")  # файлы с такими расширениями хранятся с +.bak


def get_target_uid_gid() -> Tuple[int, int]:
    sudo_uid = os.environ.get("SUDO_UID")
    sudo_gid = os.environ.get("SUDO_GID")
    if sudo_uid and sudo_gid:
        return int(sudo_uid), int(sudo_gid)
    return os.getuid(), os.getgid()


def fix_permissions(path: Path, uid: int, gid: int):
    if uid == 0 or not path.exists():
        return
    try:
        subprocess.run(["chown", "-R", f"{uid}:{gid}", str(path)],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        subprocess.run(["chmod", "-R", "u+rwX,go+rX", str(path)],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    except Exception:
        pass


def to_storage_name(rel: str) -> str:
    """Имя файла внутри bakFiles-хранилищ (bp/mk прячем от сборщика суффиксом .bak)."""
    return rel + ".bak" if rel.endswith(STORAGE_BAK_SUFFIXES) else rel


def from_storage_name(stored: str) -> str:
    """Обратное преобразование: убрать .bak только у bp/mk."""
    if stored.endswith(".bak") and stored[:-4].endswith(STORAGE_BAK_SUFFIXES):
        return stored[:-4]
    return stored


def norm_rel(p) -> str:
    return str(p).replace("\\", "/").strip("/")


# ============================================================
# 4. Git-хелперы
# ============================================================
def get_git_info(file_path: Path) -> Tuple[Optional[Path], Optional[str]]:
    """Возвращает (корень git-репозитория, путь файла внутри него)."""
    resolved = file_path.resolve()
    curr = resolved if resolved.is_dir() else resolved.parent
    while curr != curr.parent:
        if (curr / ".git").exists():
            try:
                return curr, norm_rel(resolved.relative_to(curr))
            except ValueError:
                return curr, None
        curr = curr.parent
    return None, None


def git_show_head(repo_root: Path, rel_in_repo: str) -> Optional[bytes]:
    """Чистое содержимое файла из HEAD, не трогая рабочую копию."""
    res = subprocess.run(["git", "show", f"HEAD:{rel_in_repo}"],
                         cwd=repo_root, capture_output=True)
    return res.stdout if res.returncode == 0 else None


def git_head_has_file(repo_root: Path, rel_in_repo: str) -> bool:
    res = subprocess.run(["git", "cat-file", "-e", f"HEAD:{rel_in_repo}"],
                         cwd=repo_root, capture_output=True)
    return res.returncode == 0


def git_checkout_head(repo_root: Path, rel_in_repo: str) -> bool:
    """Откат файла к состоянию HEAD."""
    res = subprocess.run(["git", "checkout", "HEAD", "--", rel_in_repo],
                         cwd=repo_root, capture_output=True)
    return res.returncode == 0


def git_is_modified(repo_root: Path, rel_in_repo: str) -> bool:
    """True, если файл отличается от HEAD (рабочая копия или индекс)."""
    res = subprocess.run(["git", "diff", "--quiet", "HEAD", "--", rel_in_repo],
                         cwd=repo_root, capture_output=True)
    return res.returncode == 1


# ============================================================
# 5. Исключения smart-сканера
# ============================================================
def load_exceptions() -> List[str]:
    if EXCEPTIONS_FILE.exists():
        try:
            data = json.loads(EXCEPTIONS_FILE.read_text(encoding="utf-8"))
            if isinstance(data, list):
                return sorted(set(norm_rel(e) for e in data if str(e).strip()))
        except Exception:
            pass
    return []


def save_exceptions(exceptions: List[str]):
    BAK_ROOT.mkdir(parents=True, exist_ok=True)
    clean = sorted(set(norm_rel(e) for e in exceptions if str(e).strip()))
    EXCEPTIONS_FILE.write_text(json.dumps(clean, indent=2, ensure_ascii=False), encoding="utf-8")


def is_path_excluded(rel_path_str: str, exceptions: List[str]) -> bool:
    norm_path = norm_rel(rel_path_str)
    if norm_path.endswith(SKIP_SUFFIXES):
        return True
    for exc in list(DEFAULT_EXCEPTIONS) + list(exceptions):
        e = norm_rel(exc)
        if e and (norm_path == e or norm_path.startswith(e + "/")):
            return True
    return False


def safe_confirm(prompt_text: str, default: bool = True) -> bool:
    try:
        return Confirm.ask(prompt_text, default=default)
    except Exception:
        try:
            ans = input(f"{prompt_text} [{'Y/n' if default else 'y/N'}]: ").strip().lower()
            if not ans:
                return default
            return ans in ("y", "yes", "д", "да", "1")
        except Exception:
            return default


def manage_exceptions_ui(uid: int, gid: int):
    while True:
        exceptions = load_exceptions()
        console.clear()
        console.print(Panel(
            "[bold cyan]⚙️ Управление исключениями Smart-сканера (-s/--smart)[/bold cyan]\n"
            "[dim]Указанные каталоги и ВСЕ их подкаталоги игнорируются при поиске изменений.\n"
            "Системные исключения (всегда): out, bakFiles, .repo[/dim]", expand=False))

        table = Table(title="Текущие правила исключения", show_header=True)
        table.add_column("№", style="cyan", width=5)
        table.add_column("Игнорируемый путь (от корня дерева)", style="bold white")
        if exceptions:
            for idx, exc in enumerate(exceptions, 1):
                table.add_row(str(idx), exc)
        else:
            table.add_row("-", "[italic yellow]Пользовательских правил нет[/italic yellow]")
        console.print(table)

        console.print("\n[bold yellow]Действия:[/bold yellow]")
        console.print("  [bold green]1[/bold green] — Добавить каталог")
        console.print("  [bold red]2[/bold red] — Удалить каталог")
        console.print("  [bold cyan]0[/bold cyan] — Сохранить и выйти\n")
        choice = Prompt.ask("Действие", choices=["1", "2", "0"], default="0")

        if choice == "1":
            new_path = norm_rel(Prompt.ask("\n[bold green]Путь к каталогу[/bold green]"))
            if new_path:
                if new_path not in exceptions:
                    exceptions.append(new_path)
                    save_exceptions(exceptions)
                    fix_permissions(EXCEPTIONS_FILE, uid, gid)
                    console.print(f"[bold green]✓ Добавлено:[/] {new_path}")
                else:
                    console.print("[yellow]Такой путь уже есть![/yellow]")
            Prompt.ask("\nEnter для продолжения...")
        elif choice == "2":
            if not exceptions:
                console.print("[yellow]Список пуст![/yellow]")
                Prompt.ask("\nEnter для продолжения...")
                continue
            idx_str = Prompt.ask("\n[bold red]Номер записи для удаления[/bold red]")
            if idx_str.isdigit() and 1 <= int(idx_str) <= len(exceptions):
                removed = exceptions.pop(int(idx_str) - 1)
                save_exceptions(exceptions)
                fix_permissions(EXCEPTIONS_FILE, uid, gid)
                console.print(f"[bold green]✓ Удалено:[/] {removed}")
            else:
                console.print("[red]Неверный номер![/red]")
            Prompt.ask("\nEnter для продолжения...")
        else:
            break


# ============================================================
# 6. Smart-сканер: поиск изменений по всем git-проектам дерева
# ============================================================
def list_repo_projects() -> Optional[List[str]]:
    """Список git-проектов дерева по манифесту repo (относительные пути)."""
    res = subprocess.run(["repo", "list", "-p"], cwd=ROOT_DIR,
                         capture_output=True, text=True)
    if res.returncode != 0:
        return None
    return [norm_rel(line) for line in res.stdout.splitlines() if line.strip()]


def walk_find_git_roots(exceptions: List[str]) -> List[str]:
    """Fallback: обход дерева в поиске .git (если repo list недоступен)."""
    roots = []
    for current_root, dirs, _files in os.walk(ROOT_DIR):
        curr = Path(current_root)
        try:
            rel = norm_rel(curr.relative_to(ROOT_DIR))
        except ValueError:
            rel = norm_rel(curr)
        if rel != "." and is_path_excluded(rel, exceptions):
            dirs[:] = []
            continue
        dirs[:] = [d for d in dirs if d != ".git" and not is_path_excluded(
            d if rel == "." else f"{rel}/{d}", exceptions)]
        if ".git" in os.listdir(current_root):
            roots.append("" if rel == "." else rel)
            dirs[:] = []  # вложенных проектов внутри проекта не ищем
    return roots


def parse_porcelain_z(data: bytes) -> Tuple[List[str], List[str], List[str]]:
    """Разбор `git status --porcelain=v1 -z` -> (modified, untracked, deleted)."""
    modified, untracked, deleted = [], [], []
    fields = data.split(b"\0")
    i = 0
    while i < len(fields):
        field = fields[i]
        i += 1
        if not field:
            continue
        xy = field[:2].decode("ascii", "replace")
        path = os.fsdecode(field[3:])
        if "R" in xy or "C" in xy:
            i += 1  # второе поле — старое имя файла
        if xy == "??" or "A" in xy or "R" in xy or "C" in xy:
            untracked.append(path)
        elif "D" in xy:
            deleted.append(path)
        elif any(c in xy for c in ("M", "T", "U")):
            modified.append(path)
    return modified, untracked, deleted


def scan_project(repo_rel: str, exceptions: List[str]):
    """git status одного проекта. Возвращает (repo_rel, modified, untracked, deleted)."""
    repo_abs = ROOT_DIR / repo_rel if repo_rel else ROOT_DIR
    if not (repo_abs / ".git").exists():
        return repo_rel, [], [], []
    res = subprocess.run(
        ["git", "status", "--porcelain=v1", "-z", "--untracked-files=normal"],
        cwd=repo_abs, capture_output=True)
    if res.returncode != 0:
        return repo_rel, [], [], []
    mod, untr, dele = parse_porcelain_z(res.stdout)

    def to_tree(p: str) -> str:
        return norm_rel(f"{repo_rel}/{p}" if repo_rel else p)

    mod = [to_tree(p) for p in mod if not is_path_excluded(to_tree(p), exceptions)]
    untr = [to_tree(p) for p in untr if not is_path_excluded(to_tree(p), exceptions)]
    dele = [to_tree(p) for p in dele if not is_path_excluded(to_tree(p), exceptions)]
    return repo_rel, mod, untr, dele


def smart_scan(exceptions: List[str]) -> Dict[str, Dict[str, List[str]]]:
    """
    Полное сканирование дерева. Возвращает привязку к git-каталогам:
    { repo_rel: {"modified": [...], "untracked": [...], "deleted": [...]} }
    (ключ "" — корневой проект, если он есть)
    """
    projects = list_repo_projects()
    if projects is None:
        console.print("[yellow]! repo list недоступен, использую обход файловой системы[/yellow]")
        projects = walk_find_git_roots(exceptions)

    projects = [p for p in projects if not is_path_excluded(p, exceptions)]

    result: Dict[str, Dict[str, List[str]]] = {}
    with ThreadPoolExecutor(max_workers=16) as pool:
        futures = [pool.submit(scan_project, p, exceptions) for p in projects]
        for fut in futures:
            repo_rel, mod, untr, dele = fut.result()
            if mod or untr or dele:
                entry = result.setdefault(repo_rel, {"modified": [], "untracked": [], "deleted": []})
                entry["modified"].extend(mod)
                entry["untracked"].extend(untr)
                entry["deleted"].extend(dele)

    for entry in result.values():
        for key in entry:
            entry[key] = sorted(set(entry[key]))
    return dict(sorted(result.items()))


def display_scan_binding(scan: Dict[str, Dict[str, List[str]]]):
    """Таблица привязки найденных файлов к их git-каталогам."""
    table = Table(title="📌 Привязка изменений к git-каталогам", show_header=True)
    table.add_column("Git-каталог", style="bold cyan")
    table.add_column("Изменено", style="yellow", justify="right")
    table.add_column("Новых", style="green", justify="right")
    table.add_column("Удалено", style="red", justify="right")
    table.add_column("Файлы", style="white")
    for repo, data in scan.items():
        files = data["modified"] + data["untracked"]
        shown = "\n".join(files[:8])
        if len(files) > 8:
            shown += f"\n... и ещё {len(files) - 8}"
        table.add_row(repo or "(корень дерева)",
                      str(len(data["modified"])),
                      str(len(data["untracked"])),
                      str(len(data["deleted"])),
                      shown)
    console.print(table)


# ============================================================
# 7. Снимок: бэкап изменённых, откат, оригиналы, патчи
# ============================================================
class SnapshotDirs:
    def __init__(self, ts: str):
        self.root = SNAP_ROOT / ts
        self.modified = self.root / "modified"
        self.original = self.root / "original"
        self.patches = self.root / "patches"
        self.new_files = self.root / "new_files"
        self.original_prev = self.root / "original_prev"
        self.manifest = self.root / "manifest.json"

    def mkdirs(self):
        for d in (self.modified, self.original, self.patches):
            d.mkdir(parents=True, exist_ok=True)


def make_patch(orig_file: Path, modified_file: Path, patch_path: Path, rel: str) -> bool:
    """Unified diff orig -> modified с метками a/<rel>, b/<rel>. True если патч непуст."""
    res = subprocess.run(
        ["diff", "-u", "--label", f"a/{rel}", "--label", f"b/{rel}",
         str(orig_file), str(modified_file)],
        capture_output=True)
    if res.returncode == 1 and res.stdout:
        patch_path.parent.mkdir(parents=True, exist_ok=True)
        patch_path.write_bytes(res.stdout)
        return True
    return False


def process_modified_file(rel: str, repo_abs: Path, snap: SnapshotDirs,
                          uid: int, gid: int) -> Tuple[str, str]:
    """
    Конвейер одного изменённого файла:
    бэкап текущего -> откат к HEAD -> копия оригинала -> генерация патча.
    """
    tree_file = ROOT_DIR / rel
    storage = to_storage_name(rel)
    snap_mod = snap.modified / storage
    snap_orig = snap.original / storage
    live_orig = ORIG_DIR / storage
    patch_path = snap.patches / f"{rel}.patch"

    for p in (snap_mod, snap_orig, live_orig):
        p.parent.mkdir(parents=True, exist_ok=True)

    if not tree_file.exists():
        return "missing", f"Файл отсутствует в дереве: {rel}"

    # 1. Бэкап ТЕКУЩЕГО (изменённого) файла
    shutil.copy2(tree_file, snap_mod)

    # 2. Откат изменений к HEAD
    rel_in_repo = norm_rel(tree_file.resolve().relative_to(repo_abs))
    if not git_checkout_head(repo_abs, rel_in_repo):
        head_data = git_show_head(repo_abs, rel_in_repo)
        if head_data is None:
            return "revert_failed", f"Не удалось откатить к HEAD: {rel}"
        tree_file.write_bytes(head_data)

    # 3. Чистый оригинал -> в снимок и в живое хранилище original/
    shutil.copy2(tree_file, snap_orig)
    shutil.copy2(tree_file, live_orig)
    fix_permissions(live_orig, uid, gid)

    # 4. Патч: оригинал vs сохранённая изменённая копия
    if make_patch(snap_orig, snap_mod, patch_path, rel):
        return "ok", f"Слепок готов: {rel}"
    return "nodiff", f"Отличий от HEAD не найдено: {rel}"


def clean_original_dir(snap: SnapshotDirs):
    """Smart-режим: очистка original/ с бэкапом прежнего содержимого."""
    if ORIG_DIR.exists() and any(ORIG_DIR.iterdir()):
        snap.original_prev.mkdir(parents=True, exist_ok=True)
        for item in ORIG_DIR.iterdir():
            shutil.move(str(item), str(snap.original_prev / item.name))
    ORIG_DIR.mkdir(parents=True, exist_ok=True)


def move_new_files_aside(new_files: List[str], snap: SnapshotDirs, uid: int, gid: int):
    """Временно убирает untracked-файлы из дерева в снимок."""
    if not new_files:
        return
    snap.new_files.mkdir(parents=True, exist_ok=True)
    for rel in new_files:
        src = ROOT_DIR / rel
        dest = snap.new_files / rel
        dest.parent.mkdir(parents=True, exist_ok=True)
        if src.is_dir():
            shutil.copytree(src, dest, dirs_exist_ok=True)
            shutil.rmtree(src, ignore_errors=True)
        elif src.exists():
            shutil.copy2(src, dest)
            src.unlink()


def restore_new_files(snap: SnapshotDirs, uid: int, gid: int) -> int:
    """Возвращает untracked-файлы из снимка в дерево."""
    if not snap.new_files.exists():
        return 0
    count = 0
    for src in snap.new_files.rglob("*"):
        if src.is_file():
            target = ROOT_DIR / src.relative_to(snap.new_files)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(src, target)
            fix_permissions(target, uid, gid)
            count += 1
    return count


def refresh_original_from_head(rel: str, uid: int, gid: int) -> bool:
    """Обновляет bakFiles/original содержимым файла из ТЕКУЩЕГО HEAD (после sync)."""
    repo_root, rel_in_repo = get_git_info(ROOT_DIR / rel)
    if not repo_root or not rel_in_repo:
        return False
    data = git_show_head(repo_root, rel_in_repo)
    if data is None:
        return False
    live_orig = ORIG_DIR / to_storage_name(rel)
    live_orig.parent.mkdir(parents=True, exist_ok=True)
    live_orig.write_bytes(data)
    fix_permissions(live_orig, uid, gid)
    return True


# ============================================================
# 8. Отказоустойчивый движок наложения патчей
# ============================================================
def _run_patch(args: List[str], target: Path, patch_bytes: bytes) -> subprocess.CompletedProcess:
    return subprocess.run(["patch"] + args + [str(target)],
                          input=patch_bytes, capture_output=True)


def apply_patch_smart(rel: str, patch_path: Path, orig_file: Path,
                      snap_mod: Optional[Path], uid: int, gid: int) -> Tuple[str, str]:
    """
    Порядок попыток:
      1) цель/патч существуют?
      2) изменения уже присутствуют? (reverse dry-run)
      3) patch -l --fuzz=3 (терпимость к пробелам), с предохранителем
      4) fallback: трёхсторонний git merge-file (дерево / оригинал / копия из снимка)
      5) failed с подробным отчётом (целевой файл не трогаем)
    """
    target = ROOT_DIR / rel
    if not target.exists():
        return "missing", f"Целевой файл не найден: {rel}"
    if not patch_path.exists() or patch_path.stat().st_size == 0:
        return "skipped", f"Патч пуст или отсутствует: {rel}"

    patch_bytes = patch_path.read_bytes()
    repo_root, rel_in_repo = get_git_info(target)

    # --- 2. Уже применён? ---
    chk = _run_patch(["-p0", "-R", "--dry-run", "-s", "-l", "--fuzz=3"], target, patch_bytes)
    if chk.returncode == 0:
        return "already_applied", f"Изменения уже присутствуют (пропуск): {rel}"

    # --- 3. Прямое наложение с предохранителем ---
    tmp_dir = Path(tempfile.mkdtemp(prefix="sync_tree_patch_"))
    ACTIVE_TEMP_PATHS.add(tmp_dir)
    try:
        backup = tmp_dir / "target.orig"
        shutil.copy2(target, backup)
        res = _run_patch(["-p0", "-N", "-s", "-l", "--fuzz=3", "--no-backup-if-mismatch"],
                         target, patch_bytes)
        if res.returncode == 0:
            fix_permissions(target, uid, gid)
            for junk in (Path(f"{target}.rej"), Path(f"{target}.orig")):
                junk.unlink(missing_ok=True)
            return "applied", f"Патч применён: {rel}"
        # Неудача -> возвращаем целевой файл в исходное состояние
        shutil.copy2(backup, target)
        for junk in (Path(f"{target}.rej"), Path(f"{target}.orig")):
            junk.unlink(missing_ok=True)

        # --- 4. Fallback: 3-way merge ---
        if orig_file.exists() and snap_mod and snap_mod.exists():
            merge = subprocess.run(
                ["git", "merge-file", "-p",
                 "-L", f"current:{rel}", "-L", f"base:{rel}", "-L", f"local-changes:{rel}",
                 str(target), str(orig_file), str(snap_mod)],
                capture_output=True)
            if merge.returncode == 0 and merge.stdout:
                target.write_bytes(merge.stdout)
                fix_permissions(target, uid, gid)
                return "merged", f"Применено 3-way слиянием (контекст съехал): {rel}"
            return "failed", (f"Конфликт 3-way слияния: {rel} "
                              f"(нужно ручное разрешение; патч: {patch_path})")
        return "failed", f"Патч не лёг и нет данных для 3-way merge: {rel}"
    finally:
        ACTIVE_TEMP_PATHS.discard(tmp_dir)
        shutil.rmtree(tmp_dir, ignore_errors=True)


def apply_patches_from_dir(patches_dir: Path, snap: Optional[SnapshotDirs],
                           uid: int, gid: int,
                           only: Optional[List[str]] = None) -> Dict[str, List[Tuple[str, str]]]:
    """Применяет все *.patch из каталога. Возвращает {status: [(rel, msg)]}."""
    results: Dict[str, List[Tuple[str, str]]] = {}
    patch_files = sorted(patches_dir.rglob("*.patch")) if patches_dir.exists() else []
    if not patch_files:
        console.print("[yellow]Патчей для применения нет.[/yellow]")
        return results

    for patch_file in patch_files:
        rel = norm_rel(patch_file.relative_to(patches_dir))[:-6]  # убрать ".patch"
        if only and rel not in only:
            continue
        storage = to_storage_name(rel)
        if snap is not None:
            orig_file = snap.original / storage
            if not orig_file.exists():
                orig_file = snap.original / rel  # legacy-имена без .bak
            if not orig_file.exists():
                orig_file = ORIG_DIR / storage
            if not orig_file.exists():
                orig_file = ORIG_DIR / rel
            snap_mod = snap.modified / storage
            if not snap_mod.exists():
                snap_mod = snap.modified / rel  # legacy-имена без .bak
            if not snap_mod.exists():
                snap_mod = None
        else:
            orig_file = ORIG_DIR / storage
            snap_mod = None

        status, msg = apply_patch_smart(rel, patch_file, orig_file, snap_mod, uid, gid)
        results.setdefault(status, []).append((rel, msg))
        color = "bold green" if status in ("applied", "already_applied", "merged") else \
                "yellow" if status in ("skipped", "missing") else "bold red"
        console.print(f"  [{color}]{msg}[/]")

        # После успешного применения обновляем живой оригинал до нового HEAD
        if status in ("applied", "merged", "already_applied"):
            refresh_original_from_head(rel, uid, gid)
    return results


def print_apply_summary(results: Dict[str, List[Tuple[str, str]]]):
    table = Table(title="📊 Итоги применения патчей", show_header=True)
    table.add_column("Статус", style="bold")
    table.add_column("Файлов", justify="right")
    table.add_column("Список", style="dim")
    labels = {
        "applied": ("✅ Применены", "green"),
        "already_applied": ("✓ Уже были применены", "green"),
        "merged": ("🔀 Применены 3-way merge", "cyan"),
        "skipped": ("⏭ Пропущены (пустой патч)", "yellow"),
        "missing": ("❓ Целевой файл отсутствует", "yellow"),
        "failed": ("❌ Не применены", "red"),
    }
    total_fail = 0
    for status, items in results.items():
        label, color = labels.get(status, (status, "white"))
        names = "\n".join(rel for rel, _ in items[:6])
        if len(items) > 6:
            names += f"\n... и ещё {len(items) - 6}"
        table.add_row(f"[{color}]{label}[/]", str(len(items)), names)
        if status == "failed":
            total_fail = len(items)
    console.print(table)
    return total_fail


# ============================================================
# 9. repo sync
# ============================================================
def run_repo_sync(jobs: int, force: bool = False) -> int:
    cmd = ["repo", "sync", "-c", "--force-sync", "--no-clone-bundle", "--no-tags"]
    if not force:
        cmd.append("--prune")
    cmd.append(f"-j{jobs}")
    console.print(f"\n[bold cyan]>>> {' '.join(cmd)}[/bold cyan]")
    return subprocess.run(cmd, cwd=ROOT_DIR).returncode


# ============================================================
# 10. Интерактивный менеджер снимков (--change / -c)
# ============================================================
def _snapshot_patch_files(snap_dir: Path) -> List[Path]:
    """Патчи снимка: новый формат (patches/) и legacy (diff/)."""
    for sub in ("patches", "diff"):
        d = snap_dir / sub
        if d.exists():
            files = sorted(d.rglob("*.patch"))
            if files:
                return files
    return []


def _snapshot_dirs_compat(snap_dir: Path) -> SnapshotDirs:
    """SnapshotDirs с подстановкой legacy-имён (copy/ -> modified/, diff/ -> patches/)."""
    snap = SnapshotDirs(snap_dir.name)
    snap.root = snap_dir
    snap.modified = snap_dir / "modified" if (snap_dir / "modified").exists() else snap_dir / "copy"
    snap.original = snap_dir / "original"
    snap.patches = snap_dir / "patches" if (snap_dir / "patches").exists() else snap_dir / "diff"
    snap.new_files = snap_dir / "new_files" if (snap_dir / "new_files").exists() else snap_dir / "new_files_temp"
    return snap


def change_mode_ui(uid: int, gid: int):
    console.print(Panel("[bold cyan]🗂 Менеджер снимков и патчей (--change)[/bold cyan]\n"
                        "[dim]Выбор снимка → просмотр/применение отдельных патчей или всех сразу.[/dim]",
                        expand=False))
    if not SNAP_ROOT.exists():
        console.print("[bold red]❌ Каталог снимков не найден (bakFiles/snapshots).[/bold red]")
        return

    while True:
        snaps = sorted([d for d in SNAP_ROOT.iterdir() if d.is_dir()],
                       key=lambda d: d.name, reverse=True)
        if not snaps:
            console.print("[bold red]❌ Снимки не найдены.[/bold red]")
            return

        table = Table(title="Доступные снимки", show_header=True)
        table.add_column("№", style="cyan", width=5)
        table.add_column("Снимок", style="bold white")
        table.add_column("Патчей", justify="right", style="yellow")
        for idx, s in enumerate(snaps, 1):
            table.add_row(str(idx), s.name, str(len(_snapshot_patch_files(s))))
        console.print(table)

        choice = Prompt.ask("Номер снимка (q — выход)", default="q")
        if choice.lower() == "q":
            return
        if not choice.isdigit() or not (1 <= int(choice) <= len(snaps)):
            console.print("[red]Неверный номер![/red]")
            continue

        snap_dir = snaps[int(choice) - 1]
        snap = _snapshot_dirs_compat(snap_dir)
        patch_files = _snapshot_patch_files(snap_dir)
        if not patch_files:
            console.print("[yellow]В этом снимке нет патчей.[/yellow]")
            continue

        while True:
            table = Table(title=f"Файлы снимка {snap_dir.name}", show_header=True)
            table.add_column("№", style="cyan", width=5)
            table.add_column("Целевой файл", style="white")
            table.add_column("Размер", justify="right", style="dim")
            rels = []
            for idx, pf in enumerate(patch_files, 1):
                rel = norm_rel(pf.relative_to(snap.patches))[:-6]
                rels.append(rel)
                table.add_row(str(idx), rel, f"{pf.stat().st_size} B")
            console.print(table)
            console.print("[bold yellow]Команды:[/bold yellow] номер — просмотр/применение, "
                          "[bold green]a[/bold green] — накатить ВСЕ патчи снимка, q — назад")
            cmd = Prompt.ask("Команда", default="q")

            if cmd.lower() == "q":
                break
            if cmd.lower() == "a":
                if safe_confirm(f"Применить ВСЕ {len(patch_files)} патчей из {snap_dir.name}?", True):
                    results = apply_patches_from_dir(snap.patches, snap, uid, gid)
                    print_apply_summary(results)
                    Prompt.ask("\nEnter для продолжения...")
                continue
            if not cmd.isdigit() or not (1 <= int(cmd) <= len(patch_files)):
                console.print("[red]Неверный номер![/red]")
                continue

            pf = patch_files[int(cmd) - 1]
            rel = rels[int(cmd) - 1]
            syntax = Syntax(pf.read_text(errors="replace"), "diff",
                            theme="monokai", line_numbers=True)
            console.print(Panel(syntax, title=f"Патч: {rel}", border_style="cyan", expand=True))
            if safe_confirm(f"Применить этот патч к [cyan]{rel}[/cyan]?", True):
                results = apply_patches_from_dir(snap.patches, snap, uid, gid, only=[rel])
                print_apply_summary(results)
                Prompt.ask("\nEnter для продолжения...")


# ============================================================
# 11. Сборка списков изменений
# ============================================================
def gather_from_original(exceptions: List[str]) -> Dict[str, Dict[str, List[str]]]:
    """
    Обычный режим: кандидаты = файлы из bakFiles/original.
    Локальная модификация подтверждается через git diff HEAD (защита от
    апстрим-изменений: если файл изменил только репо — это не локальный патч).
    """
    result: Dict[str, Dict[str, List[str]]] = {}
    if not ORIG_DIR.exists():
        return result
    for stored in sorted(ORIG_DIR.rglob("*")):
        if not stored.is_file():
            continue
        rel = from_storage_name(norm_rel(stored.relative_to(ORIG_DIR)))
        if is_path_excluded(rel, exceptions):
            continue
        tree_file = ROOT_DIR / rel
        if not tree_file.exists():
            continue
        repo_root, rel_in_repo = get_git_info(tree_file)
        if not repo_root or not rel_in_repo:
            continue
        if not git_head_has_file(repo_root, rel_in_repo):
            continue
        if not git_is_modified(repo_root, rel_in_repo):
            continue  # в дереве чисто — локальных изменений нет
        repo_rel = norm_rel(repo_root.relative_to(ROOT_DIR)) \
            if repo_root != ROOT_DIR else ""
        entry = result.setdefault(repo_rel, {"modified": [], "untracked": [], "deleted": []})
        entry["modified"].append(rel)

    # Совместимость: дополнительные новые файлы из newFiles.txt
    if NEW_FILES_LIST.exists():
        for line in NEW_FILES_LIST.read_text().splitlines():
            line = norm_rel(line)
            if not line or line.startswith("#"):
                continue
            p = ROOT_DIR / line
            if p.exists() and not is_path_excluded(line, exceptions):
                repo_root, _ = get_git_info(p)
                repo_rel = norm_rel(repo_root.relative_to(ROOT_DIR)) if repo_root and repo_root != ROOT_DIR else ""
                entry = result.setdefault(repo_rel, {"modified": [], "untracked": [], "deleted": []})
                if line not in entry["untracked"]:
                    entry["untracked"].append(line)
    return dict(sorted(result.items()))


def flatten_modified(scan: Dict[str, Dict[str, List[str]]]) -> List[str]:
    out = []
    for data in scan.values():
        out.extend(data["modified"])
    return sorted(set(out))


def flatten_untracked(scan: Dict[str, Dict[str, List[str]]]) -> List[str]:
    out = []
    for data in scan.values():
        out.extend(data["untracked"])
    return sorted(set(out))


def write_manifest(snap: SnapshotDirs, mode: str,
                   scan: Dict[str, Dict[str, List[str]]],
                   processed: Dict[str, str], extra: Optional[dict] = None):
    manifest = {
        "timestamp": snap.root.name,
        "created": datetime.now().isoformat(),
        "mode": mode,
        "repos": scan,
        "processed": processed,
    }
    if extra:
        manifest.update(extra)
    snap.manifest.write_text(json.dumps(manifest, indent=2, ensure_ascii=False),
                             encoding="utf-8")


# ============================================================
# 12. Главный конвейер
# ============================================================
def snapshot_pipeline(scan: Dict[str, Dict[str, List[str]]], snap: SnapshotDirs,
                      uid: int, gid: int, clean_original: bool,
                      interactive: bool) -> Tuple[List[str], Dict[str, str]]:
    """
    Подтверждение -> бэкап -> откат -> оригиналы -> патчи -> убрать новые файлы.
    Возвращает (список новых файлов, {rel: статус обработки}).
    """
    modified = flatten_modified(scan)
    untracked = flatten_untracked(scan)
    deleted = flatten_modified({r: {"modified": d["deleted"]} for r, d in scan.items()})

    if deleted:
        console.print("[yellow]⚠ Обнаружены удалённые из дерева файлы (пропускаю, бэкап невозможен):[/yellow]")
        for d in deleted:
            console.print(f"  [dim]{d}[/dim]")

    if interactive:
        if modified and not safe_confirm(
                f"Сохранить и подготовить патчи для ВСЕХ {len(modified)} изменённых файлов?", True):
            keep = []
            for f in modified:
                if safe_confirm(f"  Включить [cyan]{f}[/cyan]?", True):
                    keep.append(f)
            modified = keep
            scan = {r: {"modified": [f for f in d["modified"] if f in keep],
                        "untracked": d["untracked"], "deleted": d["deleted"]}
                    for r, d in scan.items()}
        if untracked and not safe_confirm(
                f"Временно убрать и сохранить ВСЕ {len(untracked)} новых/untracked файлов?", True):
            keep_u = []
            for f in untracked:
                if safe_confirm(f"  Включить [cyan]{f}[/cyan]?", True):
                    keep_u.append(f)
            untracked = keep_u

    if clean_original:
        console.print("[yellow]--> Очистка original/ (прежнее содержимое — в снимке original_prev/)[/yellow]")
        clean_original_dir(snap)
    ORIG_DIR.mkdir(parents=True, exist_ok=True)

    processed: Dict[str, str] = {}
    for repo_rel, data in scan.items():
        repo_abs = ROOT_DIR / repo_rel if repo_rel else ROOT_DIR
        for rel in data["modified"]:
            status, msg = process_modified_file(rel, repo_abs, snap, uid, gid)
            processed[rel] = status
            color = "green" if status == "ok" else "yellow" if status == "nodiff" else "red"
            console.print(f"  [{color}]{msg}[/]")

    if untracked:
        console.print(f"[yellow]--> Временное изъятие {len(untracked)} новых/untracked файлов[/yellow]")
        move_new_files_aside(untracked, snap, uid, gid)

    return untracked, processed


def restore_modified_from_snapshot(snap: SnapshotDirs, uid: int, gid: int) -> int:
    """Возврат изменённых файлов из снимка обратно в дерево (режим -d, откат при сбое sync)."""
    count = 0
    if not snap.modified.exists():
        return 0
    for src in snap.modified.rglob("*"):
        if not src.is_file():
            continue
        rel = from_storage_name(norm_rel(src.relative_to(snap.modified)))
        target = ROOT_DIR / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, target)
        fix_permissions(target, uid, gid)
        count += 1
    return count


# ============================================================
# 13. Точка входа
# ============================================================
def main():
    parser = argparse.ArgumentParser(
        description="Умная синхронизация дерева Android с сохранением локальных патчей")
    parser.add_argument("-s", "--smart", action="store_true",
                        help="Глубокий умный сканер git-репозиториев для поиска изменений")
    parser.add_argument("-d", "--diff", action="store_true",
                        help="(с -s) Подготовить ТОЛЬКО слепок изменений, без repo sync")
    parser.add_argument("-c", "--change", action="store_true",
                        help="Диалог выбора снимка: просмотр и применение патчей")
    parser.add_argument("-f", "--forcesync", action="store_true",
                        help="Полный force sync с откатом изменений, без применения патчей "
                             "(слепок делается всегда; без -s — по bakFiles/original, с -s — глубокий скан)")
    parser.add_argument("-r", "--revert", action="store_true",
                        help="Только откатить локальные изменения к HEAD (со слепком), без repo sync. "
                             "Без -s — по bakFiles/original, с -s — глубокий скан")
    parser.add_argument("-e", "--except", dest="manage_exceptions", action="store_true",
                        help="Открыть UI управления исключениями smart-сканера")
    parser.add_argument("-j", "--jobs", type=int, default=os.cpu_count() or 8,
                        help="Количество потоков repo sync")
    parser.add_argument("-y", "--yes", action="store_true",
                        help="Неинтерактивный режим (автоподтверждение)")
    args = parser.parse_args()

    uid, gid = get_target_uid_gid()
    interactive = not args.yes

    console.print(Panel("[bold cyan]🔄 Умная синхронизация дерева исходников Android[/bold cyan]",
                        expand=False))

    # --- UI исключений ---
    if args.manage_exceptions:
        manage_exceptions_ui(uid, gid)
        if not (args.smart or args.forcesync or args.change or args.revert):
            return

    # --- Менеджер снимков ---
    if args.change:
        change_mode_ui(uid, gid)
        return

    if args.diff and not args.smart:
        console.print("[bold red]❌ Флаг -d/--diff работает только вместе с -s/--smart![/bold red]")
        sys.exit(2)

    if args.revert and args.forcesync:
        console.print("[bold red]❌ Флаги -r/--revert и -f/--forcesync несовместимы "
                      "(revert — без sync, forcesync — с sync).[/bold red]")
        sys.exit(2)
    if args.revert and args.diff:
        console.print("[bold red]❌ Флаги -r/--revert и -d/--diff несовместимы "
                      "(diff возвращает файлы в дерево, revert — откатывает).[/bold red]")
        sys.exit(2)

    # --- FORCE SYNC идёт через общий конвейер, чтобы изменения были
    # --- найдены (по original/ или глубоким сканом -s), сохранены в слепок,
    # --- откачены к HEAD и НЕ накатывались обратно после sync.
    # --- Раннего "голого" repo sync без слепка здесь больше нет.
    is_force = args.forcesync
    is_revert = args.revert
    if is_force:
        console.print("[bold yellow]! Режим FORCE SYNC: изменения будут сохранены "
                      "в слепок, откачены к HEAD и НЕ применены обратно[/bold yellow]")
    if is_revert:
        console.print("[bold yellow]! Режим REVERT: откат изменений к HEAD "
                      "без repo sync (слепок сохранится, патчи НЕ вернутся)[/bold yellow]")

    # --- Сбор изменений ---
    exceptions = load_exceptions()
    PROGRESS_COLUMNS = [SpinnerColumn(), TextColumn("{task.description}"),
                        BarColumn(bar_width=30), TextColumn("[progress.percentage]{task.percentage:>3.0f}%")]

    with Progress(*PROGRESS_COLUMNS, console=console, transient=True) as progress:
        task = progress.add_task("[cyan]Поиск изменений в дереве...", total=None)
        if args.smart:
            scan = smart_scan(exceptions)
        else:
            scan = gather_from_original(exceptions)

    modified = flatten_modified(scan)
    untracked = flatten_untracked(scan)

    if not scan or (not modified and not untracked):
        # -f / -r без -s смотрят только bakFiles/original и могут пропустить
        # правки вне отслеживаемого списка — предлагаем глубокий скан.
        if (is_force or is_revert) and not args.smart:
            console.print("[yellow]ℹ По bakFiles/original изменений не найдено, "
                          "но это не гарантирует чистоту дерева.[/yellow]")
            do_smart = False
            if interactive:
                action = "REVERT" if is_revert else "FORCE SYNC"
                do_smart = safe_confirm(
                    f"Запустить глубокий smart-скан всех git-проектов перед {action}?", True)
            if do_smart:
                with Progress(*PROGRESS_COLUMNS, console=console, transient=True) as progress2:
                    task2 = progress2.add_task("[cyan]Глубокий поиск изменений (-s)...", total=None)
                    scan = smart_scan(exceptions)
                modified = flatten_modified(scan)
                untracked = flatten_untracked(scan)
                if scan and (modified or untracked):
                    console.print("[bold yellow]--> Глубокий скан нашёл изменения, "
                                  "перехожу к слепку и откату.[/bold yellow]")
                    # таблицу покажет общий display ниже, минуя выход
                else:
                    console.print("[green]✓ Глубокий скан тоже ничего не нашёл.[/green]")
                    if is_revert:
                        console.print("[green]Откат не требуется — дерево чистое.[/green]")
                        return
                    if args.diff:
                        console.print("[yellow]Слепок пуст — выход без sync.[/yellow]")
                        return
                    sys.exit(run_repo_sync(args.jobs, force=args.forcesync))
            else:
                if not ORIG_DIR.exists():
                    console.print("[yellow]ℹ bakFiles/original отсутствует — отслеживаемых файлов нет.\n"
                                  "  Запустите с -s для первичного сканирования.[/yellow]")
                else:
                    console.print("[green]✓ Локальных изменений не найдено.[/green]")
                if is_revert:
                    console.print("[green]Откат не требуется — дерево чистое.[/green]")
                    return
                if args.diff:
                    console.print("[yellow]Слепок пуст — выход без sync.[/yellow]")
                    return
                sys.exit(run_repo_sync(args.jobs, force=args.forcesync))
        elif is_revert:
            console.print("[green]✓ Локальных изменений не найдено — откат не требуется.[/green]")
            return
        else:
            if not args.smart and not ORIG_DIR.exists():
                console.print("[yellow]ℹ bakFiles/original отсутствует — отслеживаемых файлов нет.\n"
                              "  Запустите с -s для первичного сканирования.[/yellow]")
            else:
                console.print("[green]✓ Локальных изменений не найдено.[/green]")
            if args.diff:
                console.print("[yellow]Слепок пуст — выход без sync.[/yellow]")
                return
            sys.exit(run_repo_sync(args.jobs, force=args.forcesync))

    display_scan_binding(scan)

    # Финальное подтверждение для -f / -r: откат необратим в дереве
    # (остаётся только слепок), патчи обратно не накатываются.
    if is_force and interactive:
        if not safe_confirm(
                f"FORCE SYNC откатит {len(modified)} изм. + уберёт {len(untracked)} новых "
                f"файлов (слепок сохранится, патчи НЕ вернутся). Продолжить?", False):
            console.print("[yellow]Отменено.[/yellow]")
            return
    if is_revert and interactive:
        if not safe_confirm(
                f"REVERT откатит {len(modified)} изм. + уберёт {len(untracked)} новых "
                f"файлов БЕЗ синхронизации (слепок сохранится, патчи НЕ вернутся). Продолжить?", False):
            console.print("[yellow]Отменено.[/yellow]")
            return

    # --- Создание снимка и подготовка ---
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    snap = SnapshotDirs(ts)
    snap.mkdirs()
    console.print(f"\n[bold yellow]--> Снимок состояния:[/] {snap.root}")

    mode = "smart" if args.smart else "original"
    # Для -f / -r уже было глобальное подтверждение выше — пофайловые вопросы
    # отключаем, чтобы ВСЕ найденные изменения гарантированно ушли в слепок и откат.
    untracked, processed = snapshot_pipeline(
        scan, snap, uid, gid,
        clean_original=args.smart,
        interactive=(interactive and not is_force and not is_revert))

    # --- Режим -r: только откат, без sync и без возврата файлов ---
    if is_revert:
        # snapshot_pipeline уже откатил modified к HEAD и убрал untracked в слепок.
        # Дополнительно восстанавливаем удалённые из дерева файлы к HEAD.
        reverted_deleted = 0
        for repo_rel, data in scan.items():
            repo_abs = ROOT_DIR / repo_rel if repo_rel else ROOT_DIR
            for rel in data.get("deleted", []):
                target = ROOT_DIR / rel
                if target.exists():
                    continue
                try:
                    rel_in_repo = norm_rel(target.resolve().relative_to(repo_abs.resolve()))
                except ValueError:
                    continue
                if git_checkout_head(repo_abs, rel_in_repo):
                    reverted_deleted += 1
                else:
                    head_data = git_show_head(repo_abs, rel_in_repo)
                    if head_data is not None:
                        target.parent.mkdir(parents=True, exist_ok=True)
                        target.write_bytes(head_data)
                        reverted_deleted += 1
        if reverted_deleted:
            console.print(f"[green]✓ Восстановлено удалённых файлов: {reverted_deleted}[/green]")
        n_mod = sum(1 for s in processed.values() if s == "ok")
        n_untracked = len(untracked)
        write_manifest(snap, mode + "+revert", scan, processed,
                       {"reverted_modified": n_mod,
                        "removed_untracked": n_untracked,
                        "reverted_deleted": reverted_deleted})
        fix_permissions(BAK_ROOT, uid, gid)
        console.print(f"\n[bold green]✨ Откат завершён (без sync):[/] {snap.root}\n"
                      f"  Откачено изменённых: {n_mod}, убрано новых: {n_untracked}, "
                      f"восстановлено удалённых: {reverted_deleted}.\n"
                      f"  Всё сохранено в слепке; вернуть можно через -c.")
        return

    # --- Режим -d: только слепок, возвращаем изменения в дерево ---
    if args.diff:
        restored = restore_modified_from_snapshot(snap, uid, gid)
        restored += restore_new_files(snap, uid, gid)
        write_manifest(snap, mode + "+diff", scan, processed,
                       {"restored_to_tree": restored})
        fix_permissions(BAK_ROOT, uid, gid)
        console.print(f"\n[bold green]✨ Слепок готов:[/] {snap.root}\n"
                      f"  Изменённые файлы возвращены в дерево ({restored} шт.). Sync не выполнялся.")
        return

    # --- repo sync ---
    sync_rc = run_repo_sync(args.jobs, force=args.forcesync)
    if sync_rc != 0:
        console.print("\n[bold red]❌ Сбой repo sync![/bold red]")
        if not interactive or safe_confirm("Вернуть изменения из снимка обратно в дерево?", True):
            restored = restore_modified_from_snapshot(snap, uid, gid)
            restored += restore_new_files(snap, uid, gid)
            console.print(f"[yellow]Восстановлено файлов: {restored}[/yellow]")
        write_manifest(snap, mode, scan, processed, {"sync_rc": sync_rc})
        sys.exit(1)

    # --- Возврат новых файлов (force sync их не трогает, но мы их убирали) ---
    restored_new = restore_new_files(snap, uid, gid)
    if restored_new:
        console.print(f"[green]✓ Возвращено новых файлов: {restored_new}[/green]")

    # --- Применение патчей (кроме --forcesync) ---
    apply_results: Dict[str, List[Tuple[str, str]]] = {}
    if args.forcesync:
        console.print("[bold yellow]! --forcesync: патчи НЕ применяются. "
                      f"Слепок сохранён: {snap.root} (применить можно через -c)[/bold yellow]")
    else:
        console.print("\n[bold yellow]--> Применение сохранённых патчей к обновлённому дереву...[/bold yellow]")
        apply_results = apply_patches_from_dir(snap.patches, snap, uid, gid)
        print_apply_summary(apply_results)

    write_manifest(snap, mode + ("+forcesync" if args.forcesync else ""), scan, processed,
                   {"sync_rc": sync_rc,
                    "apply": {k: [rel for rel, _ in v] for k, v in apply_results.items()}})
    fix_permissions(BAK_ROOT, uid, gid)

    failed = len(apply_results.get("failed", []))
    if failed == 0:
        console.print("\n[bold green]✨ Синхронизация завершена успешно![/bold green]\n")
    else:
        console.print(f"\n[bold red]⚠️ Готово, но {failed} патч(ей) не применились — "
                      "разберите конфликты вручную (см. вывод выше).[/bold red]\n")
    sys.exit(0 if failed == 0 else 1)


if __name__ == "__main__":
    main()
