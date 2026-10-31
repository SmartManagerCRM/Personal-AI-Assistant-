# Security and privacy review (self-audit, not independent)

* **No cloud backend / Supabase**: repo-wide search finds none; no analytics/crash SDKs.
* **Network**: single choke point `NetworkGate` (default OFF) for model downloads and the optional online provider; `usesCleartextTraffic=false`; online endpoint must be `https://`.
* **Secrets**: API key and Profile/Important memory encrypted with a non-exportable AES-256-GCM Android Keystore key. Notifications are held in memory only.
* **Action safety**: every operation is a named action with validated slots, required permissions and a risk level; unknown or restricted actions never reach a handler; the model cannot run arbitrary code; Level-3 topics are refused by text before the model is called and are never registered as executable; audit log of every action.
* **Exported components**: `MainActivity` (assist/voice intents), `VoiceInteractionService`/session/recognition services and the notification listener (all guarded by system bind permissions), `BootReceiver` (system broadcasts). Camera activity, mic service and reminder receiver are not exported.
* **Residual risks**: dev license key committed (rotate); `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is restricted on Google Play; Android's own recognizer (fallback) may use Google servers; wake-word false accepts open the microphone for a conversation (visible notification + overlay); the LLM can produce wrong content which the user hears — it never gets to act without the policy gate.
* Not done: independent review, dependency CVE scan, fuzzing of the intent parser.
