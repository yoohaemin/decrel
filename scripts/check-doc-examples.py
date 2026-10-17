#!/usr/bin/env python3
"""Require application Scala examples to participate in mdoc compilation."""
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parent.parent
errors = []
for path in [root / "README.md", *sorted((root / "mdoc/docs").rglob("*.md"))]:
    for number, line in enumerate(path.read_text().splitlines(), 1):
        if re.match(r"^\s*```scala(?:\s|$)", line):
            if path == root / "README.md" or not re.search(r"\bmdoc(?:[:\s]|$)", line):
                errors.append(f"{path.relative_to(root)}:{number}: move Scala examples into an mdoc fence in mdoc/docs")
if errors:
    print("\n".join(errors), file=sys.stderr)
    sys.exit(1)
print("All Scala documentation examples participate in mdoc compilation.")
