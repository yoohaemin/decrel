#!/usr/bin/env python3
"""Require application Scala examples to participate in mdoc compilation."""
from pathlib import Path
import re
import sys


def unchecked_scala_lines(text, readme=False):
    fence = None
    for number, line in enumerate(text.splitlines(), 1):
        if fence:
            if re.fullmatch(r" {0,3}" + re.escape(fence[0]) + "{" + str(fence[1]) + r",}[ \t]*", line):
                fence = None
            continue
        opening = re.match(r"^ {0,3}(`{3,}|~{3,})(.*)$", line)
        if not opening:
            continue
        delimiter, info = opening.groups()
        if delimiter[0] == "`" and "`" in info:
            continue
        fence = (delimiter[0], len(delimiter))
        words = info.split()
        if words and words[0] == "scala":
            if readme or not any(word == "mdoc" or word.startswith("mdoc:") for word in words[1:]):
                yield number


def main():
    root = Path(__file__).resolve().parent.parent
    errors = []
    for path in [root / "README.md", *sorted((root / "mdoc/docs").rglob("*.md"))]:
        for number in unchecked_scala_lines(path.read_text(), path == root / "README.md"):
            errors.append(f"{path.relative_to(root)}:{number}: move Scala examples into an mdoc fence in mdoc/docs")
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print("All Scala documentation examples participate in mdoc compilation.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
