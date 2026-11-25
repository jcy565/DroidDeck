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
