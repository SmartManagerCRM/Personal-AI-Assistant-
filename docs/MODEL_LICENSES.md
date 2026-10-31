# Model and third-party license register

Verify again before any commercial release; this register records what was found in October 2026 from repository metadata, not legal advice.

## Models (downloaded by the user, never bundled)
| Model | Source | License (as tagged) | Commercial use | Notes |
|---|---|---|---|---|
| Whisper Base multilingual INT8 | `csukuangfj/sherpa-onnx-whisper-base` | MIT (OpenAI Whisper) | yes | 160 MB; SHA-256 pinned |
| Whisper large-v3-turbo, Arabic dialectal v2 INT8 (experimental) | `dmouayad/sherpa-onnx-whisper-large-v3-turbo-arabic-dialectal-v2` | Apache-2.0 tag | **verify** fine-tune dataset `oddadmix/lahgtna-v3-small` | 1.1 GB; not benchmarked |
| KWS Zipformer 3.3M (English) | `mobilebytesensei/sherpa-onnx-kws-zipformer-gigaspeech-3.3M` | Apache-2.0 tag | **verify GigaSpeech data terms** | 6 MB |
| Qwen3-1.7B Q4_K_M GGUF | `unsloth/Qwen3-1.7B-GGUF` | Apache-2.0 (Qwen3) | yes | 1.1 GB; SHA-256 verified against the downloaded file |

| Whisper Small — Tunisian Derja, converted to sherpa-onnx INT8 (import-only) | `TheBiggyBang/whisper-small-tunisian-derja` + `scripts/asr-conversion` | Apache-2.0 tag (base: Whisper MIT) | yes, **with CC-BY-4.0 attribution** for the LinTO training data (`linagora/linto-dataset-audio-ar-tn`); the LinTO corpora themselves include TunSwitch/ApprendreLeTunisien — verify their terms | 413 MB; hashes pinned to the author's conversion build |
| Whisper Tiny — Tunisian (`awaxsama/whisper-tiny-tunisian-arabic`) | converted for testing only | Apache-2.0 tag | not shipped: no better than stock Base on unseen data; training sources include TunSwitch | |

## Evaluated and **excluded**
| Model | Reason |
|---|---|
| `TuniSpeech-AI/whisper-tunisian-dialect` | **CC-BY-NC-4.0** — non-commercial; cannot ship in a commercial release |
| `OpenVoiceOS/whisper-*-arabic-dialectal-v2-onnx` | Apache-2.0, but Optimum/onnx-asr layout (merged decoder + external data); not loadable by sherpa-onnx without re-export; small int8 is ~705 MB |

## Code dependencies
| Component | License |
|---|---|
| Kotlin, kotlinx.coroutines, AndroidX (core, activity, lifecycle, navigation, Room, DataStore, WorkManager, CameraX, Compose, Material3) | Apache-2.0 |
| Hilt / Dagger | Apache-2.0 |
| OkHttp | Apache-2.0 |
| sherpa-onnx 1.12.9 (AAR) | Apache-2.0 |
| ONNX Runtime (inside the sherpa AAR) | MIT |
| llama.cpp / ggml (sources from the `llama-cpp-python` 0.3.36 sdist) | MIT |
| JUnit (tests only) | EPL-1.0 |
| `org.json` (Android platform) | Android platform |
No Supabase, no paid SDK, no analytics, no crash reporter.
