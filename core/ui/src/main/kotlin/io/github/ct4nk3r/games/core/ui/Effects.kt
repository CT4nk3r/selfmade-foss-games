package io.github.ct4nk3r.games.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.sin
import kotlin.random.Random

class GameHaptics(private val haptic: HapticFeedback, private val enabled: Boolean) {
    fun move() {
        if (enabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun error() {
        if (enabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}

@Composable
fun rememberGameHaptics(enabled: Boolean): GameHaptics {
    val haptic = LocalHapticFeedback.current
    return remember(haptic, enabled) { GameHaptics(haptic, enabled) }
}

private class Particle(random: Random) {
    val x = random.nextFloat()
    val startY = -random.nextFloat()
    val speed = 0.25f + random.nextFloat() * 0.3f
    val wobble = 2f + random.nextFloat() * 3f
    val phase = random.nextFloat() * 6.28f
    val spin = random.nextFloat() * 360f
    val color = CONFETTI_COLORS[random.nextInt(CONFETTI_COLORS.size)]
}

private val CONFETTI_COLORS = listOf(
    Color(0xFFE53935), Color(0xFFFDD835), Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFFFF7043),
)

/** A short falling-confetti celebration drawn over the board. */
@Composable
fun Confetti(modifier: Modifier = Modifier) {
    val particles = remember { Random(System.nanoTime()).let { r -> List(140) { Particle(r) } } }
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (time < 7f) {
            withFrameNanos { now -> time = (now - start) / 1_000_000_000f }
        }
    }
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val pieceSize = Size(w * 0.018f, w * 0.010f)
        particles.forEach { p ->
            val y = (p.startY + p.speed * time) * h
            if (y < -pieceSize.height || y > h) return@forEach
            val x = (p.x + sin(time * p.wobble + p.phase) * 0.03f) * w
            rotate(p.spin + time * 180f, pivot = Offset(x, y)) {
                drawRect(p.color, topLeft = Offset(x, y), size = pieceSize)
            }
        }
    }
}
