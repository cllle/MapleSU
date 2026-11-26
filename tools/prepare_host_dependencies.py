"""Prepare the pinned Maven JARs used by source-only host checks."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import tempfile
import urllib.request

MAVEN_BASE = "https://repo.maven.apache.org/maven2/"


def load_lock(path: Path) -> list[dict]:
    records = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(records, list) or not records:
        raise ValueError("HOST_JVM_LOCK_EMPTY")
    seen = set()
    for record in records:
        for key in ("group", "artifact", "version"):
            value = record.get(key, "")
            if not isinstance(value, str) or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.-]*", value):
                raise ValueError("HOST_JVM_LOCK_COORDINATE:" + key)
        coordinate = (record["group"], record["artifact"], record["version"])
        if coordinate in seen:
            raise ValueError("HOST_JVM_LOCK_DUPLICATE:" + ":".join(coordinate))
        seen.add(coordinate)
        if type(record.get("bytes")) is not int or record["bytes"] <= 0:
            raise ValueError("HOST_JVM_LOCK_SIZE:" + record["artifact"])
        if not isinstance(record.get("sha256"), str) or not re.fullmatch(r"[a-f0-9]{64}", record["sha256"]):
            raise ValueError("HOST_JVM_LOCK_SHA256:" + record["artifact"])
    return records


def verify(path: Path, record: dict) -> None:
    if path.stat().st_size != record["bytes"]:
        raise ValueError("HOST_JVM_DEPENDENCY_SIZE_MISMATCH:" + record["artifact"])
    if hashlib.sha256(path.read_bytes()).hexdigest() != record["sha256"]:
        raise ValueError("HOST_JVM_DEPENDENCY_SHA256_MISMATCH:" + record["artifact"])


def prepare(record: dict, home: Path, *, check_only: bool = False) -> bool:
    group, artifact, version = (record[key] for key in ("group", "artifact", "version"))
    directory = home / "caches/modules-2/files-2.1" / group / artifact / version
    filename = f"{artifact}-{version}.jar"
    target = directory / record["sha256"] / filename
    if any(path != target for path in directory.glob("*/*.jar")):
        raise ValueError("HOST_JVM_DEPENDENCY_UNEXPECTED_JAR:" + artifact)
    if target.exists():
        verify(target, record)
        return False
    if check_only:
        raise ValueError("HOST_JVM_DEPENDENCY_MISSING:" + artifact)
    relative = "/".join((group.replace(".", "/"), artifact, version, filename))
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(dir=target.parent, suffix=".part", delete=False) as stream:
            temporary = Path(stream.name)
            with urllib.request.urlopen(MAVEN_BASE + relative, timeout=60) as response:
                size = 0
                while chunk := response.read(1024 * 1024):
                    size += len(chunk)
                    if size > record["bytes"]:
                        raise ValueError("HOST_JVM_DEPENDENCY_SIZE_MISMATCH:" + artifact)
                    stream.write(chunk)
        verify(temporary, record)
        os.replace(temporary, target)
    finally:
        if temporary is not None and temporary.exists():
            temporary.unlink()
    return True


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--lock", type=Path, default=Path(__file__).with_name("host-jvm-dependencies.json"))
    parser.add_argument("--gradle-user-home", type=Path,
                        default=Path(os.environ["GRADLE_USER_HOME"]) if os.environ.get("GRADLE_USER_HOME") else None)
    parser.add_argument("--check", action="store_true", help="Verify all pinned JARs without network access")
    args = parser.parse_args(argv)
    if args.gradle_user_home is None:
        parser.error("Set GRADLE_USER_HOME or pass --gradle-user-home")
    try:
        records = load_lock(args.lock)
        downloaded = sum(prepare(record, args.gradle_user_home.resolve(), check_only=args.check) for record in records)
    except (OSError, ValueError, KeyError) as error:
        print("HOST_JVM_DEPENDENCY_PREPARATION_FAILED:" + str(error), file=sys.stderr)
        return 1
    print(json.dumps(dict(dependencies=len(records), downloaded=downloaded,
                          reused=len(records) - downloaded, totalBytes=sum(record["bytes"] for record in records),
                          lockSha256=hashlib.sha256(args.lock.read_bytes()).hexdigest()), sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
