# Independent engine / AI module architecture

The user wants one usable DroidDeck AI app with independently maintained engine and AI update tracks, not merely a block on upstream APK installs.

Initial implementation: a plain Java 17 Gradle library :ai-core, module version 0.1.0. It owns the AI-to-host API compatibility contract and ChatGPT exchange snapshot/ID validation. It has no Android imports, network, file I/O, native library, model runtime or dependency on :app. The application owns host API version 1, supported draft schema 1 and the trusted file tools, and depends on :ai-core one way. AiHostBridge checks the actual linked module at runtime, reports version/mismatch, and blocks new agent execution, exchange creation/acceptance, model import and share. The manual file and game tools remain independent of this gate. The contract schema describes AI exchange/draft compatibility; existing on-disk draft format is not migrated by this change.

This is the first extracted module, not complete AI isolation: GameAgent prompt/tool routing, UI and optional native inference remain in :app. The build must still contain the library. LinkageError is caught at the bridge, but process-level native crashes and actual game startup isolation need hardware tests. The AI contract does not auto-detect source incompatibility; engine changes must update the host API version when required, and CI must validate the boundary.

Update tracks:
* Engine: reviewed upstream source revision in an isolated merge branch. Run full game, API, packaging, permission, signing and regression tests before combining.
* AI code: maintain its own version and source history. Future code updates must ship in a normally signed APK (or a supported separately signed Android component, if later designed and reviewed). Do not download arbitrary dex/native code or bypass package validation. Current :ai-core is bundled; no independent over-the-air executable replacement.
* AI data/models: later add explicit signed, schema-versioned non-executable data packages with pinned trusted keys, staged validation, atomic activation, prior-version rollback, size limits and no auto-transmission. No such updater yet.
* Preserve separate AI package identity and user-managed release certificate; upstream official installer remains blocked in AI builds. Save files, AI drafts and backups must be preserved during review and migration.

Next steps: extract more portable AI planning/protocol code behind a narrow engine-owned interface; define signed AI data manifest and rollback; add version matrix and upstream merge checks; verify Android Gradle and full regression gate; trusted signing and actual device install/update; evaluate optional local runtime and ChatGPT share flow. No claim of independent APK-free AI code update or safe installation yet.
