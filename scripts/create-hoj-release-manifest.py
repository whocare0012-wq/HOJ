"""Create a local release identity manifest; this script never uploads or deploys."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[1]


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT)


def digest(path):
    value = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(chunk)
    return value.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--frontend", type=Path, required=True, help="Built frontend directory")
    parser.add_argument("--backend", type=Path, required=True, help="Built backend JAR")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if not (args.frontend / "index.html").is_file() or not args.backend.is_file():
        parser.error("Both built artifacts must exist")
    names = git("ls-files", "-z", "--cached", "--others", "--exclude-standard", "--",
                "hoj-springboot", "hoj-vue", "sqlAndsetting", "scripts", ".github").decode().split("\0")
    source = hashlib.sha256()
    source_count = 0
    for name in sorted(set(names)):
        path = ROOT / name
        if not name or not path.is_file() or any(part in {"target", "dist", "node_modules", ".playwright-cli"} for part in path.parts):
            continue
        source.update(name.encode() + b"\0" + digest(path).encode() + b"\n")
        source_count += 1
    # Paths and secret values from source/config files are not included in the manifest.
    report = {
        "schemaVersion": 1, "createdAt": datetime.now(timezone.utc).isoformat(),
        "sourceRevision": git("rev-parse", "HEAD").decode().strip(),
        "workingTreeDirty": bool(git("status", "--porcelain")),
        "sourceTreeSha256": source.hexdigest(), "sourceFileCount": source_count,
        "backend": {"name": args.backend.name, "sha256": digest(args.backend)},
        "frontend": {p.relative_to(args.frontend).as_posix(): digest(p)
                     for p in sorted(args.frontend.rglob("*")) if p.is_file()},
        "scope": "local-only",
        "provenance": "Source snapshot taken when manifest was generated; run immediately after building."
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    # Never overwrite an existing release record.
    with args.output.open("x", encoding="utf-8") as stream:
        json.dump(report, stream, indent=2, ensure_ascii=False)
        stream.write("\n")
    print(str(args.output.resolve()))


if __name__ == "__main__":
    main()
