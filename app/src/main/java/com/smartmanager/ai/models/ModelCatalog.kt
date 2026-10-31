package com.smartmanager.ai.models

enum class ModelKind { KWS, ASR, LLM, TTS, VAD }

data class ModelFile(val name: String, val url: String, val sizeBytes: Long, val sha256: String? = null)

data class ModelEntry(
    val id: String, val kind: ModelKind, val title: String, val description: String,
    val files: List<ModelFile>, val license: String, val commercialUse: String, val languages: String, val runtime: String,
    /** Minimum free RAM guidance in MB. */
    val ramMb: Int = 0,
) { val totalBytes get() = files.sumOf { it.sizeBytes } }

/**
 * Catalog of downloadable models. URLs, sizes and SHA-256 values were read from the Hugging Face API (Oct 2026).
 * Every file carries its SHA-256 (LFS oid from the HF API, or computed from the downloaded small file) and is verified after download.
 * Nothing here is bundled in the APK and nothing downloads without an explicit tap.
 */
object ModelCatalog {
    private const val HF = "https://huggingface.co"
    val entries: List<ModelEntry> = listOf(
        ModelEntry(
            "kws-gigaspeech-3m", ModelKind.KWS, "Wake word — Zipformer 3.3M (English phonetics)",
            "Open-vocabulary keyword spotter (~6 MB). Any phrase you type is tokenised on the device. English-trained: Tunisian/French phrases work only approximately — test and tune sensitivity.",
            listOf(
                ModelFile("encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx", "$HF/mobilebytesensei/sherpa-onnx-kws-zipformer-gigaspeech-3.3M/resolve/main/encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx", 4_807_159, "1e721676515bcd42a186979733981213c66c80db680e1cc582dfedf3be76e678"),
                ModelFile("decoder-epoch-12-avg-2-chunk-16-left-64.onnx", "$HF/mobilebytesensei/sherpa-onnx-kws-zipformer-gigaspeech-3.3M/resolve/main/decoder-epoch-12-avg-2-chunk-16-left-64.onnx", 1_063_189, "f61ebd3eed3773a44d088d53dfae92dbb6aec4839f4dcaee2d402414741663a3"),
                ModelFile("joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx", "$HF/mobilebytesensei/sherpa-onnx-kws-zipformer-gigaspeech-3.3M/resolve/main/joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx", 163_380, "eae9da0c7e1e6c6a3f4cc42d167899c388f6c6701b94cb96320e4f55df79624c"),
                ModelFile("tokens.txt", "$HF/mobilebytesensei/sherpa-onnx-kws-zipformer-gigaspeech-3.3M/resolve/main/tokens.txt", 5_006, "fd2ded4050a55d2b1578870ba8697d02371980217806b7558bd0a5cc60f3ba53"),
                ModelFile("bpe.model", "$HF/mobilebytesensei/sherpa-onnx-kws-zipformer-gigaspeech-3.3M/resolve/main/bpe.model", 244_837, "c8a2a0129c4ab8e463164c142f82d25649661b122c8cd0b7aab5c9e80b90ad24"),
            ),
            "Apache-2.0 (repository tag; training data GigaSpeech has its own terms — verify before commercial release)", "Verify GigaSpeech data terms", "English (custom phrases)", "sherpa-onnx", 80,
        ),
        ModelEntry(
            "asr-whisper-base-int8", ModelKind.ASR, "Speech recognition — Whisper Base multilingual (INT8)",
            "Offline ASR for Arabic (incl. dialect approximation), French and English with automatic language detection. ~160 MB. Tunisian Derja accuracy is limited — see the language benchmark.",
            listOf(
                ModelFile("base-encoder.int8.onnx", "$HF/csukuangfj/sherpa-onnx-whisper-base/resolve/main/base-encoder.int8.onnx", 29_120_534, "0b8fb1304b6109976038efff5ace81720e00386f3ff6b54ee8c75291ca0a1e11"),
                ModelFile("base-decoder.int8.onnx", "$HF/csukuangfj/sherpa-onnx-whisper-base/resolve/main/base-decoder.int8.onnx", 130_672_026, "9759d217388a01b3a4c7c15533201067b48ae819c4daafc8624e64b9409dc02d"),
                ModelFile("base-tokens.txt", "$HF/csukuangfj/sherpa-onnx-whisper-base/resolve/main/base-tokens.txt", 816_730, "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126"),
            ),
            "MIT (OpenAI Whisper weights)", "Yes", "Multilingual (99 languages); Arabic = MSA-biased", "sherpa-onnx", 450,
        ),
        ModelEntry(
            "llm-qwen3-1.7b-q4km", ModelKind.LLM, "Local brain — Qwen3 1.7B (Q4_K_M)",
            "Conversational model and tool-calling for requests the rules cannot handle. 1.1 GB. Requires the native llama.cpp runtime (see KNOWN_LIMITATIONS).",
            listOf(ModelFile("Qwen3-1.7B-Q4_K_M.gguf", "$HF/unsloth/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf", 1_107_409_472, "b139949c5bd74937ad8ed8c8cf3d9ffb1e99c866c823204dc42c0d91fa181897")),
            "Apache-2.0 (Qwen3)", "Yes", "100+ languages; Tunisian comprehension unverified", "llama.cpp", 2200,
        ),
    )
    fun byId(id: String) = entries.firstOrNull { it.id == id }
}
