"""Read-only APK inventory and comparison; does not establish APK safety."""
import argparse
import hashlib
import json
import zipfile

def inventory(path):
    with zipfile.ZipFile(path) as apk:
        entries = {}
        for info in apk.infolist():
            if info.is_dir():
                continue
            if info.filename in entries:
                raise ValueError("Duplicate ZIP entry: " + info.filename)
            digest = hashlib.sha256()
            with apk.open(info) as stream:
                for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                    digest.update(chunk)
            entries[info.filename] = {"sha256": digest.hexdigest(), "size": info.file_size}
    return entries

def compare(old, new):
    return {
        "added": sorted(new.keys() - old.keys()),
        "removed": sorted(old.keys() - new.keys()),
        "changed": sorted(k for k in old.keys() & new.keys() if old[k] != new[k]),
        "manifest_changed": old.get("AndroidManifest.xml") != new.get("AndroidManifest.xml"),
    }

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk")
    parser.add_argument("--baseline")
    args = parser.parse_args()
    current = inventory(args.apk)
    result = {"entries": current}
    if args.baseline:
        result["comparison"] = compare(inventory(args.baseline), current)
    print(json.dumps(result, indent=2, sort_keys=True))

if __name__ == "__main__":
    main()
