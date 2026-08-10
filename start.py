from __future__ import annotations

import subprocess
import sys
from pathlib import Path


def main() -> int:
    repo_root = Path(__file__).resolve().parent
    start_script = repo_root / "scripts" / "start.ps1"
    print("[interface-platform] Starting application at http://localhost:8080 ...")
    try:
        result = subprocess.run(
            [
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                str(start_script),
                *sys.argv[1:],
            ],
            cwd=repo_root,
            check=False,
        )
        return result.returncode
    except KeyboardInterrupt:
        return 130


if __name__ == "__main__":
    sys.exit(main())
