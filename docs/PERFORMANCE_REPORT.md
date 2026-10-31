# Battery and performance report

**Nothing was measured on a phone.** No battery, RAM, CPU or thermal profile exists. What exists:

* Design: only the wake-word spotter (≈6 MB model, 1 thread) and the microphone read loop run in standby. Whisper and the LLM load on demand and are released after 45 s idle. The spotter is reset every ~60 s to bound decoder state.
* Instrumentation in the app (Settings → Battery & performance): free/total RAM, app PSS, battery %, temperature, thermal status, battery-optimisation exemption, wake-word decode lag, ASR last/average latency, model load time, LLM throughput.
* Host-only data point: Qwen3-1.7B Q4_K_M answers a short request in ≈2–5 s on a 4-core x86 host (`benchmarks/llm-host-validation.txt`); **do not extrapolate to the Dimensity 8300**.

Estimated RAM when everything is loaded (model file sizes + runtime overhead, **unmeasured**): spotter ≈80 MB, Whisper Base ≈450 MB, Qwen3-1.7B Q4 ≈2.2 GB (mmap), dialectal large-v3-turbo ≈2.5 GB.

## To measure (pending)
`adb shell dumpsys batterystats --reset`; 8 h standby screen-off; wake-word ×50 for false accept/reject; `dumpsys meminfo com.smartmanager.ai`; Perfetto trace of a full turn; thermal status after 10 consecutive turns.
