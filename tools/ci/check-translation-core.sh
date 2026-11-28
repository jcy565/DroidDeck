#!/usr/bin/env bash
# Optional host-only check; never substitutes for the required Android test suite.
set -euo pipefail
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
source="$root/app/src/main/java/com/droiddeck/launcher/files"
for tool in kotlinc java; do
  command -v "$tool" >/dev/null || { echo "Required host tool missing: $tool" >&2; exit 1; }
done
work="$(mktemp -d)"
trap 'rm -rf -- "$work"' EXIT
names=(GameConfigPreview GamePatchAuthor GamePatchStore ImageAssetDiscovery ImageDraftStore ImageQueueStore ImageTextRegions ImageTranslationInput LocalGameAdvisor TranslationDiscovery TranslationDraftStore TranslationJson TranslationLimits TranslationQueueStore TranslationReuse TranslationTable TranslationWorklist)
files=()
for name in "${names[@]}"; do files+=("$source/$name.kt"); done
kotlinc "${files[@]}" "$root/tools/tests/kotlin/TranslationCoreCheck.kt" -include-runtime -d "$work/check.jar"
java -Djava.awt.headless=true -jar "$work/check.jar"
