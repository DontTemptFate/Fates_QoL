#!/usr/bin/env python3
"""Every /ofa sub-command in Commands.java must be listed in the README's Commands table."""
import re
import sys
from pathlib import Path

root = Path(__file__).resolve().parent.parent
src = (root / "src/main/java/io/github/donttemptfate/fatesofa/game/Commands.java").read_text(encoding="utf-8")
readme = (root / "README.md").read_text(encoding="utf-8")
section = readme.split("## Commands", 1)[1].split("\n## ", 1)[0]
words = sorted(set(re.findall(r'literal\("([a-z]+)"\)', src)) - {"ofa"})
missing = [w for w in words if not re.search(r"`[^`]*\b" + w + r"\b[^`]*`", section)]
if missing:
    sys.exit("README Commands table is missing: " + ", ".join(missing))
print("README lists all", len(words), "command words")
