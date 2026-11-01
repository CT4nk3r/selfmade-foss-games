package io.github.ct4nk3r.games.core.common

import java.time.LocalDate
import java.util.Locale

/** Formats seconds as m:ss or h:mm:ss. */
fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.ROOT, "%d:%02d", m, s)
}

/** Deterministic seed for a daily challenge, identical on every device. */
object DailySeed {
    fun forDate(date: LocalDate, salt: Long): Long {
        var x = date.toEpochDay() * 0x9E3779B97F4A7C15uL.toLong() + salt
        x = (x xor (x ushr 30)) * -0x40a7b892e31b1a47L
        x = (x xor (x ushr 27)) * -0x6b2fb644ecceee15L
        return x xor (x ushr 31)
    }
}
