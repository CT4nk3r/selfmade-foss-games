package io.github.ct4nk3r.games.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CommonTest {
    @Test
    fun timelineUndoRedo() {
        var t = Timeline(1).push(2).push(3)
        assertEquals(3, t.current)
        t = t.undo().undo()
        assertEquals(1, t.current)
        assertFalse(t.canUndo)
        t = t.redo()
        assertEquals(2, t.current)
        t = t.push(9)
        assertEquals(9, t.current)
        assertFalse(t.canRedo)
        assertEquals(listOf(1, 2), t.past)
    }

    @Test
    fun timelineReplaceKeepsHistory() {
        val t = Timeline(1).push(2).replace(5)
        assertEquals(5, t.current)
        assertEquals(1, t.undo().current)
    }

    @Test
    fun statsTrackStreaksAndBests() {
        var s = GameStats()
        s = s.withWin(100, 50, 300)
        s = s.withWin(80, 60, 200)
        assertEquals(2, s.currentStreak)
        assertEquals(80L, s.bestTimeSeconds)
        assertEquals(50, s.bestMoves)
        assertEquals(300, s.bestScore)
        s = s.withLoss(30)
        assertEquals(0, s.currentStreak)
        assertEquals(2, s.bestStreak)
        assertEquals(3, s.played)
        assertEquals(210L, s.totalTimeSeconds)
        assertTrue(s.winRate in 0.66f..0.67f)
    }

    @Test
    fun durationFormat() {
        assertEquals("0:05", formatDuration(5))
        assertEquals("12:34", formatDuration(754))
        assertEquals("1:02:03", formatDuration(3723))
    }

    @Test
    fun dailySeedIsStablePerDay() {
        val d = LocalDate.of(2026, 11, 1)
        assertEquals(DailySeed.forDate(d, 1), DailySeed.forDate(d, 1))
        assertNotEquals(DailySeed.forDate(d, 1), DailySeed.forDate(d.plusDays(1), 1))
    }
}
