package com.smartmanager.ai.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartmanager.ai.R
import com.smartmanager.ai.core.conversation.ConvState

@Composable
fun MainScreen(vm: AppViewModel, onSettings: () -> Unit, onStartStandby: () -> Unit, onTalk: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle(); val cfg by vm.cfg.collectAsStateWithLifecycle(); val perf by vm.perfStats.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LaunchedEffect(ui.transcript.size) { if (ui.transcript.isNotEmpty()) listState.animateScrollToItem(ui.transcript.size - 1) }
    var typed by remember { mutableStateOf("") }
    val statusRes = when (ui.conv) { ConvState.STANDBY -> R.string.state_standby; ConvState.WAKE_DETECTED -> R.string.state_wake; ConvState.LISTENING -> R.string.state_listening; ConvState.PROCESSING -> R.string.state_processing; ConvState.THINKING -> R.string.state_thinking
        ConvState.SPEAKING -> R.string.state_speaking; ConvState.EXECUTING -> R.string.state_executing; ConvState.WAITING_CONFIRMATION -> R.string.state_confirm; ConvState.ERROR -> R.string.state_error; ConvState.QUIET -> R.string.state_quiet; ConvState.SHUTDOWN -> R.string.state_shutdown }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AssistChip(onClick = {}, label = { Text(stringResource(if (ui.micOn) R.string.mic_on else R.string.mic_off)) }, leadingIcon = { Icon(if (ui.micOn) Icons.Filled.Mic else Icons.Filled.MicOff, null, tint = if (ui.micOn) MaterialTheme.colorScheme.secondary else Color.Gray) })
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, stringResource(R.string.settings)) }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.clickable { onTalk() }) { Orb(ui.conv, ui.micOn) }
        Text(stringResource(statusRes), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        if (ui.message.isNotBlank()) Text(ui.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        else Text(stringResource(R.string.main_hint, cfg.wakePhrase), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        if (perf.asrEngine.isNotBlank() || perf.lastError.isNotBlank()) Text(
            buildString { if (perf.asrEngine.isNotBlank()) append("ASR ${perf.asrEngine.removePrefix("asr-").take(24)} ${perf.asrLastMs} ms"); if (perf.replyMs >= 0) append(" · reply ${perf.replyMs} ms"); if (perf.lastDetectedLang.isNotBlank()) append(" · lang ${perf.lastDetectedLang}"); if (perf.lastError.isNotBlank()) append("\n⚠ ${perf.lastError}") },
            color = Color.Gray, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        if (ui.providerLabel.isNotBlank()) Text(ui.providerLabel, color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(ui.transcript) { l ->
                val rtl = l.text.any { it in '؀'..'ۿ' }
                CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (l.fromUser) Arrangement.End else Arrangement.Start) {
                        Surface(shape = RoundedCornerShape(16.dp), color = if (l.fromUser) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface, modifier = Modifier.widthIn(max = 320.dp)) { Text(l.text, Modifier.padding(12.dp)) }
                    }
                }
            }
        }
        OutlinedTextField(typed, { typed = it }, Modifier.fillMaxWidth(), placeholder = { Text(stringResource(R.string.type_command)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { if (typed.isNotBlank()) { vm.controller.typed(typed); typed = "" } }),
            trailingIcon = { IconButton(onClick = { if (typed.isNotBlank()) { vm.controller.typed(typed); typed = "" } }) { Icon(Icons.Filled.Send, stringResource(R.string.send)) } })
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            if (ui.standby) FilledTonalButton(onClick = { vm.controller.emergencyStop("") }, Modifier.weight(1f), colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF5A1F2A))) { Icon(Icons.Filled.PowerSettingsNew, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.emergency_stop)) }
            else Button(onClick = onStartStandby, Modifier.weight(1f)) { Icon(Icons.Filled.Hearing, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.start_standby)) }
            FilledIconButton(onClick = onTalk, Modifier.size(52.dp)) { Icon(Icons.Filled.Mic, null) }
        }
    }
}
