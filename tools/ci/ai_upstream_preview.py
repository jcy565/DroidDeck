"""Inspect upstream updates without modifying the working tree, index or refs."""
import argparse
import json
import re
import subprocess
from pathlib import Path


def git(repo, *args, allowed=(0,)):
    result = subprocess.run(
        ["git", "-C", str(repo), *args], capture_output=True, check=False
    )
    if result.returncode not in allowed:
        raise RuntimeError(result.stderr.decode("utf-8", "replace"))
    return result


def commit(repo, value):
    if not re.fullmatch(r"[0-9a-f]{40}", value):
        raise ValueError("Use a pinned full SHA-1 commit")
    return git(repo, "rev-parse", "--verify", value + "^{commit}").stdout.decode().strip()


def paths(raw):
    return {p.decode("utf-8", "surrogateescape") for p in raw.split(b"\0") if p}


def changed(repo, base, head):
    return paths(git(repo, "diff", "--no-ext-diff", "--no-renames", "--name-only",
                     "-z", base, head, "--").stdout)


def sensitive(path):
    lower = path.lower()
    return (lower.startswith((".github/", "keystore/", "gradle/", "tools/release/"))
            or any(part in lower for part in (
                "androidmanifest.xml", "build.gradle", "settings.gradle",
                "cargo.lock", "cargo.toml", "gradle.properties", "network_security",
                "signer", "signing", "permission", "appfilesprovider", "gamesaves",
                "gamestorage", "sessionprefs", "settingsreset")))


def preview(repo, ai, upstream):
    ai, upstream = commit(repo, ai), commit(repo, upstream)
    bases = git(repo, "merge-base", "--all", ai, upstream).stdout.decode().splitlines()
    if len(bases) != 1:
        raise ValueError("Exactly one common ancestor is required")
    base = bases[0]
    ours, theirs = changed(repo, base, ai), changed(repo, base, upstream)
    result = git(repo, "merge-tree", "--write-tree", "--name-only", "--no-messages",
                 "-z", ai, upstream, allowed=(0, 1))
    tree, _, rest = result.stdout.partition(b"\0")
    tree = tree.decode("ascii").strip()
    if not re.fullmatch(r"[0-9a-f]{40}", tree):
        raise ValueError("Unexpected merge-tree output")
    conflicts = paths(rest)
    if result.returncode == 0 and conflicts:
        raise ValueError("Unexpected paths in clean merge output")
    if result.returncode == 1 and not conflicts:
        raise ValueError("Conflict result lacks conflict paths")
    review = sorted(p for p in ours | theirs if sensitive(p))
    return {
        "schema": 1, "ai": ai, "upstream": upstream, "merge_base": base,
        "ai_changed": sorted(ours), "upstream_changed": sorted(theirs),
        "overlapping": sorted(ours & theirs), "conflicts": sorted(conflicts),
        "sensitive_review": review,
        "merge_status": "conflicts" if conflicts else "clean",
        "next": "manual_review" if conflicts or review else "regression_required",
        "release_approved": False,
        "scope": "Git preview only; no commit, push, build, installation or approval."
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path("."))
    parser.add_argument("--ai", required=True)
    parser.add_argument("--upstream", required=True)
    args = parser.parse_args()
    report = preview(args.repo, args.ai, args.upstream)
    print(json.dumps(report, ensure_ascii=True, indent=2))
    return 1 if report["conflicts"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
