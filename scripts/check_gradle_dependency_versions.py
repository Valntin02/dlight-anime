#!/usr/bin/env python3
"""Fail when one Maven module is declared with multiple version expressions."""

from __future__ import annotations

import re
import subprocess
from collections import defaultdict
from pathlib import Path


COORDINATE = re.compile(
    r'''["']([A-Za-z0-9_.-]+):([A-Za-z0-9_.-]+):([^\s"']+)["']'''
)


def tracked_gradle_files() -> list[Path]:
    result = subprocess.run(
        ["git", "ls-files", "*.gradle"],
        check=True,
        text=True,
        capture_output=True,
    )
    return [Path(line) for line in result.stdout.splitlines() if line]


def main() -> int:
    declarations: dict[str, set[str]] = defaultdict(set)
    for path in tracked_gradle_files():
        text = path.read_text(encoding="utf-8")
        for group, artifact, version in COORDINATE.findall(text):
            declarations[f"{group}:{artifact}"].add(version)

    conflicts = {
        module: sorted(versions)
        for module, versions in declarations.items()
        if len(versions) > 1
    }
    if not conflicts:
        print("Gradle dependency declarations use one version expression per module.")
        return 0

    print("Conflicting Gradle dependency declarations:")
    for module, versions in sorted(conflicts.items()):
        print(f"- {module}: {', '.join(versions)}")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
