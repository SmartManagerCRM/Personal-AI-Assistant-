# Architecture

```
:core (pure Kotlin/JVM — fully unit-tested)         :app (Android)                         :license-core / :license-generator
 language/  TextNormalizer, Lexicon, TimeParser,     engine/   AssistantController,         ECDSA P-256 signed, device-bound,
            TunisianLanguageProcessor                          AssistantService (FGS mic),   offline licenses; CLI generator
 intent/    IntentParser (deterministic)                      VoiceInput, EnergyBargeIn
 action/    ActionRegistry, RiskLevel, Outcome      assistant/ VoiceInteractionService/Session/RecognitionService, role helper
 conversation/ ConversationEngine, state machine,   audio/ MicHub (one mic owner), EnergyVad, UtteranceCollector
            Planner, Responses (TN/MSA/FR/EN)       wake/  WakeWordManager (sherpa KWS)    asr/ Whisper (sherpa), Android recognizer
 ai/        AIProvider, ProviderRouter,             tts/   AndroidTts                       actions/ PhoneActions, ContactResolver, …
            StructuredOutput, PromptBuilder         ai/    LlamaCppProvider (JNI), OpenAiCompatibleProvider
 wake/      SentencePieceEncoder                    models/ ModelCatalog, ModelManager      data/ Room, DataStore, MemoryRepository
                                                    ui/    Compose screens                  security/ Keystore  perm/ compat/ net/
```

**Dependency rule**: `:core` knows nothing about Android; the app implements its ports (`SpeechInput`, `SpeechOutput`, `BargeInSource`, `ActionHandler`, `PermissionChecker`, `AuditLog`, `AIProvider`). That is why the conversation, policy, planning and language logic could be tested here without a device.

## Turn pipeline
wake word (MicHub → sherpa KWS) → `AssistantController.converse` (stop spotter, show assistant session, load ASR) → `ConversationEngine.runSession`:
listen (VAD → Whisper | Android recognizer) → `TunisianLanguageProcessor` (normalise, detect language) → `Planner` (split multi-step) → `IntentParser` (rules) → if `UNKNOWN`: `ProviderRouter` → local LLM (structured JSON) → every action → `ActionRegistry.run` (slots → permission → prepare/resolve → confirm → execute → audit) → localized reply → TTS (with barge-in) → next turn without wake word, until silence timeout → standby (models released after 45 s).

## Hybrid intelligence
Rules first (open app, volume, call, reminder, camera…), LLM only for what rules cannot parse. Offline is the default: `ProviderRouter` consults online providers only if network master switch **and** hybrid mode **and** the provider are enabled by the user.

## Resource lifecycle
Standby: mic read loop + 6 MB spotter. Conversation: + VAD + Whisper (loaded on first use). Reasoning: llama.cpp model mmap'd on first LLM turn. Idle 45 s: Whisper and LLM released; spotter stays.
