#!/usr/bin/env python3
"""Run the project's offline-safe checks against synthetic backends.

Full verification owns its SSH fixture and fails on skipped Android tests.
It never starts Codex, accesses a real SSH account, or installs dependencies.
"""
import argparse
import json
import os
from pathlib import Path
import shutil
import signal
import socket
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def run(command):
    print("\nRunning: " + " ".join(map(str, command)), flush=True)
    subprocess.run(list(map(str, command)), cwd=ROOT, check=True)


def check_fixture_port(port=22228):
    # Never silently reuse a service whose identity and lifetime we don't own.
    with socket.socket() as probe:
        # Match the fixture: a recently closed TCP connection is not a live listener.
        probe.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            probe.bind(("127.0.0.1", port))
        except OSError as error:
            raise RuntimeError(f"Port {port} is occupied. Stop your test fixture before full verification.") from error


def start_fixture(log):
    check_fixture_port()
    process = subprocess.Popen(
        [sys.executable, "-u", "tools/ssh_fixture.py"], cwd=ROOT,
        stdout=log, stderr=log, start_new_session=True,
    )
    try:
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            if process.poll() is not None:
                raise RuntimeError("SSH fixture exited; inspect app/build/verification/ssh-fixture.log")
            # The fixture flushes this line only after bind/listen succeeds.
            if "SSH test fixture ready" in Path(log.name).read_text():
                return process
            time.sleep(.1)
        raise RuntimeError("SSH fixture did not become ready within 15 seconds")
    except BaseException:
        stop_fixture(process)
        raise


def stop_fixture(process):
    if process is None or process.poll() is not None:
        return
    process.terminate()  # Runs the fixture's cleanup of its own fake daemon processes.
    try:
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGKILL)
        process.wait(timeout=5)


def android_report(full):
    paths = sorted((ROOT / "app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"))
    if not paths:
        raise RuntimeError("Gradle did not produce Android test results")
    counts = {key: 0 for key in ("tests", "failures", "errors", "skipped")}
    for path in paths:
        suite = ET.parse(path).getroot()
        for key in counts:
            counts[key] += int(suite.get(key, "0"))
    if counts["tests"] == 0 or counts["failures"] or counts["errors"] or (full and counts["skipped"]):
        raise RuntimeError("Android verification is incomplete: " + json.dumps(counts))
    lint = ROOT / "app/build/reports/lint-results-debug.xml"
    if not lint.exists():
        raise RuntimeError("Gradle did not produce a Lint report")
    severities = {}
    for issue in ET.parse(lint).getroot().findall("issue"):
        level = issue.get("severity")
        severities[level] = severities.get(level, 0) + 1
    if severities.get("Error", 0) or severities.get("Fatal", 0):
        raise RuntimeError("Lint reports blocking issues")
    return {"android": counts, "lint": severities, "ssh_fixture": full}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--unit", action="store_true", help="Skip SSH integration; does not qualify as full acceptance")
    parser.add_argument("--python-only", action="store_true", help="Run Python and source checks without JDK/Android SDK")
    parser.add_argument("--offline", action="store_true", help="Ask Gradle to use cached dependencies")
    args = parser.parse_args()
    full = not args.unit and not args.python_only
    output = ROOT / "app/build/verification"
    output.mkdir(parents=True, exist_ok=True)
    summary = output / "summary.json"
    summary.unlink(missing_ok=True)  # Preflight failures must also invalidate previous success.
    if not args.python_only:
        java = Path(os.environ.get("JAVA_HOME", "")) / "bin/java"
        if not java.is_file() and shutil.which("java") is None:
            parser.error("Set JAVA_HOME to a JDK 17 installation; see docs/DEVELOPMENT.md")
        if not (os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or (ROOT / "local.properties").exists()):
            parser.error("Set ANDROID_HOME to your Android SDK; see docs/DEVELOPMENT.md")
    if full:
        # Diagnose before spending time on compilation.
        try:
            import paramiko  # noqa: F401
        except ImportError:
            parser.error("Install tools/requirements-dev.txt in your development environment")
    run([sys.executable, "tools/check_project.py"])
    run([sys.executable, "tools/generate_icons.py", "--check"])
    run([sys.executable, "-m", "unittest", "discover", "-s", "tools", "-p", "test_*.py"])
    if args.python_only:
        summary.write_text(json.dumps({"scope": "python-only", "full_acceptance": False}, indent=2) + "\n")
        return
    fixture = None
    try:
        with (output / "ssh-fixture.log").open("w") as log:
            if full:
                fixture = start_fixture(log)
            command = ["./gradlew", ":app:testDebugUnitTest", ":app:assembleDebug", ":app:lintDebug", "--console=plain"]
            # Test properties alone are not guaranteed to invalidate Gradle's cached test task.
            command += ["-PsshFixture"] if full else []
            command += ["--offline"] if args.offline else []
            command += ["--rerun-tasks"]
            run(command)
            report = android_report(full)
            report.update(scope="full" if full else "unit", full_acceptance=full)
    finally:
        stop_fixture(fixture)
    summary.write_text(json.dumps(report, indent=2) + "\n")
    print("\nVerification: " + json.dumps(report), flush=True)


def interrupted(_signal, _frame):
    raise KeyboardInterrupt()


if __name__ == "__main__":
    signal.signal(signal.SIGTERM, interrupted)
    try:
        main()
    except (RuntimeError, subprocess.CalledProcessError) as error:
        print("Verification failed: " + str(error), file=sys.stderr)
        sys.exit(1)
    except KeyboardInterrupt:
        sys.exit(130)
