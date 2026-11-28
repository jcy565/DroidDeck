#!/usr/bin/env bash
set -euo pipefail
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf -- "$work"' EXIT
src="$root/app/src/main/java/com/droiddeck/launcher"
kotlinc "$src/hud/GameFrames.kt" "$src/hud/HudFrames.kt" \
  "$src/compatibility/TrialFrameSamples.kt" "$src/compatibility/OptimizationComparison.kt" \
  "$root/tools/tests/kotlin/PerformanceCoreCheck.kt" -include-runtime -d "$work/check.jar"
java -jar "$work/check.jar"
