"""Print a read-only D01 package/version snapshot for the current Android user."""

import argparse
import json
import os
import re
import subprocess
import sys

from adb_device import DeviceResolutionError, resolve_serial

OFFICIAL = "com.droiddeck.launcher"
INDEPENDENT = "com.droiddeck.launcher.ai"


def read_snapshot(shell):
    user = shell("am", "get-current-user").strip()
    if not re.fullmatch(r"[0-9]+", user):
        raise ValueError("Could not determine current Android user")

    def versions(prefix):
        output = shell("pm", "list", "packages", "--show-versioncode", "--user", user, prefix)
        result = {}
        for line in output.splitlines():
            match = re.fullmatch(r"package:([\w.]+)\s+versionCode:([0-9]+)", line.strip())
            if not match or match[1] in result:
                raise ValueError("Unexpected package manager response")
            result[match[1]] = int(match[2])
        return result

    if "android" not in versions("android"):
        raise ValueError("Could not verify package manager access")
    packages = versions(OFFICIAL)
    if shell("am", "get-current-user").strip() != user:
        raise ValueError("Android user changed during query")
    return {"android_user": int(user), "packages": {
        name: packages.get(name) for name in (OFFICIAL, INDEPENDENT)
    }}


def valid_version(value):
    return type(value) is int and 0 <= value <= 2**63 - 1


def validate_snapshot(value):
    if not isinstance(value, dict) or set(value) != {"android_user", "packages"}:
        raise ValueError("Invalid snapshot schema")
    if not valid_version(value["android_user"]):
        raise ValueError("Invalid Android user")
    packages = value["packages"]
    if not isinstance(packages, dict) or set(packages) != {OFFICIAL, INDEPENDENT}:
        raise ValueError("Invalid package snapshot")
    if any(v is not None and not valid_version(v) for v in packages.values()):
        raise ValueError("Invalid package version")


def compare_snapshots(before, after, expected_version):
    """Assess package evidence only, not device identity, app signature or save integrity."""
    validate_snapshot(before)
    validate_snapshot(after)
    if not valid_version(expected_version):
        raise ValueError("Invalid expected version")
    old, new = before["packages"], after["packages"]
    checks = {
        "same_android_user": before["android_user"] == after["android_user"],
        "official_present_before": old[OFFICIAL] is not None,
        "official_version_unchanged": old[OFFICIAL] is not None and old[OFFICIAL] == new[OFFICIAL],
        "independent_absent_before": old[INDEPENDENT] is None,
        "independent_expected_version_after": new[INDEPENDENT] == expected_version,
    }
    return {
        "package_checks_passed": all(checks.values()),
        "checks": checks,
        "device_identity_verified": False,
        "save_integrity_verified": False,
        "signature_verified": False,
        "d01_passed": False,
    }


def load_snapshot(path):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError("Duplicate snapshot field")
            result[key] = value
        return result

    with open(path, "rb") as stream:
        data = stream.read(8193)
    if len(data) > 8192:
        raise ValueError("Snapshot too large")
    value = json.loads(data.decode("utf-8"), object_pairs_hook=unique)
    validate_snapshot(value)
    return value


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default=os.environ.get("ADB", "adb"))
    parser.add_argument("--serial")
    parser.add_argument("--compare", nargs=2, metavar=("BEFORE", "AFTER"),
                        help="Compare saved snapshots offline; no ADB commands")
    parser.add_argument("--expected-version", type=int)
    args = parser.parse_args()
    if (args.compare is None) != (args.expected_version is None):
        parser.error("--compare and --expected-version must be used together")
    if args.compare and args.serial is not None:
        parser.error("--serial is only used for live snapshots")
    try:
        if args.compare:
            result = compare_snapshots(load_snapshot(args.compare[0]),
                                       load_snapshot(args.compare[1]), args.expected_version)
            print(json.dumps(result, indent=2))
            return 0 if result["package_checks_passed"] else 1
        serial = resolve_serial(args.adb, args.serial)

        def shell(*command):
            result = subprocess.run([args.adb, "-s", serial, "shell", *command],
                                    capture_output=True, text=True, timeout=15)
            if result.returncode or result.stderr.strip():
                raise ValueError("ADB package query failed")
            return result.stdout

        print(json.dumps(read_snapshot(shell), indent=2))
    except (DeviceResolutionError, OSError, subprocess.TimeoutExpired, ValueError):
        print("Package evidence unavailable. Check input snapshots or the selected device and Android user.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
