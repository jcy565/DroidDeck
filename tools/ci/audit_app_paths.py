"""Report potential app identity and shared storage conflicts for manual review."""
import json
import re
import subprocess
from pathlib import Path

RULES = {
    "package_literal": r"com\.droiddeck\.(?:launcher|shortcuts)",
    "official_uri": r"droiddeck://|com\.droiddeck\.shortcuts\.RELAY",
    "private_path": r"/data/(?:data|user(?:_de)?)/",
    "shared_path": r"/sdcard(?:/|\b)|/storage/emulated/|/mnt/sdcard",
    "storage_api": r"getExternalStorageDirectory|getExternalFilesDir|externalFilesDir|Environment\.DIRECTORY_|getFilesDir|filesDir",
    "runtime_path": r"steamapps|compatdata|WINEPREFIX|STEAM_COMPAT_DATA_PATH",
}
EXTENSIONS = {".kt", ".java", ".xml", ".gradle", ".kts", ".sh", ".py", ".rs", ".c", ".cpp", ".h", ".json", ".toml", ".properties"}

def main():
    root = Path(subprocess.check_output(["git", "rev-parse", "--show-toplevel"], text=True).strip())
    tracked = subprocess.check_output(["git", "ls-files", "-z"], cwd=root).decode().split("\0")
    findings, skipped = [], []
    for name in tracked:
        path = root / name
        if not name or path.suffix not in EXTENSIONS or name == "tools/ci/audit_app_paths.py":
            continue
        if path.is_symlink() or not path.is_file() or path.stat().st_size > 2 * 1024 * 1024:
            skipped.append(name)
            continue
        try:
            lines = path.read_text(encoding="utf-8").splitlines()
        except UnicodeError:
            skipped.append(name)
            continue
        for number, line in enumerate(lines, 1):
            categories = [key for key, pattern in RULES.items() if re.search(pattern, line)]
            if categories:
                findings.append({"path": name, "line": number, "categories": categories})
    print(json.dumps({"findings": findings, "skipped": skipped}, indent=2))

if __name__ == "__main__":
    main()
