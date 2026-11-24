"""Compare actual APK contents, permissions and signer reports with pinned 0.7.1."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

from apk_inventory import compare, inventory

OFFICIAL_HASH = "179ab8b4dc8f9223b70a1e4287c55a090b36ee376242186a60211100158462f1"
OFFICIAL_ID = "com.droiddeck.launcher"
ASSISTANT_ID = OFFICIAL_ID + ".ai"


def inspect(apk, aapt, apksigner):
    permissions = subprocess.check_output([aapt, "dump", "permissions", apk], text=True)
    signer = subprocess.check_output([apksigner, "verify", "--print-certs", apk], text=True)
    manifest = subprocess.check_output([aapt, "dump", "xmltree", apk, "AndroidManifest.xml"], text=True)
    application = manifest.split("E: application ", 1)[1]
    names = re.findall(r'android:(authorities|taskAffinity)\([^)]*\)="([^"]+)"', application)
    return {
        "authorities": sorted(v for k, v in names if k == "authorities"),
        "task_affinities": sorted(v for k, v in names if k == "taskAffinity"),
        "package": re.search(r"^package: (\S+)$", permissions, re.M)[1],
        "permissions": sorted(set(re.findall(r"^uses-permission[^:]*: name='([^']+)'", permissions, re.M))),
        "certificate_sha256": re.findall(r"^Signer #\d+ certificate SHA-256 digest: (\w+)$", signer, re.M),
    }


def normalized(permissions, package):
    return {s if s != package + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" else "<app>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" for s in permissions}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("official", type=Path)
    parser.add_argument("assistant", type=Path)
    parser.add_argument("--aapt", default="aapt")
    parser.add_argument("--apksigner", default="apksigner")
    args = parser.parse_args()
    with args.official.open("rb") as stream:
        if hashlib.file_digest(stream, "sha256").hexdigest() != OFFICIAL_HASH:
            parser.error("official 0.7.1 hash mismatch")
    old, new = [inspect(path, args.aapt, args.apksigner) for path in (args.official, args.assistant)]
    extra = sorted(normalized(new["permissions"], new["package"]) - normalized(old["permissions"], old["package"]))
    print(json.dumps(dict(official=old, assistant=new, added_permissions=extra,
                          changes=compare(inventory(args.official), inventory(args.assistant))), indent=2))
    if old["package"] != OFFICIAL_ID or new["package"] != ASSISTANT_ID or extra:
        raise SystemExit("Unexpected application ID or added permissions")
    for field in ("authorities", "task_affinities"):
        if set(old[field]) & set(new[field]):
            raise SystemExit("Shared official " + field)
    if not {ASSISTANT_ID + suffix for suffix in (".documents", ".logs", ".agent")} <= set(new["authorities"]):
        raise SystemExit("Assistant provider missing")
