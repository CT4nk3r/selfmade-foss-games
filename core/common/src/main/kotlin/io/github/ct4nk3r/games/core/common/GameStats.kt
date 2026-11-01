package io.github.ct4nk3r.games.core.common

import kotlinx.serialization.Serializable

/** Statistics for one game variant (e.g. "Klondike, draw one" or "Mahjong, Turtle layout"). */
@Serializable
data class GameStats(
    val played: Int = 0,
    val won: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val bestTimeSeconds: Long? = null,
    val bestMoves: Int? = null,
    val bestScore: Int? = null,
    val totalTimeSeconds: Long = 0,
) {
    val winRate: Float get() = if (played == 0) 0f else won.toFloat() / played

    fun withWin(seconds: Long, moves: Int?, score: Int?): GameStats {
        val streak = currentStreak + 1
        return copy(
            played = played + 1,
            won = won + 1,
            currentStreak = streak,
            bestStreak = maxOf(bestStreak, streak),
            bestTimeSeconds = minOfNullable(bestTimeSeconds, seconds),
            bestMoves = if (moves == null) bestMoves else minOfNullable(bestMoves, moves),
            bestScore = if (score == null) bestScore else maxOf(bestScore ?: score, score),
            totalTimeSeconds = totalTimeSeconds + seconds,
        )
    }

    fun withLoss(seconds: Long): GameStats = copy(
        played = played + 1,
        currentStreak = 0,
        totalTimeSeconds = totalTimeSeconds + seconds,
    )

    private fun <T : Comparable<T>> minOfNullable(old: T?, new: T): T = if (old == null || new < old) new else old
}
