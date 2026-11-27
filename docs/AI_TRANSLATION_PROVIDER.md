# File translation provider integration

The translation runner processes explicit 25-row windows of existing file authoring sources.
It never writes a game file, skips already entered translations, validates supported placeholders,
reports resumable positions, checks caller-supplied session/memory budget before and after model calls,
and closes the provider on completion, failure, or cancellation. Coroutine timeout requires a
cooperative adapter; native calls must also have a bounded cancellation/resource strategy.
Only a future UI adapter may commit a returned draft after checking the original revision again.

## Model selection remains open

Google ML Kit on-device Translation (com.google.mlkit:translate:17.0.3) is an evaluated candidate,
not an installed dependency. It supports Korean, model download and local text inference.
Its SDK diagnostics/data disclosure and attribution/distribution conditions need review against
this project's privacy, resource and licensing requirements before adoption. No external SDK or
model is downloaded automatically and no cloud text translation or live screen translation is added.

References:
- https://developers.google.com/ml-kit/language/translation/android
- https://developers.google.com/ml-kit/terms
- https://developers.google.com/ml-kit/android-data-disclosure
- https://developers.google.com/ml-kit/language/translation/translation-terms

Next: select a license-compatible local model/adapter, confirm real inference, connect an explicit
UI operation with cancellation and stale-source checks, test on actual hardware, and measure
memory/storage/translation quality. A fake provider is only a test fixture, never a user-facing engine.

## Implemented local glossary provider

MenuTranslationProvider uses an original fixed glossary for exact common English UI phrases (case-insensitive). No model, SDK, network, or automatic game-file writes. Unknown phrases stay unchanged. The dialog explicitly offers up to 25 rows per click, preserves existing edits, and rechecks the source revision and session/memory budget before committing a draft. Resetting the cursor does not clear the draft. User review remains necessary because exact phrases can be context-dependent. This is not neural translation. A further candidate is Helsinki-NLP/opus-mt-tc-big-en-ko (Marian English→Korean, also listed in Qualcomm AI Hub); actual model license, tokenizer, runtime, resource requirements and inference must still be verified before shipping. Reference: https://aihub.qualcomm.com/mobile/models/opus_mt_en_ko
