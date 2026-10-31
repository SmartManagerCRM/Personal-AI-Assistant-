# Language benchmark

## 1. Intent understanding (text level) — measured
135 sentences: Tunisian Arabic script 38, MSA 18, French 28, English 25, Arabizi 15, Tunisian/French mixed 6, open-ended 5 (must fall through to the LLM). **135/135** — report in `benchmarks/intent-benchmark-report.txt`. Read the caveats in `TEST_RESULTS.md`: author-written sentences, text only, first run was 84% before bug fixes.

## 2. Speech recognition — NOT measured
No Tunisian recordings and no device were available, so no WER/CER exists. What was established:

| Candidate | Format / size | License | sherpa-onnx compatible | Status |
|---|---|---|---|---|
| Whisper Base multilingual INT8 | sherpa layout, 160 MB | MIT | yes | **default**, not timed |
| Whisper large-v3-turbo Arabic dialectal v2 INT8 | sherpa layout, 1.1 GB | Apache-2.0 tag | yes (same loader) | experimental in model manager, not timed, probably too slow for interactive use on a phone |
| OpenVoiceOS whisper-small Arabic dialectal v2 ONNX | Optimum/onnx-asr layout, 705 MB int8 | Apache-2.0 | **no** (needs re-export) | not integrated |
| TuniSpeech `whisper-tunisian-dialect` | transformers | **CC-BY-NC-4.0** | needs export | **excluded** (non-commercial) |
| TheBiggyBang `whisper-small-tunisian-derja`, awaxsama `whisper-tiny-tunisian-arabic` | transformers/CT2 | Apache-2.0 tag | needs sherpa `export-onnx.py` | recommended next step: convert, INT8-quantise, benchmark |

Planned protocol (pending): 50 recorded utterances per language on the Infinix X6856, reporting WER, real-time factor, peak RAM, model load time and language-detection accuracy per model. The in-app Performance screen already records last/average ASR latency, model load time and detected language.

## 3. Local LLM (host validation, not phone)
See `benchmarks/llm-host-validation.txt`: structured actions 10/10, open-ended Arabic/Tunisian answers unreliable (wrong or empty answers), French/English fine.
