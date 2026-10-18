#!/usr/bin/env python3
"""Prints the CHANGELOG.md section for a version (## <version> ... up to the next ##). Fails if there is none."""
import re
import sys
from pathlib import Path

def notes(version: str, text: str) -> str:
    lines, out, inside = text.splitlines(), [], False
    for line in lines:
        m = re.match(r"^##\s+\[?v?([0-9][^\]\s]*)\]?", line)
        if m:
            if inside:
                break
            inside = m.group(1) == version
            continue
        if inside:
            out.append(line)
    body = "\n".join(out).strip()
    if not body:
        raise SystemExit(f"CHANGELOG.md has no section '## {version}'")
    return body

if __name__ == "__main__":
    root = Path(__file__).resolve().parent.parent
    print(notes(sys.argv[1], (root / "CHANGELOG.md").read_text(encoding="utf-8")))
