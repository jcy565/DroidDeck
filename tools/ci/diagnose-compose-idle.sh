#!/usr/bin/env bash
# Diagnostic only: the original full-suite test outcome remains authoritative.
set -euo pipefail
: "${NDK_VERSION:?NDK version required}"
out=app/build/compose-idle-diagnostics
mkdir -p "$out"
tests=(
    'com.droiddeck.launcher.ui.ComponentDownloadsDrawerTest.controllerPicksNightlyOnItsTabThenDownloadsItFromTheRow'
    'com.droiddeck.launcher.ui.ComponentsPageProfileTest.aNewNavigationTargetReplacesThePreviousProfile'
    'com.droiddeck.launcher.ui.FlathubFocusTest.openingAnAppReleasesTheCoveredNativeSearchField'
    'com.droiddeck.launcher.ui.FocusGlideWindowsTest.dropdownItemsShareTheirOwnWindowGlide'
    'com.droiddeck.launcher.ui.GameEnvironmentEditorTest.backgroundSelectionCannotRetargetAnOpenVariableEdit'
    'com.droiddeck.launcher.ui.GlassesCalibrationControlsTest.startRequiresInstructionsAndDoneRecentersAfterSuccessfulCapture'
    'com.droiddeck.launcher.ui.GlassesPanelTest.ultraControlsDisappearOnDisconnectAndForOtherGlassesWithSavedPreferences'
    'com.droiddeck.launcher.ui.GlassesPanelTest.spatialSteamVrIncludesHeadTrackingAndRestoresTheSavedVirtualScreenSwitch'
    'com.droiddeck.launcher.ui.HeadingLocaleTest.headingsFollowTheAppLanguageAndRecomposeWhenItChanges'
    'com.droiddeck.launcher.ui.ModeHeaderFocusTest.clickingSearchIconFocusesField'
    'com.droiddeck.launcher.ui.ModeHeaderFocusTest.reenteringCompositionWithNonEmptyQueryDoesNotStealFocus'
)
run_case() {
    local test="$1" dest="$2" summary="$3" code=0
    shift 3
    local filters=()
    for selector in "$@"; do
        filters+=(--tests "$selector")
    done
    mkdir -p "$dest"
    # A previous diagnostic invocation must not supply this case's XML.
    rm -rf "$dest/results"
    # Full suite artifacts have already been uploaded. Do not mistake an old
    # XML report for the result of this isolated invocation.
    rm -rf app/build/test-results/testReleaseUnitTest app/build/reports/tests/testReleaseUnitTest
    ./gradlew testReleaseUnitTest "${filters[@]}" --console=plain --info --stacktrace \
        -Drobolectric.logging=stderr -PndkVersion="$NDK_VERSION" 2>&1 | tee "$dest/run.log" || code=$?
    printf '%s\t%s\n' "$test" "$code" >> "$summary"
    if [ -d app/build/test-results/testReleaseUnitTest ]; then
        cp -a app/build/test-results/testReleaseUnitTest "$dest/results"
    fi
}

# Run the five classes observed before and through the second timeout in
# build 67's worker 52 together. Gradle controls their execution order.
prefix=com.droiddeck.launcher.ui
printf 'test\texit_code\n' > "$out/group-summary.tsv"
run_case component-navigation "$out/component-navigation" "$out/group-summary.tsv" \
    "$prefix.BottomScreenBarTest" "$prefix.BottomScreenTypingTest" "$prefix.CloudMessageTest" \
    "$prefix.ComponentDownloadsDrawerTest" "$prefix.ComponentsPageProfileTest"
cat "$out/group-summary.tsv"

printf 'test\texit_code\n' > "$out/summary.tsv"
for test in "${tests[@]}"; do
    run_case "$test" "$out/${test##*.}" "$out/summary.tsv" "$test"
done
cat "$out/summary.tsv"

# Separate whole-class diagnostics distinguish intra-class interactions from
# failures that only appear when unrelated classes share a JVM. They do not
# change the mandatory full-suite result or the method-level summary.
classes=(
    'com.droiddeck.launcher.ui.ComponentDownloadsDrawerTest'
    'com.droiddeck.launcher.ui.ComponentsPageProfileTest'
)
printf 'test\texit_code\n' > "$out/class-summary.tsv"
for test in "${classes[@]}"; do
    run_case "$test" "$out/class-${test##*.}" "$out/class-summary.tsv" "$test"
done
cat "$out/class-summary.tsv"

# If the larger group fails but both classes pass alone, run just those two
# classes together. Keep the extra probe separate from existing summaries.
# A failing pair narrows the reproducer; a passing pair does not prove which
# preceding class or execution order caused the larger group's failure.
printf 'test\texit_code\n' > "$out/pair-summary.tsv"
rm -rf "$out/component-pair"
if grep -Eq '^component-navigation[[:space:]]+[1-9][0-9]*$' "$out/group-summary.tsv" &&
    grep -Fxq "$(printf '%s\t0' "${classes[0]}")" "$out/class-summary.tsv" &&
    grep -Fxq "$(printf '%s\t0' "${classes[1]}")" "$out/class-summary.tsv"; then
    run_case component-pair "$out/component-pair" "$out/pair-summary.tsv" "${classes[@]}"
fi
cat "$out/pair-summary.tsv"
