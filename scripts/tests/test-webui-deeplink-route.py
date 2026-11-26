"""Run the production intent dispatcher with JVM platform and SDK-boundary fixtures."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/specgen"))
from specgen.product import cached_jar


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--source-root", type=Path, default=ROOT / "manager/src/main/java")
    parser.add_argument("--frontend-source", type=Path, default=ROOT / "manager/src/main/assets/liteweb/a/app.js")
    parser.add_argument("--manifest-source", type=Path, default=ROOT / "manager/src/main/AndroidManifest.xml")
    args = parser.parse_args()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=False)
    java = shutil.which("java")
    if not java:
        parser.error("Java 21 is required")
    dependencies = [cached_jar(*item) for item in [
        ("org.jetbrains.kotlin", "kotlin-stdlib", "2.4.20"),
        ("org.jetbrains", "annotations", "13.0"),
        ("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.11.0"),
        ("junit", "junit", "4.13.2"), ("org.hamcrest", "hamcrest-core", "1.3")]]
    compiler = cached_jar("org.jetbrains.kotlin", "kotlin-compiler-embeddable", "2.4.20")
    source = args.source_root.resolve()
    fixture = ROOT / "manager/src/test/resources/webui-deeplink-jvm"
    node = shutil.which("node")
    if not node:
        parser.error("Node.js is required to execute the real frontend function")
    links = out / "frontend-links.properties"
    frontend_source = args.frontend_source.resolve()
    manifest_source = args.manifest_source.resolve()
    frontend = subprocess.run([node, str(fixture / "export-liteweb-links.cjs"), str(links), str(frontend_source)],
                              capture_output=True, text=True, encoding="utf-8", timeout=30)
    (out / "frontend.log").write_text(frontend.stdout + frontend.stderr, encoding="utf-8")
    if frontend.returncode:
        raise SystemExit(frontend.returncode)
    names = [
        "com/maplesu/core/ui/navigation3/IntentDispatcher.kt",
        "com/maplesu/core/ui/navigation3/IntentManagerReadiness.kt",
        "com/maplesu/core/ui/navigation3/WebUiOpenPolicy.kt",
        "dev/maplesu/manager/modules/webui/ModuleWebUiPlan.kt"]
    sources = [source / name if (source / name).is_file() else ROOT / "manager/src/main/java" / name for name in names]
    sources += sorted(fixture.glob("fixtures/*.kt"))
    sources += [fixture / "WebUiDeepLinkRouteJvmTest.kt"]
    inputs = [{"path": str(p), "bytes": p.stat().st_size,
               "sha256": hashlib.sha256(p.read_bytes()).hexdigest()} for p in [
                   *sources, frontend_source, manifest_source, fixture / "export-liteweb-links.cjs", Path(__file__).resolve()]]
    (out / "INPUTS.json").write_text(json.dumps(inputs, indent=2) + "\n", encoding="utf-8")
    classpath = os.pathsep.join(map(str, dependencies))
    jar = out / "tests.jar"
    commands = [
        [java, "-Xmx768m", "-cp", os.pathsep.join(map(str, [compiler] + dependencies)),
         "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-nowarn",
         "-jvm-target", "21", "-classpath", classpath, "-d", str(jar), *map(str, sources)],
        [java, "-Dwebui.links=" + str(links), "-cp", str(jar) + os.pathsep + classpath, "org.junit.runner.JUnitCore",
         "com.maplesu.core.ui.navigation3.WebUiDeepLinkRouteJvmTest"]]
    attempts = []
    for name, command in zip(["compile", "tests"], commands):
        result = subprocess.run(command, cwd=ROOT / "manager", capture_output=True,
                                text=True, encoding="utf-8", errors="replace", timeout=120)
        log = result.stdout + result.stderr
        (out / (name + ".log")).write_text(log, encoding="utf-8")
        attempts.append({"name": name, "exitCode": result.returncode})
        print(log)
        if result.returncode:
            break
    unchanged = all(p["sha256"] == hashlib.sha256(Path(p["path"]).read_bytes()).hexdigest() for p in inputs)
    ns = "{http://schemas.android.com/apk/res/android}"
    app = ET.parse(manifest_source).getroot().find("application")
    main = next(activity for activity in app.findall("activity") if activity.get(ns + "name") == ".ui.MainActivity")
    hosts = []
    for entry in main.findall("intent-filter"):
        for data in entry.findall("data"):
            if data.get(ns + "scheme") == "ksu":
                categories = {c.get(ns + "name") for c in entry.findall("category")}
                if "android.intent.category.BROWSABLE" in categories:
                    hosts.append(data.get(ns + "host"))
                    if "android.intent.category.DEFAULT" not in categories:
                        hosts.append("missing-category")
    manifest_valid = hosts == ["webui"] and main.get(ns + "exported") == "true" and all(
        activity.get(ns + "exported") == "false" for activity in app.findall("activity")
        if activity.get(ns + "name") in {".ui.webui.WebUIActivity", ".ui.webui.ModuleWebUIActivity"})
    (out / "RESULTS.json").write_text(json.dumps(dict(
        passed=result.returncode == 0 and unchanged and manifest_valid, attempts=attempts,
        sourcesUnchanged=unchanged, manifestValid=manifest_valid, ksuBrowsableHosts=hosts,
        scope="Real frontend function to complete production dispatcher and route plan; browser Intent decoding, Android/Compose/SDK/protected-config boundary are JVM fixtures. Manifest is checked statically.",
        deviceTestsPerformed=False, apkBuilt=False, moduleScriptsExecuted=False), indent=2) + "\n", encoding="utf-8")
    raise SystemExit(result.returncode if unchanged and manifest_valid else 1)


if __name__ == "__main__":
    main()
