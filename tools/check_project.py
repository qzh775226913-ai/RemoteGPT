#!/usr/bin/env python3
"""Check current documentation, requirement references and source ownership budgets."""
import json
from pathlib import Path
import re
import sys
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]


def document_links(path, root):
    errors = []
    # Code examples aren't actual Markdown links.
    content = re.sub(r"```[^\n]*\n[\s\S]*?```", "", path.read_text())
    for target in re.findall(r"!?\[[^\]]*\]\(([^)]+)\)", content):
        url = urlsplit(target.strip().strip("<>"))
        if url.scheme or url.netloc or not url.path:
            continue
        resolved = (path.parent / unquote(url.path)).resolve()
        if not resolved.is_relative_to(root.resolve()) or not resolved.exists():
            errors.append(f"{path.relative_to(root)}: unresolved local link {target}")
    return errors


def check(root):
    errors = []
    registry = json.loads((root / "tools/project_structure.json").read_text())
    base = root / "app/src/main/java/app/remotessh"
    actual = {p.stem: p for p in base.glob("*.java")}
    owners = {}
    for module, names in registry["modules"].items():
        for name in names:
            if name in owners:
                errors.append(f"{name}: registered in both {owners[name]} and {module}")
            owners[name] = module
    for name in sorted(actual.keys() - owners.keys()):
        errors.append(f"{name}: missing module registration")
    for name in sorted(owners.keys() - actual.keys()):
        errors.append(f"{name}: registered source does not exist")
    for name, path in actual.items():
        budget = registry["line_budgets"].get(name, registry["default_java_line_budget"])
        count = len(path.read_text().splitlines())
        if count > budget:
            errors.append(f"{name}: {count} lines exceed {budget}; extract a responsibility before growing this file")
    for name, budget in registry["python_asset_line_budgets"].items():
        path = root / "app/src/main/assets" / name
        if not path.exists() or len(path.read_text().splitlines()) > budget:
            errors.append(f"{name}: missing or exceeds {budget} lines")
    docs = list(root.glob("*.md")) + [p for p in (root / "docs").rglob("*.md") if "history" not in p.relative_to(root / "docs").parts]
    for path in docs:
        errors.extend(document_links(path, root))
    requirements = (root / "docs/REQUIREMENTS.md").read_text()
    rows = re.findall(r"^\|\s*(R-\d+)\s*\|([^\n]+)", requirements, re.M)
    ids = [ident for ident, _ in rows]
    if not ids or len(ids) != len(set(ids)):
        errors.append("Requirements must have nonempty unique stable R-xx IDs")
    for ident, row in rows:
        if not re.search(r"\]\([^)]*(?:Test\.java|test_[^/)]*\.py)\)", row):
            errors.append(f"{ident}: no linked automated regression entry; document an explicit manual-only exception first")
    build = (root / "app/build.gradle").read_text()
    version = re.search(r"versionName\s+'([^']+)'", build).group(1)
    code = re.search(r"versionCode\s+(\d+)", build).group(1)
    expected = f"{version} / versionCode {code}"
    for path in [root / "README.md", root / "docs/REQUIREMENTS.md"]:
        if expected not in path.read_text():
            errors.append(f"{path.relative_to(root)}: current version must match {expected}")
    return errors


def main():
    errors = check(ROOT)
    if errors:
        print("\n".join(errors), file=sys.stderr)
        raise SystemExit(1)
    print("Verified current docs, requirement/test links, module ownership and file budgets")


if __name__ == "__main__":
    main()
