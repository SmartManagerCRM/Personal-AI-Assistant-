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
