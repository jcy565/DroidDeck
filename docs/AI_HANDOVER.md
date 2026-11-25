# AI handover checkpoint

Branch: ai-compatibility-history. Recovered HEAD: 8899de2bba92bd89f9173dd4fe1b043ec9f4f1a9. Stable: a367bc7be1b0cb5ed2e9464d8d50ea8fd64f16ec.
See AI_DEVELOPMENT_STATUS.md and AI_RUNTIME_BUDGET.md for cumulative status and constraints.

Recovered Work changes since 6c781c8: App memory callbacks, idle Steam touch icon cache release, folder scan cancellation, compatibility panel in GameDetail, performance trial scene/output comparison, SmartMemory tests, workflow changes. Work reports 23 JVM, 4 inventory and 6 CI tests passed locally; not independently rerun here.
Actions 45595329653 failed at Prepare Robolectric runtime dependencies; seven prerequisite jobs succeeded. Exact failure log not yet inspected. Do not bypass tests or checksum verification. No new APK build requested.
Next: inspect dependency failure, then implement approved native settings changes with stale-value checks and safe rollback; validate existing APIs and session races. Preserve original app/data, no root, no security bypass, no resident AI, no arbitrary process killing. Device installation/performance unverified.
Update this checkpoint after each substantive change, before usage runs low. Do not claim unexecuted tests passed.

## CI follow-up
Job 139143573666 logs confirm curl HTTP 429, exit 22 during Robolectric preparation after APK packaging. Commit 19129156b920256b89908c14d9e14fd082576aad adds delayed retries and alternate Maven Central endpoint, retaining SHA-512 verification. Not tested or rebuilt. Next: review native settings apply/rollback and verify CI fix at next authorized build.

## Native settings transaction
01da23fa341c7162038ba954ab4465e7f778fa18 adds DroidDeckSettingsTransaction.kt for approved output resolution and Proton DXVK bridge changes, stale-value checks, session gate and in-memory rollback receipt. Not compiled or tested. Receipt is not durable; effective resolution restore may not reproduce absent/legacy preference state. Existing setters use apply(), and other writers/session start can race. No UI wiring. Next: durable exact preference snapshot, tests and approved UI integration before enabling writes.

## Persistent settings backup
00805c35684ca275611ab7dd4749f3132286d963 adds SettingsBackup; b9bda1799acca773ec0a85b8652e193305b70d5a wires transaction to it. Confirmed SessionPrefs uses private session preferences and exact three keys. Same editor commit stores prior presence/raw value and proposed value, restore removes originally absent keys, conflicts refused. One pending backup per key. Not compiled/tested; direct preference writes intentionally limited to verified keys, call off main thread. Commit failure and concurrent external writers/session starts remain risks; recovery UI and tests next. No new build.

## Backup regression tests
07939870bdf007a4d95e0a67d4f545c53beab82f adds seven Robolectric SDK28 tests for absent key, legacy value, boolean type, stale preview, duplicate backup, conflicting restore and unapproved restore. Written only; not run. Next: recovery UI, session race protection and supported driver/runtime preview. Overall progress estimate ~60%, subjective; device verification still zero.

## Runtime preview
741ea8efe63d5e791e49cb3053194c83e0ac9a66 adds RuntimeSettingsPreview.kt using verified SessionPrefs Linux/Android driver and FEX getters. Candidate membership is caller-supplied, not yet connected to installed component inventory. FEX setter publishes GameEnvironmentStore, so raw preference writes are not appropriate. Read-only, untested. Next: locate actual component selector, bind candidate lists and add tests; recovery UI and session race protection remain. No new build.

## Runtime preview tests
3b19c7155a0298fbdbc84cf6d8043f92da662332 adds five tests covering all three setting previews, unknown/blank/control-character/oversized candidate rejection. Not executed. Actual installed component inventory binding remains outstanding; do not enable runtime writes before validation. No new build.

## Local driver candidates
 d7ca7efb8b0f5720e0c5fa66bae646684c532500 adds driverCandidates(GpuDriversState, Setting) using DriverMenus.state local linuxRows/androidRows, excluding empty/default auto IDs and download catalog. Verified DriverMenus.refreshDrivers uses LinuxVulkanDriver.optionValues and Turnip visibleBundled/enumerateImported. selectDriver can change bundle halves and GPU mode, so do not use single-key rollback for drivers. FEX candidates empty until verified. Adapter not yet called by UI, no tests/build run. Next: integrate fresh selector state and multi-setting preview/rollback.

## Driver group snapshot
6fa5e4cda90a33c200bce297ca4414ecc62bfb63 adds read-only DriverSettingsSnapshot for exact nullable linuxDriver/androidDriver/gpuDriverMode and legacy linuxDriver.steam/desktop keys. Verified DriverBundle.select changes both halves, selectDisplay may clear Linux, DriverMenus.setMode(auto=true) can trigger ensureAuto and further changes. Snapshot does not capture installed files/live compositor/auto jobs; no driver writes or restore enabled. Next: persistent group journal, conflict checks and tests; restrict experiments until auto-job coordination exists. Not built/tested.

## Persistent driver baseline
1ee4b4bff3a5374f2cb0413e8436fef866246c52 adds DriverBackupJournal save/load using private preferences and synchronous commit, approval, idle gate, stale snapshot and duplicate checks, exact nullable values. No restore or driver writes enabled. Not tested or built; session/auto-job races remain. Next: journal tests, applied-state tracking and coordinated restore.

## Driver journal tests
2a53b9e4416956091ea56bed57185300fdaafb6b adds six Robolectric tests: empty journal, exact nullable baseline and change detection, duplicate, stale, unapproved, incomplete journal. Written only; no build or execution. Next: applied-state tracking, conflict-safe recovery and auto-job coordination. User's '다음' means perform actual next code task and checkpoint, not merely describe plans.

## Applied driver record
90a829e904d26f41a572e297caf3ad85fdccb8ee persists a separately approved applied snapshot and requires it for recovery review. e5de498d702be3fdf6504765e70692af251961b2 adds two regression tests for conflicts, approval and duplicate records. Tests not run; no build. No driver writes or restoration enabled. External writers and session/auto-job races remain. Next: coordinate driver jobs and session lifecycle before implementing actual restore; check malformed journal handling and verify tests in CI.

## Malformed driver journal handling
1de4af1a10fa9463fd88d689ba9a103fb68ccd80 makes recovery review fail closed for malformed JSON, invalid key sets and unexpected types. 5249217f2f74eda117bc32d650a71dc5214b7ba2 adds two regression tests. Direct load remains strict for diagnostics. No tests run or build. Session/auto-job coordination still outstanding; no restore or driver writes enabled.

## Session gate follow-up
 d2b50327f0b6370dabf5d1d1b91126f92ef48a0c rechecks GameplayBudget immediately before baseline/applied journal persistence. Reviewed SessionState and SessionService running transitions; this narrows but does not eliminate the race. DriverMenus source path not resolved in this turn. No shared lifecycle lock or driver restore enabled. Next locate actual driver job implementation and coordinate lifecycle before enabling writes. Not compiled/tested.

## Auto driver callback guard
0df9a67fb53e4f061252fdb43dcdc3c06590bf29 locates DriverMenus.kt in launcher root and revalidates persisted auto mode and GameplayBudget before async release-check callback selects drivers or fallback. Resets lastAutoCheck when busy. This is not a shared lifecycle lock; installPair completion and other writers still need review. No tests/build run. Driver restore remains disabled.

## Pair installation completion guard
3f2482a1d5b817a0a6770915feaa259f95d8820d captures expected mode and both driver IDs before installPair and rejects stale selection or busy session at completion for manual and auto selection. Does not prevent installation/replacement of files during a session, ABA changes or races after final check. Next review installAsset replacement and shared lifecycle coordination; add regression tests. No build/tests executed; restore remains disabled.

## Driver file installation guard
ae71be7dda01918774a24964fd471705f8d6b318 checks GameplayBudget at pair install entry and after asset download before invoking file installers. Existing finally cleans downloaded temporary file. This narrows race but cannot prevent session starting during installation. Manual imports/deletion and shared lifecycle coordination remain. No regression tests added this turn; no build/tests executed. Next review those paths and add tests.

## Manual driver guards
7393ca1dcbfee9f3da51ee77e602985496026f76 guards import entry and worker, manual select/delete, and skips imported bundle auto-selection when session busy or exact driver snapshot changed. No shared lifecycle lock; import may still race with session start. No tests/build executed. Next add regression coverage and review remaining writers. User requests explicit actionable notice when tools are blocked.

## Remaining driver entry guards
165d6111825c713ddd7f270136547be9b5673a65 adds existing manual write gate to chooseMode, setMode, selectPair, updatePair and downloadReleaseDriver. Prevents mode mutation before pair install guard and blocks release download entry during busy sessions. No shared lifecycle lock; nested guards can still race. Tests not added or run, no build. Next regression coverage and lifecycle coordination.

## Async selection guard regression coverage
8cb608fb68833e793e15fb46a111d942e16e0b8c adds fail-closed raw snapshot capture and shared completion gate; 7a7039b760e97fb2cc2f8e8ee364fa40f55b4de6 wires pair install and manual bundle import to it. All five exact nullable selector keys (including legacy) must match; checks session budget before and after capture. cb430b9cb92d772b98e2f34f2d212befc7c8db44 adds five Robolectric regression tests for idle, all key changes, malformed/null baseline, all phases and running/installing/stopping flags. Written only, not compiled or executed; no new build. SessionState uses separate volatile fields, so this remains a check rather than an atomic lifecycle lock; ABA and post-check races and file replacements are unresolved. Next inspect SessionService startup ownership and establish nonblocking shared coordination before enabling driver restore.

## Shared admission gate foundation
 d5da2b7a45545fe0292bc50bd5fc78243bce7e43 adds process-local nonblocking SessionDriverGate with exclusive transferable identity-checked leases. 462b47148da6b07ef55d624a8638f1c568d20e14 and e5441be9cfc0a5b92c1be84eea4fe6fdad9f7bd7 wrap DriverMenus downloaded archive installers and manual import workers in driver-write leases with automatic close and current GameplayBudget check. 8df2db88beaf7781ddad793b9dbd0dc375468f37 adds six JVM tests for exclusion, rejected admission, stale close, exceptions and cross-thread release. No compilation, test execution or build. Reviewed SessionEvents.begin (PREPARING before service), SessionService.onStartCommand (running set after setup), and finishSessionStop (asynchronous teardown and IDLE/FAILED). SESSION lease is deliberately not acquired by production session paths yet: must trace pre-service preparation, all launch/cancel/failure/relaunch paths and lifetime handoff before wiring it, without blocking UI or silently dropping launches. Driver delete, selector writes, auto fallback and other installers still not coordinated. Thus only these archive/import workers are mutually exclusive; no claim of session-vs-driver mutual exclusion. Next trace launcher startup and add safe session admission integration, then remaining driver writers. Restore remains disabled.

## Shared gate concurrency coverage
During this turn DriverMenus update returned HTTP 409; refreshed source contains SessionDriverGate and withDriverFileWrite wrapping release install/import, so preserved newer changes. Own redundant DriverMutationGate added in 78b146af1af0ec8391c02e555c2d47f6343c64a8 removed in 214579e5a8acd5aba552f7e15a27af9b6af6a62a. 577ac312713e8b0f85fbf6452b1809235e1a4c87 adds six JVM tests for mutual exclusion, denied predicate, stale close, cross-thread release and simultaneous admission. Tests not executed; no build. SessionService inspected at blob 14bde619824f5e54924117182c752dd67b965ecb did not yet use SessionDriverGate. SessionEvents.begin publishes PREPARING before service startup; inspect actual launch callers and cleanup before acquiring/releasing session lease. Verify latest branch before editing because concurrent changes observed. Do not enable restore yet.

## Pre-service session admission
3d93f389ef27495b58eca35d197ff93609c8d1c7 integrates SessionDriverGate in root SessionActivity.kt onCreate for new IDLE/FAILED launches: try SESSION lease before SessionEvents.begin publishes PREPARING, use closes it after publication, and refuse busy admission via existing early-finish lifecycle flag. 734a7f5cb1b71c01996b86db0b1e20db352206c8 and b34546b14b23b78bbaeb13f0d9580290b8a20866 add English/Korean feedback; 327e0de5cb6467c1303827d3c4fc217a0d7a477f wires localized message. Confirmed Activity starts/attaches compositor before SessionService.start, so service-only gate would be too late. This is phase handoff, not session-lifetime lease; only coordinated driver workers are covered. SessionService itself unchanged, direct starts/relaunch/cancel/failed preparation and remaining driver writers need audit. Default-branch search found no callers; root SessionActivity path resolved directly. No test execution/build or device validation. Next test phase handoff and review direct service start/teardown, then coordinate all driver writes. Restore disabled.
