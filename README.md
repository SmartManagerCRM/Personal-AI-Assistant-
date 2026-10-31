# SmartManager Personal AI

Native Android (Kotlin, Compose, Hilt, Room) voice assistant for Tunisian Arabic, MSA, French and English: wake word, spoken conversation, deterministic phone actions, optional local LLM, offline-first, no cloud backend.

**Read `docs/PHASE_STATUS.md` and `docs/KNOWN_LIMITATIONS.md` first.** The code compiles, the logic is unit-tested (36 tests, 135-sentence intent benchmark) and the native LLM bridge was run against the real model on a Linux host — but **nothing has been run on an Android device**, so wake-word reliability, Tunisian speech recognition accuracy, battery behaviour and every OS-level feature are unverified.

```
./gradlew :app:assembleDebug            # APK in app/build/outputs/apk/debug
./gradlew :app:assembleRelease          # needs keystore.properties (see app/build.gradle.kts)
./gradlew test                          # core, license, app unit tests
./gradlew :license-generator:installDist   # offline license generator CLI
```
Prerequisites: JDK 17+, Android SDK 35, (for the local LLM) NDK 27.2.12479018 + CMake 3.22.1 and `scripts/fetch-llama-cpp.sh`. `scripts/fetch-sherpa-onnx.sh` runs automatically.

Docs: ARCHITECTURE, PHASE_STATUS, TEST_RESULTS, KNOWN_LIMITATIONS, ANDROID_COMPATIBILITY, LANGUAGE_BENCHMARK, PERFORMANCE_REPORT, MODEL_INSTALL, MODEL_LICENSES, USER_GUIDE, SECURITY_AUDIT, IMPLEMENTATION_LOG (all in `docs/`).
