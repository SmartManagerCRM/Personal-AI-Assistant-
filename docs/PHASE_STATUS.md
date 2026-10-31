# Phase status

Legend: ✅ done and verified here · 🟡 implemented and compiled, **not yet verified on a device** · ⏳ not done

No physical Android device or emulator was available in the build environment. Everything that needs Android runtime behaviour is therefore 🟡, never ✅.

| Phase | Scope | Status | Evidence / what is missing |
|---|---|---|---|
| 0 Architecture | Gradle (Kotlin DSL) multi-module project, SDK 35 / min 26, docs | ✅ | `./gradlew :app:assembleDebug assembleRelease` succeed |
| 1 Assistant foundation | `VoiceInteractionService`, session service, `RecognitionService`, role request, permissions, compat layer, Compose UI | 🟡 | Manifest verified with aapt2. Selecting it as default assistant and the session overlay need a phone |
| 2 Wake word | sherpa-onnx KeywordSpotter (verified API), keyword file generated on device from the typed phrase, metrics, quiet mode, emergency stop, foreground mic service | 🟡 | Tokenizer ✅ (matches reference `sentencepiece`). Detection accuracy, latency, screen-off behaviour and battery: **pending device** |
| 3 Speech recognition | `AsrProvider` abstraction, Whisper via sherpa-onnx, Android recognizer fallback, energy VAD + utterance collector, language auto-detect (Whisper) | 🟡 | Model files + SHA-256 verified; no ASR run, no Tunisian recordings, **no WER measured**. See `LANGUAGE_BENCHMARK.md` |
| 4 Voice + conversation | Android TTS (voice discovery, rate/pitch, audio focus), state machine, barge-in, timeouts | ✅ engine logic / 🟡 audio | State machine, flows, barge-in and timeout tested with fakes (JVM). Real TTS/echo/barge-in on speaker: pending device |
| 5 Local AI brain | llama.cpp JNI bridge, Qwen3-1.7B Q4_K_M, ChatML prompt, structured actions, load/unload | ✅ host / 🟡 phone | Bridge compiled for arm64 (in APK) **and run on an x86_64 host with the real model** (`benchmarks/llm-host-validation.txt`). Phone speed/RAM/battery: pending device |
| 6 Tunisian intelligence | `TunisianLanguageProcessor`, normalization, Arabizi, code-switching, 135-sentence benchmark | ✅ (text level) | 135/135 on the intent benchmark (see caveats). **Not** an ASR benchmark |
| 7 Phone actions | contacts, calls, WhatsApp, camera (CameraX), gallery share, apps, notifications, reminders, alarms, agenda, volume, settings panels | 🟡 | Logic compiled and unit-tested where pure (contact matching, number formatting). Real behaviour pending device |
| 8 Memory & planning | Room memory, aliases, retention worker, Keystore encryption, multi-step planner, context ("him"), audit log, failure stops plan | ✅ planner/context / 🟡 persistence | Planner + context tested; Room/Keystore untested on device |
| 9 Offline/hybrid | network master switch (default OFF) through one gate, provider router, OpenAI-compatible optional provider | ✅ routing / 🟡 network | Router rules tested; no network call exists unless the user enables it |
| 10 Optimisation | Models unloaded after 45 s idle, perf screen (RAM, thermal, battery, latencies) | 🟡 | Instrumentation exists; **no profiling was possible** |
| 11 Compatibility | per-API capability matrix, fallbacks | 🟡 | Documentation + code paths only; **0 devices/emulators run** |
| 12 Delivery | release APK (signed), debug APK, license generator, docs | ✅ build / ⏳ device validation | See `TEST_RESULTS.md` |
