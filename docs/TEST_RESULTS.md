# Test results

## What was executed in the build environment (Linux x86_64, JDK 21, Android SDK 35, NDK 27)

| Suite | Tests | Result |
|---|---|---|
| `:core` EngineTest (conversation engine, policy, planner, parser, time parser, language detection) | 21 | all pass |
| `:core` LanguageBenchmarkTest — 135 sentences | 1 (135 cases) | 135/135, see below |
| `:core` WakeWordTokenizerTest (vs. reference `sentencepiece` output) | 3 | all pass |
| `:license-core` LicenseTest | 7 | all pass |
| `:app` AppLogicTest (cross-script contact matching, WhatsApp number formatting) | 4 | all pass |
| License generator CLI (keygen → issue → verify, right and wrong device) | manual | works |
| Native llama.cpp bridge: arm64 build inside the APK | build | links |
| Native llama.cpp bridge: x86_64 host build + real Qwen3 model | 20 prompts | 17/20, see `benchmarks/llm-host-validation.txt` |
| `assembleDebug`, `assembleRelease` (R8), `apksigner verify` | build | pass (v2 signature) |
| Model files: sizes/SHA-256 | Qwen3 GGUF downloaded and hash matches catalog | pass |

### Intent benchmark (135 sentences)
TN 38/38 · MSA 18/18 · FR 28/28 · EN 25/25 · Arabizi 15/15 · Mixed TN/FR 6/6 · open-ended→LLM 5/5.
**Caveats you should know:**
* It measures *text → intent/slots* after transcription. It says nothing about speech recognition accuracy.
* The first run scored 113/134 (84%). The failures were real bugs (multi-word lexicon entries never matching, the name "علي" being swallowed as a preposition, missing "him" in "قلّو", name boundaries, language detection). They were fixed in the implementation.
* One expectation was changed: "نحب نبعث message لسارة" expected `MIXED_TN_FR`, now `TUNISIAN`, because "message" alone is not a code-switching signal (both languages use it) and the reply language is identical. A clearer mixed sentence was added.
* I wrote the sentences; they come from the brief's examples plus common phrasing. They are not recordings by Tunisian speakers and the parser's lexicon was developed against the same author's sense of Derja. Expect lower accuracy on real users.

### LLM host validation (Qwen3-1.7B Q4_K_M)
17/20 on two identical runs. Structured actions: **10/10** valid actions (Tunisian, MSA, French, English) after adding few-shot examples (before them the model echoed Derja requests back: 12/20). **Open-ended Arabic/Tunisian answers are weak**: it answered "the capital of Tunisia is Tripoli" in an earlier prompt version, explained nothing about the sky, and mis-answered "who discovered America". French/English are fine. The three remaining failures are all of this kind or the multi-step case (which the deterministic planner handles before the LLM is consulted). Speed numbers are for a 4-core x86 host and **must not be read as phone numbers**.

### Tunisian Whisper conversion + ASR benchmark (host)
Exporter validated: fp32 ONNX token-identical to PyTorch on 25/25 utterances; real sherpa-onnx runtime loads and decodes the INT8 files. WER/CER/RTF tables and the contamination caveats are in `LANGUAGE_BENCHMARK.md` (raw: `benchmarks/asr-tunisian-conversion.txt`). Not verified: exactness of the *small* model's fp32 conversion by token comparison (only tiny was compared; small's sherpa INT8 WER is within 2 points of its PyTorch fp32 WER), and any on-device behaviour.

## Not executed (needs hardware) — marked pending
Everything that touches Android at runtime: installation, assistant-role selection, foreground microphone service (incl. Android 14/15 restrictions), wake-word detection (false accepts/rejects, latency, screen-off, battery), Whisper latency/RAM on the Dimensity 8300, TTS voices (is `ar-TN` installed on the device?), barge-in with real echo, CameraX capture, calls, WhatsApp hand-off, contacts, calendar, notifications, reminders, alarms, boot behaviour, thermal behaviour, OEM battery killers on Infinix XOS. Instrumented tests were not written because no device/emulator could run them.
