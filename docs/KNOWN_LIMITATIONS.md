# Known limitations and future work

## Honest status of the headline requirements
* **Always-on, screen-off wake word is unverified.** The design (foreground service of type microphone + sherpa-onnx spotter) is the standard approach, but nothing has run on a phone. Android 14/15 restrict starting a microphone foreground service from the background, and Android 15 forbids it from `BOOT_COMPLETED`; the app falls back to a one-tap notification. Force-stop, reboot and OEM task killers (Infinix XOS) can still stop it — it cannot and does not bypass that.
* **Tunisian speech recognition is weak, and only measured on a host.** The default Whisper Base scores 93–99% WER on Tunisian speech (see LANGUAGE_BENCHMARK). A converted Tunisian Whisper Small (INT8, 413 MB, import-only; build with `scripts/asr-conversion`) reaches 47% WER on TunSwitch (likely in-domain) and 64% on unseen podcast speech — the best available here, still not dictation quality, and untested on the phone (RAM ≈1.1 GB, speed unknown). The tiny Tunisian model is no better than stock Base on unseen speech.
* **The wake-word model is English-trained** (GigaSpeech). "Hey Smart" is English; Tunisian/French phrases are only approximately spotted. Arabic-script words in the phrase are converted to an approximate phonetic Latin spelling (`WakePhrase`, e.g. عسلامة → ASLAMA), which an English-trained model may or may not recognise — real-device tuning is needed. Changing the phrase is retargeting an open-vocabulary spotter, **not** training a model. No Arabic/Tunisian KWS model is available.
* **Local LLM quality for Arabic/Tunisian open-ended questions is poor** at 1.7B/Q4 (see TEST_RESULTS). Phone commands do not depend on it (deterministic parser + structured actions). A larger model (Qwen3-4B) or Gemma 3n E2B should be benchmarked on the device.
* **No Silero VAD, no sherpa TTS**: endpointing uses an adaptive energy VAD (fails in loud rooms); speech output uses Android TextToSpeech. `ar-TN` voices usually do not exist; Tunisian wording is spoken with the best Arabic voice, or not spoken properly if no Arabic voice is installed (the Voice screen says so). Piper/VITS Arabic voices were not evaluated.
* **Barge-in** relies on microphone echo cancellation where available plus a self-calibrating threshold; it may self-trigger on loud speakers. Turn it off in Voice settings if so.

## Android platform limits (by design, not bugs)
* WhatsApp: the app opens the chat with the text prepared; **you press Send**. Photos are shared via the share sheet.
* Calls: `ACTION_CALL` places the call (with permission); the app reports "dialling", and says it cannot verify the other party answered (OFFHOOK only proves dialling).
* Wi-Fi / Bluetooth / brightness: Android does not let apps toggle them; the right settings panel is opened.
* Background activity starts are blocked by Android 10+; the app uses the assistant session when active, the foreground when visible, otherwise a tap-notification.
* Exact reminders need "Alarms & reminders" allowed on Android 12+; otherwise delivery is approximate and the user is told.

## Not implemented
* Calendar event creation/update/cancel and notification *replies* (only reading the agenda and notifications is implemented).
* Location permission category (no feature needs it, never requested).
* Online providers other than a generic OpenAI-compatible endpoint; no online ASR/TTS.
* Local LLM on 32-bit ABIs and on CPUs without dot-product (the library is built armv8.2-a+dotprod; the app checks `/proc/cpuinfo` and disables it instead of crashing).
* Custom-phrase training workflow for a wake-word model (spotter retargeting only).
* Instrumented (on-device) tests and Android 8/10/12/13/14/15/16 test passes (see ANDROID_COMPATIBILITY.md).

## Security notes
* The committed license key pair is a **development key** (`license-generator/dev-keys`). Rotate it before distribution (README there).
* The release keystore generated for this build is local to the build machine and is not in git; sign your own for distribution.
* Licensing is not enforced in this build (`-PenforceLicense=true` turns it on).
