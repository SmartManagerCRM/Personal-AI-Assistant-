# Language benchmark

## 1. Intent understanding (text level) — measured
135 sentences: Tunisian Arabic script 38, MSA 18, French 28, English 25, Arabizi 15, Tunisian/French mixed 6, open-ended 5 (must fall through to the LLM). **135/135** — report in `benchmarks/intent-benchmark-report.txt`. Read the caveats in `TEST_RESULTS.md`: author-written sentences, text only, first run was 84% before bug fixes.

## 2. Speech recognition — measured on a host (not a phone)

Two Apache-2.0 Tunisian Whisper fine-tunes were **converted to the sherpa-onnx layout** with `scripts/asr-conversion/export_sherpa.py` (own exporter; GitHub/sherpa's script was unreachable) and run through the same sherpa-onnx runtime the app uses. The fp32 ONNX is **token-identical to PyTorch `generate` on 25/25 utterances** (tiny model), so the conversion is exact; INT8 (MatMul-only dynamic quantisation) changes tokens but cost little WER. Raw numbers: `benchmarks/asr-tunisian-conversion.txt`.

| Model (INT8, sherpa-onnx, language forced to `ar`) | Size | A: TunSwitch val WER / CER | B: unseen podcast WER / CER | RTF (host) | Peak RSS |
|---|---|---|---|---|---|
| **Whisper Small — Tunisian (converted)** | 413 MB | **47.2 / 28.1** | **63.6 / 34.5** | 0.20 | 1.1 GB |
| Whisper Tiny — Tunisian (converted) | 122 MB | 72.4 / 46.9 | 94.0 / 64.0 | 0.05 | 0.4 GB |
| Whisper Base multilingual (the app's default) | 160 MB | 98.8 / 68.9 (auto-language: 106 / 74) | 93.1 / 58.8 | 0.14 | 0.75 GB |

PyTorch fp32 reference on set A: small 45.3 / 22.7, tiny 77.1 / 46.1 (INT8 cost: small +1.9 WER, +5.4 CER points; tiny none).

**How to read this honestly**
* **Test A (TunSwitch validation, 164 clips, code-switched Derja+French/English) is probably in-domain for the fine-tunes**: the tiny model's training manifest lists TunSwitchCS/TunSwitchTO and the small model's LinTO corpus shares those sources. Stock Whisper never saw it. Treat A as an optimistic upper bound.
* **Test B (100 unseen podcast clips) is the fairer one**: only the **small** Tunisian model stays clearly better than stock Base (63.6% vs 93.1% WER). The **tiny** one collapses to stock-Base level (94%) — its set-A result was mostly familiarity. Do not use the tiny model. B's references are diacritized transcripts of unknown origin, so absolute numbers are indicative.
* Even the best model is far from dictation quality (≥47% WER). It is useful for short commands where the rule parser tolerates errors, not for free text.
* Whisper Base's >90% WER means **the shipped default is effectively unusable for Tunisian speech**; forcing `ar` helps over auto-detection (99 vs 106%).
* Speed/RAM are for a 4-core x86 host, **not the Dimensity 8300**; 413 MB of weights and ≈1.1 GB RSS are real, the RTF is not transferable. Greedy decoding with no repetition guard.
* Other candidates: `OpenVoiceOS/*-arabic-dialectal-v2-onnx` (Optimum layout, not loadable as-is), `dmouayad/...large-v3-turbo-arabic-dialectal-v2` (sherpa layout, 1.1 GB, not run), TuniSpeech (CC-BY-NC, excluded).

## 3. Local LLM (host validation, not phone)
See `benchmarks/llm-host-validation.txt`: structured actions 10/10, open-ended Arabic/Tunisian answers unreliable (wrong or empty answers), French/English fine.
