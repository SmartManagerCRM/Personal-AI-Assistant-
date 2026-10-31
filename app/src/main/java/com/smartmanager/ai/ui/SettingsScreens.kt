@file:OptIn(ExperimentalMaterial3Api::class)

package com.smartmanager.ai.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartmanager.ai.R
import com.smartmanager.ai.assistant.AssistRole
import com.smartmanager.ai.compat.Compat
import com.smartmanager.ai.compat.Support
import com.smartmanager.ai.core.action.AppPermission
import com.smartmanager.ai.core.language.Lang
import com.smartmanager.ai.data.MemoryCategory
import com.smartmanager.ai.models.ModelCatalog
import com.smartmanager.ai.models.ModelKind
import com.smartmanager.license.LicenseStatus
import java.text.DateFormat
import java.util.Date

@Composable
private fun Screen(title: String, onBack: () -> Unit, content: LazyListScope.() -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 32.dp), content = content)
    }
}

@Composable private fun CardBox(content: @Composable ColumnScope.() -> Unit) = Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content) }
@Composable private fun Muted(t: String) = Text(t, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
@Composable private fun SwitchRow(label: String, checked: Boolean, desc: String = "", onChange: (Boolean) -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(label); if (desc.isNotBlank()) Muted(desc) }; Switch(checked, onChange) }

@Composable
fun SettingsHub(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val items = listOf("voice" to R.string.s_voice, "wake" to R.string.s_wake, "language" to R.string.s_language, "models" to R.string.s_models, "online" to R.string.s_online, "permissions" to R.string.s_permissions,
        "memory" to R.string.s_memory, "apps" to R.string.s_apps, "history" to R.string.s_history, "license" to R.string.s_license, "performance" to R.string.s_performance)
    Screen(stringResource(R.string.settings), onBack) { items(items) { (route, res) -> ListItem(headlineContent = { Text(stringResource(res)) }, trailingContent = { Icon(Icons.Filled.ChevronRight, null) }, modifier = Modifier.clickable { onOpen(route) }) } }
}

@Composable
fun VoiceScreen(vm: AppViewModel, onBack: () -> Unit) {
    val cfg by vm.cfg.collectAsStateWithLifecycle(); val st by vm.ttsStatus.collectAsStateWithLifecycle(); val voices by vm.voices.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshTts() }
    Screen(stringResource(R.string.s_voice), onBack) {
        item { CardBox {
            Text("Text-to-speech engine: ${st.engine.ifBlank { "—" }}")
            Text("Arabic voice: ${if (st.arabicAvailable) "available" else "NOT installed"} · Tunisian (ar-TN): ${if (st.tunisianAvailable) "available" else "not available (Tunisian wording is spoken with the best Arabic voice)"}", style = MaterialTheme.typography.bodySmall)
            if (!st.arabicAvailable) Muted("Install an Arabic voice: Android Settings → System → Languages → Text-to-speech → engine settings → Install voice data.")
        } }
        item { CardBox {
            Text("Speed ${"%.2f".format(cfg.ttsRate)}"); Slider(cfg.ttsRate, { v -> vm.update { it.copy(ttsRate = v) } }, valueRange = 0.5f..2f)
            Text("Pitch ${"%.2f".format(cfg.ttsPitch)}"); Slider(cfg.ttsPitch, { v -> vm.update { it.copy(ttsPitch = v) } }, valueRange = 0.5f..2f)
            SwitchRow("Barge-in (interrupt by speaking)", cfg.bargeIn, "Stops the assistant when you start talking. Uses echo cancellation where the phone offers it.") { b -> vm.update { it.copy(bargeIn = b) } }
        } }
        item { CardBox {
            Text("Test voice"); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.speakTest("إي، نسمعك. شنوة تحب نعمللك؟", Lang.TUNISIAN) }) { Text("Derja") }
                OutlinedButton(onClick = { vm.speakTest("مرحبًا، كيف يمكنني مساعدتك؟", Lang.MSA) }) { Text("MSA") }
                OutlinedButton(onClick = { vm.speakTest("Oui, je t'écoute.", Lang.FRENCH) }) { Text("FR") }
                OutlinedButton(onClick = { vm.speakTest("Yes, I'm listening.", Lang.ENGLISH) }) { Text("EN") }
            }
        } }
        item { Text("Voices (${voices.size})", style = MaterialTheme.typography.titleSmall) }
        items(voices.filter { it.locale.startsWith("ar") || it.locale.startsWith("fr") || it.locale.startsWith("en") }) { v ->
            ListItem(headlineContent = { Text(v.name, maxLines = 1) }, supportingContent = { Text("${v.locale} · ${if (v.offline) "offline" else "needs network"}") }, trailingContent = { if (cfg.ttsVoice == v.name) Icon(Icons.Filled.Check, stringResource(R.string.active)) },
                modifier = Modifier.clickable { vm.update { it.copy(ttsVoice = if (cfg.ttsVoice == v.name) "" else v.name) } })
        }
    }
}

@Composable
fun WakeScreen(vm: AppViewModel, onStartStandby: () -> Unit, onModels: () -> Unit, onBack: () -> Unit) {
    val cfg by vm.cfg.collectAsStateWithLifecycle(); val m by vm.wakeMetrics.collectAsStateWithLifecycle(); val ui by vm.ui.collectAsStateWithLifecycle()
    var phrase by remember(cfg.wakePhrase) { mutableStateOf(cfg.wakePhrase) }
    Screen(stringResource(R.string.s_wake), onBack) {
        item { CardBox {
            OutlinedTextField(phrase, { phrase = it }, Modifier.fillMaxWidth(), label = { Text("Wake phrase") }, singleLine = true)
            Muted("Changing the phrase re-targets an open-vocabulary spotter; it is NOT a trained model for that phrase. English letters only; check detection and tune sensitivity.")
            Button(onClick = { vm.update { it.copy(wakePhrase = phrase.trim()) }; if (ui.standby) onStartStandby() }, enabled = phrase.isNotBlank() && phrase.trim() != cfg.wakePhrase) { Text(stringResource(R.string.save)) }
        } }
        item { CardBox {
            Text("Sensitivity ${"%.0f".format(cfg.wakeSensitivity * 100)}%"); Slider(cfg.wakeSensitivity, { v -> vm.update { it.copy(wakeSensitivity = v) } }, valueRange = 0f..1f)
            Muted("Higher = catches more, but more false activations. Applies next time listening starts.")
        } }
        item { CardBox {
            Text("Engine: ${m.engine} — ${if (m.ready) "ready" else "not ready"}"); if (m.reason.isNotBlank()) Text(m.reason, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Text("Detections: ${m.detections} · suppressed: ${m.suppressed} · marked false: ${m.falsePositives}")
            Text("Latency (decode lag): last ${m.lastLatencyMs} ms · avg ${m.avgLatencyMs} ms")
            Text("Spotter threshold: ${"%.2f".format(m.threshold)}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.wake.markFalsePositive() }) { Text("Last was false") }
                OutlinedButton(onClick = onModels) { Text(stringResource(R.string.s_models)) }
            }
        } }
        item { CardBox {
            SwitchRow(if (ui.standby) "Listening for wake word" else "Wake-word listening is off", ui.standby, "Foreground service + notification while the microphone is open.") { on -> if (on) onStartStandby() else vm.controller.emergencyStop("") }
            Text("Quiet mode", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(15, 30, 60).forEach { mins -> OutlinedButton(onClick = { vm.controller.quiet(mins) }, enabled = ui.standby) { Text("$mins min") } } }
            if (ui.quietUntilMs > System.currentTimeMillis()) Muted("Quiet until ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ui.quietUntilMs))} — microphone is closed.")
        } }
    }
}

@Composable
fun LanguageScreen(vm: AppViewModel, onBack: () -> Unit) {
    val cfg by vm.cfg.collectAsStateWithLifecycle()
    val opts = listOf("auto" to R.string.lang_auto, Lang.TUNISIAN.name to R.string.lang_tn, Lang.MSA.name to R.string.lang_msa, Lang.FRENCH.name to R.string.lang_fr, Lang.ENGLISH.name to R.string.lang_en)
    Screen(stringResource(R.string.s_language), onBack) {
        item { Muted("Automatic: the assistant answers in the language you spoke (Tunisian, Arabic, French, English, or mixed Tunisian/French). Pick one to always answer in it. You can also say “parle en français”, “speak English”, “تكلم بالتونسي”.") }
        items(opts) { (k, r) -> ListItem(headlineContent = { Text(stringResource(r)) }, leadingContent = { RadioButton(cfg.language == k, onClick = { vm.update { it.copy(language = k) } }) }, modifier = Modifier.clickable { vm.update { it.copy(language = k) } }) }
        item { CardBox { OutlinedTextField(cfg.defaultCountryCode, { c -> vm.update { it.copy(defaultCountryCode = c.filter { ch -> ch.isDigit() }.take(4)) } }, label = { Text("Default country code for WhatsApp numbers") }, singleLine = true); Muted("Local numbers (e.g. 8 digits) get this prefix. Tunisia = 216.") } }
    }
}

@Composable
fun ModelsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cfg by vm.cfg.collectAsStateWithLifecycle(); val prog by vm.models.progress.collectAsStateWithLifecycle(); val rev by vm.models.revision.collectAsStateWithLifecycle()
    var importFor by remember { mutableStateOf<com.smartmanager.ai.models.ModelEntry?>(null) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val e = importFor; if (uri != null && e != null) {
            val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null } ?: ""
            scope.launch { status = vm.models.importFile(e, name, uri).fold({ "Imported $name" }, { "Import failed: ${it.message}" }) }
        }
    }
    Screen(stringResource(R.string.s_models), onBack) {
        item { CardBox { Text("Free space: ${vm.models.freeBytes() / 1_000_000} MB"); Muted("Models are never bundled and never download without your tap. Downloads need Online providers → “Allow network access”. Offline install: copy the files to the phone and use “Import file” (name and SHA-256 are verified)."); if (status.isNotBlank()) Text(status) } }
        items(ModelCatalog.entries) { e ->
            key(rev) {
                val installed = vm.models.isInstalled(e)
                CardBox {
                    Text(e.title, style = MaterialTheme.typography.titleSmall)
                    Muted(e.description)
                    Text("${e.kind} · ${e.totalBytes / 1_000_000} MB · RAM ≈ ${e.ramMb} MB · ${e.runtime}", style = MaterialTheme.typography.bodySmall)
                    Text("Languages: ${e.languages}", style = MaterialTheme.typography.bodySmall); Text("License: ${e.license}", style = MaterialTheme.typography.bodySmall); Text("Commercial use: ${e.commercialUse}", style = MaterialTheme.typography.bodySmall)
                    Text("Status: " + if (installed) "installed" else "${vm.models.installedBytes(e) / 1_000_000} / ${e.totalBytes / 1_000_000} MB present")
                    prog?.takeIf { it.modelId == e.id }?.let { p -> if (p.error != null) Text(p.error, color = MaterialTheme.colorScheme.error) else if (!p.finished) { LinearProgressIndicator(progress = { (p.doneBytes.toFloat() / maxOf(p.totalBytes, 1)).coerceIn(0f, 1f) }, Modifier.fillMaxWidth()); Muted("${p.file} ${p.doneBytes / 1_000_000}/${p.totalBytes / 1_000_000} MB") } }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!installed) Button(onClick = { vm.download(e) }) { Text(stringResource(R.string.install)) }
                        OutlinedButton(onClick = { importFor = e; picker.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.import_file)) }
                        if (installed || vm.models.installedBytes(e) > 0) OutlinedButton(onClick = { vm.models.remove(e) }) { Text(stringResource(R.string.remove)) }
                    }
                    if (installed) {
                        val active = when (e.kind) { ModelKind.ASR -> cfg.activeAsr == "sherpa-whisper-base"; ModelKind.LLM -> cfg.activeLlm == e.id; ModelKind.KWS -> cfg.activeKws == e.id; else -> false }
                        SwitchRow(stringResource(R.string.use) + " (" + stringResource(R.string.active) + ")", active) { on ->
                            vm.update { when (e.kind) { ModelKind.ASR -> it.copy(activeAsr = if (on) "sherpa-whisper-base" else "android"); ModelKind.LLM -> it.copy(activeLlm = if (on) e.id else ""); ModelKind.KWS -> it.copy(activeKws = if (on) e.id else ""); else -> it } }
                        }
                    }
                    if (e.kind == ModelKind.LLM && !vm.llama.nativeLoaded) Text("⚠ ${vm.llama.unavailableReason}", color = Color(0xFFFFC857), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}


@Composable
fun OnlineScreen(vm: AppViewModel, onBack: () -> Unit) {
    val cfg by vm.cfg.collectAsStateWithLifecycle()
    var key by remember { mutableStateOf("") }; var endpoint by remember(cfg.onlineEndpoint) { mutableStateOf(cfg.onlineEndpoint) }; var model by remember(cfg.onlineModel) { mutableStateOf(cfg.onlineModel) }
    Screen(stringResource(R.string.s_online), onBack) {
        item { CardBox {
            SwitchRow("Allow network access", cfg.networkAllowed, "Master switch. OFF (default) = the app never makes any network request, including model downloads.") { b -> vm.update { it.copy(networkAllowed = b, hybridMode = if (b) it.hybridMode else false) } }
            SwitchRow("Hybrid mode (online AI may help)", cfg.hybridMode, "Offline stays the default; the online provider is only asked when the local brain is unavailable.") { b -> vm.update { it.copy(hybridMode = b && it.networkAllowed) } }
            Muted("Standby microphone audio is never uploaded. Only the text of a request you spoke can be sent to the provider you enable, and the main screen shows which provider answered.")
        } }
        item { CardBox {
            SwitchRow("OpenAI-compatible provider", "openai-compatible" in cfg.enabledOnline, "Paid third-party services are used only if you enter your own API key here.") { b -> vm.update { it.copy(enabledOnline = if (b) it.enabledOnline + "openai-compatible" else it.enabledOnline - "openai-compatible") } }
            OutlinedTextField(endpoint, { endpoint = it }, Modifier.fillMaxWidth(), label = { Text("Base URL (https://…/v1)") }, singleLine = true)
            OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("Model name") }, singleLine = true)
            OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text(if (vm.openai.hasKey()) "API key (stored encrypted — type to replace)" else "API key") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            Button(onClick = { if (key.isNotBlank()) vm.openai.saveKey(key); key = ""; vm.update { it.copy(onlineEndpoint = endpoint.trim(), onlineModel = model.trim()) } }) { Text(stringResource(R.string.save)) }
        } }
    }
}

@Composable
fun PermissionsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) { val o = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) tick++ }; lifecycle.addObserver(o); onDispose { lifecycle.removeObserver(o) } }
    val desc = mapOf(AppPermission.MICROPHONE to "Wake word and voice commands (processed on the device)", AppPermission.CONTACTS to "Find people by name to call or message", AppPermission.PHONE to "Place calls directly (otherwise the dialer opens)",
        AppPermission.NOTIFICATIONS to "Show the listening notification and reminders", AppPermission.NOTIFICATION_ACCESS to "Read other apps' notifications aloud (granted in system settings)", AppPermission.CAMERA to "Take photos and videos by voice",
        AppPermission.MEDIA to "Find and share your last photo", AppPermission.CALENDAR to "Read your agenda", AppPermission.BLUETOOTH to "Reserved for Bluetooth settings shortcuts")
    Screen(stringResource(R.string.s_permissions), onBack) {
        items(vm.perms.manageable) { p ->
            key(tick) {
                val ok = vm.perms.isGranted(p)
                ListItem(headlineContent = { Text(p.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }) }, supportingContent = { Text(desc[p].orEmpty()) },
                    trailingContent = { if (ok) AssistChip(onClick = {}, label = { Text(stringResource(R.string.granted)) }) else Button(onClick = {
                        if (p == AppPermission.NOTIFICATION_ACCESS) ctx.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) else { val r = vm.perms.runtimePermissions(p); if (r.isEmpty()) ctx.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))) else launcher.launch(r.toTypedArray()) }
                    }) { Text(stringResource(R.string.grant)) } })
            }
        }
        item { CardBox { Text("Exact reminders"); Muted("Android 12+ needs “Alarms & reminders” allowed for on-time reminders."); if (Build.VERSION.SDK_INT >= 31) OutlinedButton(onClick = { ctx.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))) }) { Text("Open setting") } } }
        item { CardBox { Text("Risk levels"); Muted("Level 1 automatic: open apps, volume, reminders, reading information.\nLevel 2 asks you first: calls, messages, sharing photos.\nLevel 3 never automatic: money transfers, purchases, account changes — refused and logged.") } }
    }
}

@Composable
fun MemoryScreen(vm: AppViewModel, onBack: () -> Unit) {
    val cfg by vm.cfg.collectAsStateWithLifecycle(); val items by vm.memoryItems.collectAsStateWithLifecycle(emptyList()); val aliases by vm.aliases.collectAsStateWithLifecycle(emptyList())
    var cat by remember { mutableStateOf(MemoryCategory.PREFERENCE) }; var k by remember { mutableStateOf("") }; var v by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("") }; var contact by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Screen(stringResource(R.string.s_memory), onBack) {
        item { CardBox {
            Text("Retention of conversation history: ${if (cfg.retentionDays == 0) "keep forever" else "${cfg.retentionDays} days"}")
            Slider(cfg.retentionDays.toFloat(), { x -> vm.update { it.copy(retentionDays = x.toInt()) } }, valueRange = 0f..90f, steps = 17)
            Muted("0 = never delete. Older turns are purged daily. Profile and Important records are encrypted with the Android Keystore.")
        } }
        item { CardBox {
            Text("Add memory", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { MemoryCategory.all.forEach { c -> FilterChip(cat == c, { cat = c }, label = { Text(c.take(4)) }) } }
            OutlinedTextField(k, { k = it }, Modifier.fillMaxWidth(), label = { Text("Key (e.g. my name)") }, singleLine = true); OutlinedTextField(v, { v = it }, Modifier.fillMaxWidth(), label = { Text("Value") })
            Button(onClick = { scope.launch { vm.memory.put(cat, k.trim(), v.trim()) }; k = ""; v = "" }, enabled = k.isNotBlank() && v.isNotBlank()) { Text(stringResource(R.string.save)) }
        } }
        item { CardBox {
            Text("Contact aliases / nicknames", style = MaterialTheme.typography.titleSmall); Muted("e.g. “ماما” → the contact name in your phone book")
            OutlinedTextField(alias, { alias = it }, Modifier.fillMaxWidth(), label = { Text("Nickname") }, singleLine = true); OutlinedTextField(contact, { contact = it }, Modifier.fillMaxWidth(), label = { Text("Contact name") }, singleLine = true)
            Button(onClick = { scope.launch { vm.memory.addAlias(alias, contact) }; alias = ""; contact = "" }, enabled = alias.isNotBlank() && contact.isNotBlank()) { Text(stringResource(R.string.save)) }
        } }
        items(aliases) { a -> ListItem(headlineContent = { Text("${a.alias} → ${a.contactName}") }, trailingContent = { IconButton(onClick = { scope.launch { vm.memory.deleteAlias(a.alias) } }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) } }) }
        items(items) { m -> ListItem(headlineContent = { Text("${m.category} · ${m.key}") }, supportingContent = { Text(vm.memory.decode(m)) }, trailingContent = { IconButton(onClick = { scope.launch { vm.memory.delete(m.id) } }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) } }) }
        item { var confirm by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { if (confirm) { scope.launch { vm.memory.clearAll() }; confirm = false } else confirm = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(if (confirm) "Tap again to delete ALL memory and history" else "Delete all memory") } }
    }
}

@Composable
fun AppsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    fun installed(p: String) = try { ctx.packageManager.getPackageInfo(p, 0); true } catch (_: Exception) { false }
    val default = remember { mutableStateOf(AssistRole.isDefault(ctx)) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { default.value = AssistRole.isDefault(ctx) }
    val rows = listOf("WhatsApp" to installed("com.whatsapp"), "WhatsApp Business" to installed("com.whatsapp.w4b"), "Camera" to (ctx.packageManager.resolveActivity(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE), 0) != null),
        "Calendar" to (ctx.packageManager.resolveActivity(Intent(Intent.ACTION_VIEW, android.provider.CalendarContract.CONTENT_URI), 0) != null), "Clock / alarms" to (ctx.packageManager.resolveActivity(Intent(android.provider.AlarmClock.ACTION_SET_ALARM), 0) != null),
        "Notification access" to vm.perms.isGranted(AppPermission.NOTIFICATION_ACCESS), "Telephony" to ctx.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TELEPHONY))
    Screen(stringResource(R.string.s_apps), onBack) {
        item { CardBox {
            Text(stringResource(R.string.s_assistant), style = MaterialTheme.typography.titleSmall)
            Text(if (default.value) "SmartManager is the default digital assistant ✔" else "Not the default assistant yet")
            Muted(if (Build.VERSION.SDK_INT >= 29) "Android 10+: a system dialog lets you choose it. Then the assist gesture / power-button long-press starts the assistant." else "Android 8–9: choose it in Settings → Apps → Default apps → Assist & voice input.")
            Button(onClick = { roleLauncher.launch(AssistRole.requestIntent(ctx)) }) { Text(stringResource(R.string.grant)) }
        } }
        items(rows) { (n, ok) -> ListItem(headlineContent = { Text(n) }, trailingContent = { Text(if (ok) "✔" else "—") }) }
        item { Muted("WhatsApp: the assistant opens the chat with your text prepared. WhatsApp requires you to press Send; it cannot be sent silently, and the assistant tells you so.") }
    }
}

@Composable
fun HistoryScreen(vm: AppViewModel, onBack: () -> Unit) {
    val rows by vm.audit.collectAsStateWithLifecycle(emptyList()); val scope = rememberCoroutineScope()
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    Screen(stringResource(R.string.s_history), onBack) {
        item { OutlinedButton(onClick = { scope.launch { vm.db.dao().clearAudit() } }) { Text(stringResource(R.string.delete)) } }
        if (rows.isEmpty()) item { Muted("No actions yet.") }
        items(rows) { r -> ListItem(headlineContent = { Text("${r.action} → ${r.outcome}") }, supportingContent = { Text("${fmt.format(Date(r.timeMs))} · ${r.risk}${if (r.confirmed) " · confirmed" else ""} · ${r.summary}") }) }
    }
}

@Composable
fun LicenseScreen(vm: AppViewModel, onBack: () -> Unit) {
    val st by vm.licenseStatus.collectAsStateWithLifecycle(); var text by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.refreshLicense() }
    Screen(stringResource(R.string.s_license), onBack) {
        item { CardBox { Text("Device code"); SelectionContainer { Text(vm.license.deviceCode, style = MaterialTheme.typography.titleMedium) }; Muted("Send this code to get a license bound to this phone (validated offline; no server).") } }
        item { CardBox {
            Text(when (val s = st) { is LicenseStatus.Valid -> "Valid · ${s.payload.type}${if (s.payload.expiresAtSec > 0) " until " + DateFormat.getDateInstance().format(Date(s.payload.expiresAtSec * 1000)) else ""}"; LicenseStatus.Malformed -> "No valid license entered"; LicenseStatus.BadSignature -> "Invalid signature"
                is LicenseStatus.WrongDevice -> "License belongs to another device"; is LicenseStatus.Expired -> "License expired"; LicenseStatus.ClockTampered -> "System clock was moved back"; null -> "…" })
            Muted(if (com.smartmanager.ai.BuildConfig.ENFORCE_LICENSE) "This build requires a license." else "This build does not enforce licensing (evaluation).")
        } }
        item { CardBox { OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text("License text") }, minLines = 3); Button(onClick = { vm.activate(text) }, enabled = text.isNotBlank()) { Text("Activate") } } }
    }
}

@Composable
fun PerformanceScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val sys by vm.sys.collectAsStateWithLifecycle(); val p by vm.perfStats.collectAsStateWithLifecycle(); val w by vm.wakeMetrics.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshSystem() }
    Screen(stringResource(R.string.s_performance), onBack) {
        item { CardBox {
            sys?.let { s ->
                Text("RAM free ${s.ramFreeMb} / ${s.ramTotalMb} MB · app ${s.appPssMb} MB"); Text("Battery ${s.batteryPct}% ${if (s.charging) "(charging)" else ""} · ${"%.1f".format(s.batteryTempC)} °C · thermal: ${s.thermal}")
                Text("Battery optimisation: ${if (s.batteryOptIgnored) "app is exempt ✔" else "app is optimised (may be killed in standby)"}")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.refreshSystem() }) { Text("Refresh") }
                OutlinedButton(onClick = { ctx.startActivity(Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))) }) { Text("Exempt from battery optimisation") }
            }
        } }
        item { CardBox {
            Text("Speech recognition: ${p.asrEngine.ifBlank { "—" }} · last ${p.asrLastMs} ms · avg ${p.asrAvgMs} ms · model load ${p.whisperLoadMs} ms · detected language “${p.lastDetectedLang}”")
            Text("Wake word: last ${w.lastLatencyMs} ms · avg ${w.avgLatencyMs} ms · ${w.detections} detections")
            Text("Local LLM: ${if (vm.llama.nativeLoaded) "%.1f tok/s (est.)".format(vm.llama.lastTokensPerSecond) else vm.llama.unavailableReason}")
        } }
        item { CardBox {
            Text("Keep it alive on Infinix / Transsion (XOS), Xiaomi, Oppo, Vivo, Samsung", style = MaterialTheme.typography.titleSmall)
            Muted("1) Exempt from battery optimisation (button above).\n2) Infinix XOS: Settings → Battery → App launch / Background activity → SmartManager = Allow, and Phone Master → Auto-start = on; lock the app in the Recents screen.\n3) Samsung: Battery → Background usage limits → Never sleeping apps.\nAndroid or the maker may still stop apps after a force-stop, reboot or heavy memory pressure — the app cannot and does not bypass that. It tells you when it was stopped.")
        } }
        item { Text("Compatibility on this phone (API ${Compat.sdk})", style = MaterialTheme.typography.titleSmall) }
        items(Compat.capabilities()) { c -> ListItem(headlineContent = { Text(c.feature) }, supportingContent = { Text(c.note) }, trailingContent = { Text(when (c.support) { Support.FULL -> "✔ full"; Support.WITH_PERMISSION -> "permission"; Support.USER_INTERACTION -> "user step"; Support.MANUFACTURER_LIMITED -> "OEM-limited"; Support.UNAVAILABLE -> "n/a" }) }) }
    }
}
