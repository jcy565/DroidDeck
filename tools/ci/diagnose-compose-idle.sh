#!/usr/bin/env bash
# Diagnostic only: the original full-suite test outcome remains authoritative.
set -euo pipefail
: "${NDK_VERSION:?NDK version required}"
out=app/build/compose-idle-diagnostics
mkdir -p "$out"
tests=(
    'com.droiddeck.launcher.ui.HeadingLocaleTest.headingsFollowTheAppLanguageAndRecomposeWhenItChanges'
    'com.droiddeck.launcher.ui.ModeHeaderFocusTest.clickingSearchIconFocusesField'
)
printf 'test\texit_code\n' > "$out/summary.tsv"
for test in "${tests[@]}"; do
    name=${test##*.}
    dest="$out/$name"
    mkdir -p "$dest"
    # Full suite artifacts have already been uploaded. Do not mistake an old
    # XML report for the result of this isolated invocation.
    rm -rf app/build/test-results/testReleaseUnitTest app/build/reports/tests/testReleaseUnitTest
    code=0
    ./gradlew testReleaseUnitTest --tests "$test" --console=plain --info --stacktrace \
        -Drobolectric.logging=stderr -PndkVersion="$NDK_VERSION" 2>&1 | tee "$dest/run.log" || code=$?
    printf '%s\t%s\n' "$test" "$code" >> "$out/summary.tsv"
    if [ -d app/build/test-results/testReleaseUnitTest ]; then
        cp -a app/build/test-results/testReleaseUnitTest "$dest/results"
    fi
done
cat "$out/summary.tsv"
