from __future__ import annotations

import subprocess
import sys
from pathlib import Path


def main() -> int:
    repo_root = Path(__file__).resolve().parent
    build_script = repo_root / "scripts" / "build.ps1"
    print("[interface-platform] Building frontend and backend...")
    result = subprocess.run(
        [
            "powershell.exe",
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            str(build_script),
        ],
        cwd=repo_root,
        check=False,
    )
    if result.returncode != 0:
        print("[interface-platform] Build failed. If port 8080 is already running this project, stop it before rebuilding.")
    return result.returncode


if __name__ == "__main__":
    sys.exit(main())
