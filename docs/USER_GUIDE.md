# User guide and installation

## Install
1. Enable *Install unknown apps* for your file manager/browser, copy `SmartManager-release.apk`, tap it. (Or `adb install SmartManager-release.apk`.)
2. Open the app, tap **Start listening**, allow Microphone and Notifications.
3. **Settings → Connected apps → Default assistant → Grant** (Android 10+: pick SmartManager in the dialog; Android 8–9: Settings → Default apps → Assist & voice input). Then the power-button long-press / assist gesture opens the assistant.
4. **Settings → AI models**: install the wake-word model and a speech model (see MODEL_INSTALL.md).
5. **Settings → Permissions**: grant what you want the assistant to do (contacts for calls/messages, camera, calendar, media for sharing the last photo, notification access for reading notifications).
6. **Settings → Battery & performance → Exempt from battery optimisation**, and on Infinix: Settings → Battery → App launch/Background activity → allow; Phone Master → Auto-start; lock the app in Recents.

## Use
* Say **"Hey Smart"** (or tap the orb). Keep talking — no need to repeat the wake word until the conversation times out (≈2 silent windows).
* Examples: «عيّط لأحمد» → «لقيت أحمد بن علي. تحب نعيّطلو؟» → «إي» · «ابعثلو مساج» · «حلّ الواتساب» · «صوّرلي» / «صوّرلي سيلفي» · «فكّرني غدوة على الساعة 8 …» · «شنوة عندي اليوم؟» · «اقرالي الإشعارات» · "appelle Sarah" · "remind me in 20 minutes to call mom" · "parle en français" / "speak English" / «تكلم بالتونسي».
* Calls, messages and photo sharing always ask you first. WhatsApp messages are prepared; **you press Send**. Money transfers, purchases and account changes are refused in every language.
* Interrupt the assistant by speaking while it talks (Voice → Barge-in). Say «اسكت» / "stop" to silence it, «اطفي المساعد» / "éteins l'assistant" to stop listening, «ما تسمعنيش 30 دقيقة» / "quiet mode" for a pause.
* The notification always shows when the microphone is open, with **Stop · Quiet 30 min · Talk**; the main screen has an emergency **Stop microphone** button.
* Memory: Settings → Memory (nicknames such as «ماما», preferences, retention, delete all). Activity history lists every action and whether you confirmed it.

## Privacy
Wake word, speech recognition (with the Whisper model), the local brain and memory run on the phone. Network is OFF by default; model downloads and the optional online provider only work after you allow network access. Standby audio is never uploaded.

## If recognition is poor (what to check)
1. **Which speech engine is active?** The main screen shows "ASR <engine> <ms>" after each turn. Default Whisper Base scores 93–99% WER on Tunisian speech (see LANGUAGE_BENCHMARK) — import the Tunisian Whisper Small (MODEL_INSTALL.md) or, if you accept network use, turn on Settings → Online providers → Allow network + Hybrid so Android's recognizer (Google ar-TN) is allowed to use its server model.
2. **Android recognizer silent or wrong?** It handles one language per turn (taken from Settings → Language) and, with network off, needs an *offline pack* (Google app → Voice → Offline speech recognition). The red line on the main screen names the missing pack.
3. **Microphone level**: Settings → Wake word shows RMS/peak. Peaks below ~2500 while speaking normally mean low gain: speak closer / report the numbers.
4. **Slow replies**: the first local-LLM answer after idle reads the whole prompt (several seconds on a phone); the app pre-loads speech and language models while it says "إي، نسمعك", and models stay loaded for 5 minutes. Open-ended Arabic/Tunisian answers from the 1.7B model are often wrong — commands (call, message, reminder, camera, volume…) do not use it.
