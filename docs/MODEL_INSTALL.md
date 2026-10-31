# Model installation guide

Models are not in the APK. Open **Settings → AI models**.

1. Enable network only for the download: **Settings → Online providers → Allow network access** (it is OFF by default; downloads are refused while it is off). Turn it off again afterwards.
2. Install, in this order: **Wake word** (6 MB), **Whisper Base** (160 MB). Tap *Install*; every file is size- and SHA-256-checked and discarded on mismatch. Optional: Qwen3-1.7B (1.1 GB; needs 2.5 GB free), the experimental dialectal model (1.1 GB).
3. Switch **Use** on for the ASR model you want. Without any ASR model the app uses Android's speech recognizer (may use Google servers on phones without an offline pack — the model manager tells you which engine is active).

## Fully offline installation
Download the files on a PC (URLs are in `ModelCatalog.kt` / the model cards), copy them to the phone, then *Import file* for each file. The importer accepts a file only if its name is part of the catalog entry and its size and SHA-256 match.

| Model | Files |
|---|---|
| Wake word | `encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx`, `decoder-epoch-12-avg-2-chunk-16-left-64.onnx`, `joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx`, `tokens.txt`, `bpe.model` from `mobilebytesensei/sherpa-onnx-kws-zipformer-gigaspeech-3.3M` |
| Whisper Base | `base-encoder.int8.onnx`, `base-decoder.int8.onnx`, `base-tokens.txt` from `csukuangfj/sherpa-onnx-whisper-base` |
| Qwen3 | `Qwen3-1.7B-Q4_K_M.gguf` from `unsloth/Qwen3-1.7B-GGUF` |

## Building the local-LLM runtime
`scripts/fetch-llama-cpp.sh` downloads the llama.cpp sources from the pinned PyPI sdist (no GitHub needed); with the NDK 27.2 + CMake 3.22 installed, Gradle then compiles `libsmartllama.so` for arm64-v8a. The APK delivered with this project already contains it. `scripts/fetch-sherpa-onnx.sh` fetches the sherpa-onnx AAR (hash-pinned) and runs automatically before every build.

## Tunisian speech model (import-only)
"Whisper Small — Tunisian" is not downloadable: it is a conversion. Run `scripts/asr-conversion` (README there) to produce `small-tn-encoder.int8.onnx`, `small-tn-decoder.int8.onnx`, `small-tn-tokens.txt`, copy them to the phone and use *Import file* on that entry. The importer checks the catalog's SHA-256; a re-conversion on another machine gives different hashes — update `ModelCatalog.kt` (or set the hash to null) accordingly. The model forces Arabic (`ar`) decoding. Needs ≈1.1 GB RAM while loaded.

## Adding a better Tunisian ASR model
Export an Apache-2.0 Tunisian Whisper fine-tune with sherpa-onnx's `scripts/whisper/export-onnx.py`, INT8-quantise, then add a `ModelEntry` (kind ASR, files `encoder*.onnx`, `decoder*.onnx`, `tokens.txt`, with SHA-256) to `ModelCatalog`. The loader discovers file names generically.
