# AI runtime budget

Gameplay takes priority over assistant features.

- No resident language model, continuous log scanning, or periodic AI polling during gameplay.
- Defer optional analysis, storage scans, patching and settings changes until the session is idle. Explicit performance sampling may run for a bounded interval.
- Keep launch observations asynchronous and bounded; never block launch on database writes. Audit existing recorder for contention and defer optional writes where practical.
- Use existing session state rather than adding a polling service. Cancel optional jobs on session start; recheck state before writes.
- Avoid new wake locks, persistent foreground services and unnecessary network requests.
- Proposed acceptance targets, not measured results: idle assistant CPU near zero during gameplay; incremental steady-state memory under 20 MiB; median FPS regression under 2% and P95 frame-time regression under 5% in repeated matched-scene tests. Report measurement uncertainty and device conditions.
- Verify on the actual Android device against the same build with assistant features disabled. No claim of zero overhead before measurement.
- Preserve Android thermal and security safeguards. Never trade them for performance.
