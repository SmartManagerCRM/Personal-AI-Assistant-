package com.smartmanager.ai.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smartmanager.ai.core.conversation.ConvState

private val scheme = darkColorScheme(primary = Color(0xFF9D86FF), onPrimary = Color(0xFF0B0F1A), background = Color(0xFF0B0F1A), surface = Color(0xFF141A2B), surfaceVariant = Color(0xFF1C2338),
    onBackground = Color(0xFFE8EAF2), onSurface = Color(0xFFE8EAF2), secondary = Color(0xFF5CE1E6), error = Color(0xFFFF6B6B))

@Composable fun SmartTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, content = content)

/** Animated orb: breathes in standby, pulses faster when listening, ripples when speaking, spins a ring while thinking/executing. */
@Composable
fun Orb(state: ConvState, micOn: Boolean, size: Dp = 220.dp, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "orb")
    val speed = when (state) { ConvState.LISTENING, ConvState.WAKE_DETECTED -> 700; ConvState.SPEAKING -> 450; ConvState.THINKING, ConvState.PROCESSING, ConvState.EXECUTING -> 900; else -> 2600 }
    val pulse by t.animateFloat(0.92f, 1.08f, infiniteRepeatable(tween(speed, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "spin")
    val color = when (state) { ConvState.LISTENING, ConvState.WAKE_DETECTED -> Color(0xFF5CE1E6); ConvState.SPEAKING -> Color(0xFF9D86FF); ConvState.ERROR -> Color(0xFFFF6B6B); ConvState.WAITING_CONFIRMATION -> Color(0xFFFFC857); ConvState.QUIET, ConvState.SHUTDOWN -> Color(0xFF565C73); else -> Color(0xFF7C5CFF) }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = if (micOn) 0.55f else 0.25f), Color.Transparent), center, r * pulse * 1.15f), r * pulse * 1.15f)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), color, color.copy(alpha = 0.6f)), Offset(center.x - r * 0.2f, center.y - r * 0.25f), r), r * 0.55f * pulse)
            if (state == ConvState.THINKING || state == ConvState.PROCESSING || state == ConvState.EXECUTING) {
                val a = Math.toRadians(spin.toDouble()); val rr = r * 0.82f
                drawCircle(Color.White, 7f, Offset(center.x + (rr * Math.cos(a)).toFloat(), center.y + (rr * Math.sin(a)).toFloat()))
            }
        }
    }
}
