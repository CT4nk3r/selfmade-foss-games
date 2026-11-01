package io.github.ct4nk3r.solitaire.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KlondikeTest {
    private val game = Klondike()

    private fun c(suit: Suit, rank: Int, up: Boolean = true) = Card(suit, rank, up)

    private fun emptyState() = KlondikeState(
        seed = 0,
        stock = emptyList(),
        waste = emptyList(),
        foundations = List(4) { emptyList() },
        tableau = List(7) { emptyList() },
    )

    @Test
    fun dealHasEveryCardOnceInTheRightShape() {
        val s = game.deal(42)
        val all = s.stock + s.tableau.flatten()
        assertEquals(52, all.map { it.id }.toSet().size)
        assertEquals((1..7).toList(), s.tableau.map { it.size })
        assertTrue(s.tableau.all { pile -> pile.last().faceUp && pile.dropLast(1).none { it.faceUp } })
        assertEquals(24, s.stock.size)
        assertTrue(s.stock.none { it.faceUp })
    }

    @Test
    fun sameSeedSameDeal() {
        assertEquals(game.deal(7), game.deal(7))
    }

    @Test
    fun drawOneCyclesThroughStockWithUnlimitedRedeals() {
        var s = game.deal(1)
        val order = s.stock.reversed().map { it.id }
        repeat(24) { s = game.draw(s)!! }
        assertTrue(s.stock.isEmpty())
        assertEquals(order, s.waste.map { it.id })
        repeat(5) {
            s = game.draw(s)!! // recycle
            assertEquals(24, s.stock.size)
            repeat(24) { s = game.draw(s)!! }
        }
        assertEquals(order, s.waste.map { it.id })
        assertNull(game.redealsRemaining(s))
    }

    @Test
    fun drawThreeAndRedealLimitFromOptions() {
        val hard = Klondike(KlondikeOptions.DRAW_THREE_THREE_PASSES)
        var s = hard.deal(3)
        s = hard.draw(s)!!
        assertEquals(3, s.waste.size)
        assertEquals(21, s.stock.size)
        while (s.stock.isNotEmpty()) s = hard.draw(s)!!
        s = hard.draw(s)!!
        s = generateSequence(s) { if (it.stock.isNotEmpty()) hard.draw(it) else null }.last()
        s = hard.draw(s)!!
        s = generateSequence(s) { if (it.stock.isNotEmpty()) hard.draw(it) else null }.last()
        assertEquals(0, hard.redealsRemaining(s))
        assertFalse(hard.canDraw(s))
        assertNull(hard.draw(s))
    }

    @Test
    fun tableauRulesAlternateColourDescendingAndKingsOnEmpty() {
        val s = emptyState().copy(
            tableau = listOf(
                listOf(c(Suit.SPADES, 8)),
                listOf(c(Suit.HEARTS, 7)),
                listOf(c(Suit.CLUBS, 7)),
                listOf(c(Suit.DIAMONDS, 13)),
                emptyList(), emptyList(), emptyList(),
            ),
        )
        assertNotNull(game.move(s, PileRef.tableau(1), 0, PileRef.tableau(0)))
        assertNull(game.move(s, PileRef.tableau(2), 0, PileRef.tableau(0)))
        assertNull(game.move(s, PileRef.tableau(1), 0, PileRef.tableau(4)))
        assertNotNull(game.move(s, PileRef.tableau(3), 0, PileRef.tableau(4)))
    }

    @Test
    fun movingFromTableauFlipsAndScores() {
        val s = emptyState().copy(
            tableau = listOf(
                listOf(c(Suit.CLUBS, 5, up = false), c(Suit.HEARTS, 1)),
                emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            ),
        )
        val next = game.move(s, PileRef.tableau(0), 1, PileRef.foundation(0))!!
        assertTrue(next.tableau[0].single().faceUp)
        assertEquals(15, next.score) // +10 to foundation, +5 flip
        assertEquals(1, next.moves)
    }

    @Test
    fun runsMoveTogether() {
        val s = emptyState().copy(
            tableau = listOf(
                listOf(c(Suit.SPADES, 9), c(Suit.HEARTS, 8), c(Suit.CLUBS, 7)),
                listOf(c(Suit.DIAMONDS, 10)),
                emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            ),
        )
        val next = game.move(s, PileRef.tableau(0), 0, PileRef.tableau(1))!!
        assertEquals(4, next.tableau[1].size)
        assertTrue(next.tableau[0].isEmpty())
    }

    @Test
    fun columnToColumnFindsTheRightCardToCut() {
        // Column 0: a face-down card, then the run 10♠ 9♥ 8♣ 7♦.
        val s = emptyState().copy(
            tableau = listOf(
                listOf(c(Suit.CLUBS, 2, up = false), c(Suit.SPADES, 10), c(Suit.HEARTS, 9), c(Suit.CLUBS, 8), c(Suit.DIAMONDS, 7)),
                listOf(c(Suit.HEARTS, 10), c(Suit.SPADES, 9)), // needs a red 8: not in the run
                listOf(c(Suit.DIAMONDS, 11)), // J♦ takes the 10♠, cutting at index 1
                listOf(c(Suit.CLUBS, 10)), // 10♣ takes the 9♥, cutting at index 2
                emptyList(), emptyList(), emptyList(),
            ),
        )
        val from = PileRef.tableau(0)
        assertEquals(1, game.movableStart(s, from))
        assertNull(game.cutIndexFor(s, from, PileRef.tableau(1)))
        assertEquals(1, game.cutIndexFor(s, from, PileRef.tableau(2)))
        assertEquals(2, game.cutIndexFor(s, from, PileRef.tableau(3)))
        assertEquals(listOf(PileRef.tableau(2), PileRef.tableau(3)), game.destinationsFrom(s, from))

        val moved = game.move(s, from, game.cutIndexFor(s, from, PileRef.tableau(2))!!, PileRef.tableau(2))!!
        assertEquals(5, moved.tableau[2].size)
        assertTrue(moved.tableau[0].single().faceUp)
    }

    @Test
    fun columnToFoundationUsesOnlyTheTopCard() {
        val s = emptyState().copy(
            tableau = listOf(listOf(c(Suit.SPADES, 2), c(Suit.HEARTS, 1))) + List(6) { emptyList() },
        )
        assertEquals(1, game.cutIndexFor(s, PileRef.tableau(0), PileRef.foundation(0)))
        assertEquals(listOf(0, 1, 2, 3).map { PileRef.foundation(it) }, game.destinationsFrom(s, PileRef.tableau(0)).filter { it.type == PileType.FOUNDATION })
    }

    @Test
    fun tapPrefersFoundation() {
        val s = emptyState().copy(
            waste = listOf(c(Suit.HEARTS, 1)),
            tableau = listOf(listOf(c(Suit.SPADES, 2))) + List(6) { emptyList() },
        )
        assertEquals(PileType.FOUNDATION, game.bestTarget(s, PileRef.WASTE, 0)?.type)
    }

    @Test
    fun autoCompleteFinishesAnOpenGame() {
        // All cards face up in descending alternating runs: always winnable.
        var s = emptyState().copy(
            tableau = Suit.entries.map { suit -> (13 downTo 1).map { c(suit, it) } } + List(3) { emptyList() },
        )
        assertTrue(game.canAutoComplete(s))
        var steps = 0
        while (!s.isWon && steps < 200) {
            s = game.autoCompleteStep(s)!!
            steps++
        }
        assertTrue(s.isWon)
    }

    @Test
    fun hintSuggestsRevealingMoves() {
        val s = emptyState().copy(
            tableau = listOf(
                listOf(c(Suit.CLUBS, 3, up = false), c(Suit.HEARTS, 6)),
                listOf(c(Suit.SPADES, 7)),
            ) + List(5) { emptyList() },
        )
        assertEquals(Move.Transfer(PileRef.tableau(0), 1, PileRef.tableau(1)), game.hint(s))
    }

    @Test
    fun solverWinsMostDrawOneDeals() {
        val solver = KlondikeSolver(game)
        val results = (1L..20L).map { solver.solve(game.deal(it * 1_000_003)) }
        val solved = results.count { it == KlondikeSolver.Result.SOLVED }
        println("solver: $solved/20 solved, ${results.count { it == KlondikeSolver.Result.GAVE_UP }} gave up")
        assertTrue("solved only $solved of 20", solved >= 12)
    }

    @Test
    fun winnableSeedReallyIsWinnableAndDeterministic() {
        val solver = KlondikeSolver(game)
        val seed = solver.findWinnableSeed(123)
        assertEquals(seed, solver.findWinnableSeed(123))
        assertEquals(KlondikeSolver.Result.SOLVED, solver.solve(game.deal(seed)))
    }
}
