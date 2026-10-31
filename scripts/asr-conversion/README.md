# Convert a HuggingFace Whisper fine-tune to sherpa-onnx and benchmark it

Needs Python ≥3.10 and: `torch transformers onnx onnxruntime sherpa-onnx soundfile pyarrow scipy jiwer huggingface_hub safetensors numpy`.

```
# 1. official sherpa Whisper Base (only its tokenizer metadata + tokens.txt are reused; same 51865-token vocabulary)
huggingface-cli download csukuangfj/sherpa-onnx-whisper-base base-encoder.int8.onnx base-decoder.int8.onnx base-tokens.txt --local-dir sherpa-base
# 2. fine-tune (any size: tiny/base/small; not large-v3, which has 128 mel bins)
huggingface-cli download TheBiggyBang/whisper-small-tunisian-derja --local-dir hf-small-tn
python export_sherpa.py hf-small-tn out-small-tn small-tn sherpa-base      # writes fp32 + INT8 encoder/decoder/tokens
# 3. exactness check against PyTorch generate (token-identical expected for fp32)
python fidelity.py hf-small-tn out-small-tn small-tn fp32 25
# 4. WER/CER/RTF through the real sherpa-onnx runtime
python prep.py   # decodes MBZUAI/TunSwitch validation parquet to wav_*.npy + refs.json
python bench_sherpa.py small-tn out-small-tn small-tn ar int8
```
`export_sherpa.py` reimplements the Whisper encoder/decoder in plain PyTorch on the HF weights (KV caches, cross-attention K/V outputs, the `offset` input and metadata keys match the official sherpa-onnx Whisper models).
Quantisation is MatMul-only dynamic INT8. The exported hashes are specific to your build: update `ModelCatalog.kt` (or leave `sha256 = null`) after re-converting.
