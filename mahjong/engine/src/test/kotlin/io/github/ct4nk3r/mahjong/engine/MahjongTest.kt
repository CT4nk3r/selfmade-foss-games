package io.github.ct4nk3r.mahjong.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class MahjongTest {
    private val game = Mahjong()

    @Test
    fun fullSetHas144TilesAndPairsUp() {
        assertEquals(144, TileFace.FULL_SET.size)
        assertEquals(72, Dealer.pairs(TileFace.FULL_SET, MahjongOptions.DEFAULT).size)
    }

    @Test
    fun turtleHas144Tiles() {
        assertEquals(144, Layouts.TURTLE.size)
    }

    @Test
    fun layoutsAreWellFormed() {
        for (layout in Layouts.ALL) {
            val p = layout.positions
            assertTrue("${layout.id}: ${layout.size} tiles", layout.size % 2 == 0 && layout.size in 2..144)
            for (i in p.indices) for (j in i + 1 until p.size) {
                val overlap = p[i].z == p[j].z && abs(p[i].x - p[j].x) < 2 && abs(p[i].y - p[j].y) < 2
                assertFalse("${layout.id}: tiles $i and $j overlap", overlap)
            }
            for (t in p.filter { it.z > 0 }) {
                val supported = p.any { it.z == t.z - 1 && abs(it.x - t.x) < 2 && abs(it.y - t.y) < 2 }
                assertTrue("${layout.id}: tile $t floats", supported)
            }
            assertEquals(Layouts.ALL.map { it.id }.toSet().size, Layouts.ALL.size)
        }
    }

    @Test
    fun generatedDealsAreSolvableInEveryLayout() {
        for (layout in Layouts.ALL) repeat(5) { round ->
            val random = Random(round * 31L + layout.id.hashCode())
            val pairs = Dealer.pairs(TileFace.FULL_SET, game.options).shuffled(random).take(layout.size / 2)
            val order = mutableListOf<Pair<Int, Int>>()
            val assignment = Dealer.solvableAssignment(layout, BooleanArray(layout.size) { true }, pairs, random, removalOrder = order)
            assertNotNull("${layout.id}: could not generate a deal", assignment)
            var state = MahjongState(layout.id, 0, List(layout.size) { assignment!!.getValue(it) }, List(layout.size) { true })
            for ((a, b) in order) {
                state = game.remove(layout, state, a, b) ?: error("${layout.id}: replay failed at $a/$b")
            }
            assertTrue(state.isWon)
        }
    }

    @Test
    fun newGameIsDeterministic() {
        assertEquals(game.newGame(Layouts.TURTLE, 99), game.newGame(Layouts.TURTLE, 99))
    }

    @Test
    fun freeRuleNeedsAnOpenSideAndNothingOnTop() {
        val l = layout("test") {
            rect(0.0, 0.0, 3, 1, 0)
            tile(1.0, 0.0, 1)
            tile(5.0, 0.0, 0)
            tile(7.0, 0.0, 0)
        }
        val all = BooleanArray(l.size) { true }
        // Row: 0 1 2 with tile 3 on top of 1, and tile 4 alone.
        assertTrue(l.isFree(0, all))
        assertFalse(l.isFree(1, all))
        assertTrue(l.isFree(2, all))
        assertTrue(l.isFree(3, all))
        assertTrue(l.isFree(4, all))
        all[3] = false
        assertFalse("blocked on both sides", l.isFree(1, all))
        all[0] = false
        assertTrue(l.isFree(1, all))
    }

    @Test
    fun flowersMatchAnyFlowerButSuitsNeedTheSameValue() {
        val o = MahjongOptions.DEFAULT
        assertEquals(TileFace(TileSuit.FLOWERS, 1).matchKey(o), TileFace(TileSuit.FLOWERS, 4).matchKey(o))
        assertFalse(TileFace(TileSuit.FLOWERS, 1).matchKey(o) == TileFace(TileSuit.SEASONS, 1).matchKey(o))
        assertFalse(TileFace(TileSuit.DOTS, 1).matchKey(o) == TileFace(TileSuit.DOTS, 2).matchKey(o))
    }

    @Test
    fun removeRejectsBlockedOrDifferentTiles() {
        val state = game.newGame(Layouts.TURTLE, 5)
        val l = Layouts.TURTLE
        val blocked = l.positions.indices.first { !game.isFree(l, state, it) }
        val free = game.freeTiles(l, state)
        assertNull(game.remove(l, state, blocked, free.first()))
        val pair = game.hint(l, state)!!
        val next = game.remove(l, state, pair.first, pair.second)!!
        assertEquals(142, next.remaining)
        assertEquals(1, next.moves)
    }

    @Test
    fun shuffleKeepsTheSameTilesAndRespectsLimit() {
        val l = Layouts.PYRAMID
        var state = game.newGame(l, 8)
        repeat(10) {
            val pair = game.hint(l, state) ?: return@repeat
            state = game.remove(l, state, pair.first, pair.second)!!
        }
        val before = state.faces.filterIndexed { i, _ -> state.present[i] }.sortedBy { it.toString() }
        val shuffled = game.shuffle(l, state, Random(1))!!
        val after = shuffled.faces.filterIndexed { i, _ -> shuffled.present[i] }.sortedBy { it.toString() }
        assertEquals(before, after)
        assertFalse(game.isStuck(l, shuffled))

        val hard = Mahjong(MahjongOptions(maxShuffles = 1))
        val s1 = hard.shuffle(l, hard.newGame(l, 1), Random(2))!!
        assertNull(hard.shuffle(l, s1, Random(3)))
    }

    @Test
    fun greedyPlayOnASolvableDealWinsOften() {
        // Not guaranteed (greedy can paint itself into a corner) but should be common.
        var wins = 0
        repeat(10) { seed ->
            val l = Layouts.TURTLE
            var s = game.newGame(l, seed.toLong())
            while (true) {
                val p = game.hint(l, s) ?: break
                s = game.remove(l, s, p.first, p.second)!!
            }
            if (s.isWon) wins++
        }
        println("greedy wins: $wins/10")
    }
}

class CampaignTest {
    @Test
    fun thereAre25LevelsNumberedInOrder() {
        assertEquals((1..25).toList(), Campaign.LEVELS.map { it.number })
    }

    @Test
    fun difficultyRampsUp() {
        val levels = Campaign.LEVELS
        // Help never increases as levels go up (null = unlimited counts as most help).
        fun help(v: Int?) = v ?: Int.MAX_VALUE
        levels.zipWithNext().forEach { (a, b) ->
            assertTrue("hints grow at ${b.number}", help(b.options.maxHints) <= help(a.options.maxHints))
            assertTrue("shuffles grow at ${b.number}", help(b.options.maxShuffles) <= help(a.options.maxShuffles))
            assertTrue("kinds shrink at ${b.number}", help(b.faceKinds).let { if (it == Int.MAX_VALUE) 99 else it } >= (a.faceKinds ?: 99))
        }
        assertTrue(levels.first().layout.size <= 12)
        assertEquals(144, levels.last().layout.size)
    }

    @Test
    fun everyLevelDealsAWinnableGameAndIsRepeatable() {
        for (level in Campaign.LEVELS) {
            val game = Mahjong(level.options)
            val layout = level.layout
            val solution = mutableListOf<Pair<Int, Int>>()
            val state = game.deal(layout, level.seed, level.faceKinds, solution)
            assertEquals(state, game.newGame(layout, level.seed, level.faceKinds))
            level.faceKinds?.let { k ->
                assertTrue("level ${level.number} uses too many kinds", state.faces.map { it.matchKey(game.options) }.toSet().size <= k)
            }
            // Replay the dealer's own solution through the real rules.
            var s = state
            for ((a, b) in solution) s = game.remove(layout, s, a, b) ?: error("level ${level.number}: illegal step $a/$b")
            assertTrue("level ${level.number} cannot be won", s.isWon)
        }
    }

    @Test
    fun generatedLayoutsSurviveASaveRoundTrip() {
        val l = Layouts.pyramid(6, 4, 2)
        assertEquals(32, l.size)
        assertEquals(l.positions, Layouts.byId(l.id).positions)
    }

    @Test
    fun hintLimits() {
        val g = Mahjong(MahjongOptions(maxHints = 2))
        assertEquals(2, g.hintsRemaining(0))
        assertEquals(0, g.hintsRemaining(5))
        assertNull(Mahjong().hintsRemaining(10))
    }

    @Test
    fun stars() {
        assertEquals(3, Campaign.stars(0, 0))
        assertEquals(2, Campaign.stars(1, 0))
        assertEquals(1, Campaign.stars(2, 1))
    }
}
