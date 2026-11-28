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
    return {"android_user": int(user), "packages": {
        name: packages.get(name) for name in (OFFICIAL, INDEPENDENT)
    }}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default=os.environ.get("ADB", "adb"))
    parser.add_argument("--serial")
    args = parser.parse_args()
    try:
        serial = resolve_serial(args.adb, args.serial)

        def shell(*command):
            result = subprocess.run([args.adb, "-s", serial, "shell", *command],
                                    capture_output=True, text=True, timeout=15)
            if result.returncode or result.stderr.strip():
                raise ValueError("ADB package query failed")
            return result.stdout

        print(json.dumps(read_snapshot(shell), indent=2))
    except (DeviceResolutionError, OSError, subprocess.TimeoutExpired, ValueError):
        print("Package snapshot unavailable. Check the selected device and Android user.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
