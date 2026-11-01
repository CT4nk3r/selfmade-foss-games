package io.github.ct4nk3r.games.core.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

enum class SoundEffect(val volume: Float) {
    /** Picking up / selecting a tile. */
    SELECT(0.6f),

    /** Two tiles matched: clack-clack and a chime. */
    MATCH(1f),

    /** A card landing. */
    CARD(0.8f),
    ERROR(0.45f),
    WIN(0.9f),
}

/**
 * Plays short sound effects. All sounds are synthesised in code (see [SoundSynth]) and cached
 * as WAV files, so the project ships no third-party audio.
 */
class GameSounds(private val context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val ids = IntArray(SoundEffect.entries.size)
    @Volatile private var loadedIds = emptySet<Int>()

    var enabled: Boolean = true

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) loadedIds = loadedIds + id }
    }

    /** Renders (first run only) and loads every effect. Call off the main thread. */
    fun load() {
        val dir = File(context.cacheDir, "sounds").apply { mkdirs() }
        SoundEffect.entries.forEach { effect ->
            val file = File(dir, "${effect.name.lowercase()}_v${SoundSynth.VERSION}.wav")
            if (!file.exists()) file.writeBytes(SoundSynth.toWav(SoundSynth.render(effect)))
            ids[effect.ordinal] = pool.load(file.path, 1)
        }
    }

    fun play(effect: SoundEffect) {
        val id = ids[effect.ordinal]
        if (!enabled || id !in loadedIds) return
        pool.play(id, effect.volume, effect.volume, 1, 0, 1f)
    }

    fun release() = pool.release()
}

@Composable
fun rememberGameSounds(enabled: Boolean): GameSounds {
    val context = LocalContext.current.applicationContext
    val sounds = remember { GameSounds(context) }
    LaunchedEffect(sounds) { withContext(Dispatchers.IO) { sounds.load() } }
    DisposableEffect(sounds) { onDispose { sounds.release() } }
    sounds.enabled = enabled
    return sounds
}

/** Tiny additive synthesiser for the game's sound effects. */
internal object SoundSynth {
    const val VERSION = 1
    private const val RATE = 44_100

    fun render(effect: SoundEffect): FloatArray = when (effect) {
        SoundEffect.SELECT -> buffer(0.09) { clack(it, 0.0, 0.7, 1.15) }
        SoundEffect.MATCH -> buffer(0.7) {
            clack(it, 0.0, 0.9, 1.0)
            clack(it, 0.07, 0.9, 0.92)
            bell(it, 0.11, 1318.5, 0.22, 0.5) // E6
            bell(it, 0.11, 1975.5, 0.14, 0.4) // B6
        }
        SoundEffect.CARD -> buffer(0.07) { flick(it) }
        SoundEffect.ERROR -> buffer(0.12) { thump(it) }
        SoundEffect.WIN -> buffer(1.1) {
            listOf(1046.5, 1318.5, 1568.0, 2093.0).forEachIndexed { i, f -> bell(it, i * 0.1, f, 0.2, 0.6) }
        }
    }

    private fun buffer(seconds: Double, block: (FloatArray) -> Unit): FloatArray {
        val out = FloatArray((seconds * RATE).toInt())
        block(out)
        // Gentle soft-clip so overlapping layers never distort harshly.
        for (i in out.indices) out[i] = tanh(out[i].toDouble()).toFloat()
        return out
    }

    /** A hard, woody tile knock: a click of noise plus quickly decaying resonances. */
    private fun clack(out: FloatArray, start: Double, amp: Double, pitch: Double) {
        val random = Random(42)
        val s0 = (start * RATE).toInt()
        val modes = listOf(2150.0 to 70.0, 3420.0 to 95.0, 5230.0 to 140.0)
        for (n in 0 until (0.08 * RATE).toInt()) {
            val i = s0 + n
            if (i >= out.size) break
            val t = n.toDouble() / RATE
            var v = 0.0
            modes.forEachIndexed { k, (f, d) -> v += sin(2 * PI * f * pitch * t) * exp(-t * d) / (k + 1) }
            if (n < RATE * 0.003) v += (random.nextDouble() * 2 - 1) * (1 - n / (RATE * 0.003))
            out[i] += (v * amp * 0.6).toFloat()
        }
    }

    /** A soft chime with a short attack. */
    private fun bell(out: FloatArray, start: Double, freq: Double, amp: Double, decay: Double) {
        val s0 = (start * RATE).toInt()
        for (n in 0 until (decay * 2.5 * RATE).toInt()) {
            val i = s0 + n
            if (i >= out.size) break
            val t = n.toDouble() / RATE
            val attack = minOf(1.0, t / 0.004)
            val v = (sin(2 * PI * freq * t) + 0.3 * sin(2 * PI * freq * 2.0 * t)) * exp(-t / decay * 3)
            out[i] += (v * amp * attack).toFloat()
        }
    }

    /** A card sliding onto the table: filtered noise burst. */
    private fun flick(out: FloatArray) {
        val random = Random(7)
        var low = 0.0
        for (n in out.indices) {
            val t = n.toDouble() / RATE
            low += 0.35 * ((random.nextDouble() * 2 - 1) - low)
            out[n] += (low * exp(-t * 70) * 0.9).toFloat()
        }
    }

    /** A low, quiet "no". */
    private fun thump(out: FloatArray) {
        for (n in out.indices) {
            val t = n.toDouble() / RATE
            out[n] += (sin(2 * PI * 150 * t) * exp(-t * 35) * 0.7).toFloat()
        }
    }

    fun toWav(samples: FloatArray): ByteArray {
        val dataSize = samples.size * 2
        val buf = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(RATE).putInt(RATE * 2).putShort(2).putShort(16)
        buf.put("data".toByteArray()).putInt(dataSize)
        samples.forEach { buf.putShort((it.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()) }
        return buf.array()
    }
}
