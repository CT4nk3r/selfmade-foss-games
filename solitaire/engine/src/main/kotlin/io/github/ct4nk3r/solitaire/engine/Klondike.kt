package io.github.ct4nk3r.solitaire.engine

import kotlin.math.min
import kotlin.random.Random

/** Klondike rules. Stateless: every operation takes a state and returns a new one (or null if illegal). */
class Klondike(val options: KlondikeOptions = KlondikeOptions.DEFAULT) {

    fun deal(seed: Long): KlondikeState {
        val deck = Suit.entries
            .flatMap { suit -> (Card.ACE..Card.KING).map { Card(suit, it) } }
            .shuffled(Random(seed))
        var next = 0
        val tableau = List(TABLEAU_COUNT) { column ->
            List(column + 1) { row -> deck[next++].copy(faceUp = row == column) }
        }
        return KlondikeState(
            seed = seed,
            stock = deck.drop(next),
            waste = emptyList(),
            foundations = List(FOUNDATION_COUNT) { emptyList() },
            tableau = tableau,
            score = if (options.scoring == ScoringMode.VEGAS) VEGAS_START_SCORE else 0,
        )
    }

    // ---------------------------------------------------------------- stock

    /** Remaining passes through the stock, or null if unlimited. */
    fun redealsRemaining(state: KlondikeState): Int? = options.maxRedeals?.let { it - state.redealsUsed }

    fun canDraw(state: KlondikeState): Boolean =
        state.stock.isNotEmpty() || (state.waste.isNotEmpty() && redealsRemaining(state) != 0)

    fun draw(state: KlondikeState): KlondikeState? {
        if (state.stock.isNotEmpty()) {
            val n = min(options.drawCount, state.stock.size)
            // Turning a block of cards over reverses their order.
            val turned = state.stock.takeLast(n).reversed().map { it.copy(faceUp = true) }
            return state.copy(stock = state.stock.dropLast(n), waste = state.waste + turned, moves = state.moves + 1)
        }
        if (state.waste.isEmpty() || redealsRemaining(state) == 0) return null
        val penalty = if (options.scoring == ScoringMode.STANDARD) {
            if (options.drawCount == 1) RECYCLE_PENALTY_DRAW_ONE else RECYCLE_PENALTY_DRAW_THREE
        } else 0
        return state.copy(
            stock = state.waste.reversed().map { it.copy(faceUp = false) },
            waste = emptyList(),
            redealsUsed = state.redealsUsed + 1,
            score = clampScore(state.score + penalty),
            moves = state.moves + 1,
        )
    }

    // ---------------------------------------------------------------- transfers

    fun canPickUp(state: KlondikeState, from: PileRef, index: Int): Boolean {
        val pile = state.pile(from)
        if (index !in pile.indices) return false
        return when (from.type) {
            PileType.STOCK -> false
            PileType.WASTE -> index == pile.lastIndex
            PileType.FOUNDATION -> index == pile.lastIndex && options.allowFoundationToTableau
            PileType.TABLEAU -> isRun(pile, index)
        }
    }

    fun canPlace(state: KlondikeState, cards: List<Card>, from: PileRef, to: PileRef): Boolean {
        if (cards.isEmpty() || from == to) return false
        val first = cards.first()
        val target = state.pile(to)
        return when (to.type) {
            PileType.FOUNDATION -> {
                if (cards.size != 1 || from.type == PileType.FOUNDATION) return false
                val top = target.lastOrNull()
                if (top == null) first.rank == Card.ACE else top.suit == first.suit && first.rank == top.rank + 1
            }
            PileType.TABLEAU -> {
                val top = target.lastOrNull()
                if (top == null) !options.kingOnlyOnEmptyColumn || first.rank == Card.KING
                else top.faceUp && top.suit.isRed != first.suit.isRed && first.rank == top.rank - 1
            }
            else -> false
        }
    }

    fun move(state: KlondikeState, from: PileRef, index: Int, to: PileRef): KlondikeState? {
        if (!canPickUp(state, from, index)) return null
        val source = state.pile(from)
        val cards = source.subList(index, source.size).toList()
        if (!canPlace(state, cards, from, to)) return null

        var remaining = source.subList(0, index).toList()
        var score = state.score + transferScore(from.type, to.type)
        if (from.type == PileType.TABLEAU && remaining.isNotEmpty() && !remaining.last().faceUp) {
            remaining = remaining.dropLast(1) + remaining.last().copy(faceUp = true)
            if (options.scoring == ScoringMode.STANDARD) score += FLIP_SCORE
        }
        return state
            .withPile(from, remaining)
            .withPile(to, state.pile(to) + cards)
            .copy(score = clampScore(score), moves = state.moves + 1)
    }

    fun apply(state: KlondikeState, move: Move): KlondikeState? = when (move) {
        Move.Draw -> draw(state)
        is Move.Transfer -> move(state, move.from, move.index, move.to)
    }

    fun legalTransfers(state: KlondikeState): List<Move.Transfer> {
        val sources = buildList {
            if (state.waste.isNotEmpty()) add(PileRef.WASTE to state.waste.lastIndex)
            if (options.allowFoundationToTableau) {
                state.foundations.forEachIndexed { i, pile -> if (pile.isNotEmpty()) add(PileRef.foundation(i) to pile.lastIndex) }
            }
            state.tableau.forEachIndexed { i, pile ->
                pile.indices.filter { pile[it].faceUp }.forEach { add(PileRef.tableau(i) to it) }
            }
        }
        val targets = List(FOUNDATION_COUNT) { PileRef.foundation(it) } + List(TABLEAU_COUNT) { PileRef.tableau(it) }
        return buildList {
            for ((from, index) in sources) {
                if (!canPickUp(state, from, index)) continue
                val pile = state.pile(from)
                val cards = pile.subList(index, pile.size)
                for (to in targets) if (canPlace(state, cards, from, to)) add(Move.Transfer(from, index, to))
            }
        }
    }

    /** First index of the cards that can be picked up from [from] (the whole movable run), or null. */
    fun movableStart(state: KlondikeState, from: PileRef): Int? {
        val pile = state.pile(from)
        return when (from.type) {
            PileType.STOCK -> null
            PileType.TABLEAU -> pile.indices.firstOrNull { canPickUp(state, from, it) }
            else -> pile.lastIndex.takeIf { it >= 0 && canPickUp(state, from, it) }
        }
    }

    /**
     * For "tap a column, then tap where it goes": the card in [from] at which to cut the stack so the
     * cards fit on [to], or null if nothing fits. In Klondike at most one card of a run can fit a given
     * destination (its rank and colour are fixed), so the player never has to aim at a single card.
     */
    fun cutIndexFor(state: KlondikeState, from: PileRef, to: PileRef): Int? {
        val start = movableStart(state, from) ?: return null
        val pile = state.pile(from)
        return (start..pile.lastIndex).firstOrNull { canPlace(state, pile.subList(it, pile.size), from, to) }
    }

    /** Every pile that some cards of [from] can legally move to. */
    fun destinationsFrom(state: KlondikeState, from: PileRef): List<PileRef> =
        (List(FOUNDATION_COUNT) { PileRef.foundation(it) } + List(TABLEAU_COUNT) { PileRef.tableau(it) })
            .filter { cutIndexFor(state, from, it) != null }

    /** Where a tapped card should go: a foundation if possible, otherwise the most sensible column. */
    fun bestTarget(state: KlondikeState, from: PileRef, index: Int): PileRef? {
        if (!canPickUp(state, from, index)) return null
        val pile = state.pile(from)
        val cards = pile.subList(index, pile.size)
        if (cards.size == 1 && from.type != PileType.FOUNDATION) {
            foundationTarget(state, cards.first(), from)?.let { return it }
        }
        val columns = List(TABLEAU_COUNT) { PileRef.tableau(it) }.filter { canPlace(state, cards, from, it) }
        val movingWholeColumn = from.type == PileType.TABLEAU && index == 0
        return columns.firstOrNull { state.pile(it).isNotEmpty() }
            ?: columns.firstOrNull { !movingWholeColumn }
    }

    // ---------------------------------------------------------------- automation

    /**
     * A foundation move that can never hurt: the card is low enough that no other card
     * could still need it as a place to land.
     */
    fun nextSafeFoundationMove(state: KlondikeState): Move.Transfer? {
        for ((from, index) in topCards(state)) {
            val card = state.pile(from)[index]
            val to = foundationTarget(state, card, from) ?: continue
            if (isSafeToFoundation(state, card)) return Move.Transfer(from, index, to)
        }
        return null
    }

    /** True when the rest of the game can be played automatically. */
    fun canAutoComplete(state: KlondikeState): Boolean {
        if (state.isWon || state.tableau.any { pile -> pile.any { !it.faceUp } }) return false
        val stockEmpty = state.stock.isEmpty() && state.waste.isEmpty()
        return stockEmpty || (options.maxRedeals == null && options.drawCount == 1)
    }

    /** One step of auto-complete: a foundation move if any, otherwise a draw. */
    fun autoCompleteStep(state: KlondikeState): KlondikeState? {
        for ((from, index) in topCards(state)) {
            val card = state.pile(from)[index]
            val to = foundationTarget(state, card, from) ?: continue
            return move(state, from, index, to)
        }
        return if (canDraw(state)) draw(state) else null
    }

    /** Plays every safe foundation move. Used by the solver. */
    fun autoPlaySafe(state: KlondikeState): KlondikeState {
        var current = state
        while (true) {
            val m = nextSafeFoundationMove(current) ?: return current
            current = move(current, m.from, m.index, m.to) ?: return current
        }
    }

    // ---------------------------------------------------------------- hints

    /** The most useful move, [Move.Draw] if only drawing helps, or null if nothing useful is left. */
    fun hint(state: KlondikeState): Move? {
        val best = legalTransfers(state)
            .mapNotNull { m -> rate(state, m)?.let { m to it } }
            .maxByOrNull { it.second }
        if (best != null) return best.first
        return if (canDraw(state) && drawCanHelp(state)) Move.Draw else null
    }

    /**
     * How useful a transfer is (higher is better), or null when it achieves nothing.
     * Shared by hints and the solver.
     */
    internal fun rate(state: KlondikeState, m: Move.Transfer): Int? {
        if (m.from.type == PileType.FOUNDATION) return null
        val source = state.pile(m.from)
        val fromTableau = m.from.type == PileType.TABLEAU
        val revealsCard = fromTableau && m.index > 0 && !source[m.index - 1].faceUp
        val emptiesColumn = fromTableau && m.index == 0
        val toFoundation = m.to.type == PileType.FOUNDATION
        return when {
            toFoundation && revealsCard -> 100
            revealsCard -> 90 + m.index
            toFoundation && m.from.type == PileType.WASTE -> 80
            toFoundation -> 75
            m.from.type == PileType.WASTE -> 60
            emptiesColumn -> when {
                state.pile(m.to).isEmpty() -> null
                !options.kingOnlyOnEmptyColumn || kingWaiting(state) -> 50
                else -> null
            }
            // A move between columns that reveals nothing only helps if it frees a card for a foundation.
            fromTableau -> if (foundationTarget(state, source[m.index - 1], m.from) != null) 40 else null
            else -> null
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun isRun(pile: List<Card>, index: Int): Boolean {
        for (i in index until pile.size) {
            val card = pile[i]
            if (!card.faceUp) return false
            if (i > index) {
                val below = pile[i - 1]
                if (below.suit.isRed == card.suit.isRed || below.rank != card.rank + 1) return false
            }
        }
        return true
    }

    private fun foundationTarget(state: KlondikeState, card: Card, from: PileRef): PileRef? =
        List(FOUNDATION_COUNT) { PileRef.foundation(it) }.firstOrNull { canPlace(state, listOf(card), from, it) }

    private fun isSafeToFoundation(state: KlondikeState, card: Card): Boolean =
        card.rank <= 2 || Suit.entries
            .filter { it.isRed != card.suit.isRed }
            .all { state.foundationRank(it) >= card.rank - 1 }

    private fun topCards(state: KlondikeState): List<Pair<PileRef, Int>> = buildList {
        if (state.waste.isNotEmpty()) add(PileRef.WASTE to state.waste.lastIndex)
        state.tableau.forEachIndexed { i, pile -> if (pile.isNotEmpty()) add(PileRef.tableau(i) to pile.lastIndex) }
    }

    private fun kingWaiting(state: KlondikeState): Boolean =
        state.waste.lastOrNull()?.rank == Card.KING ||
            state.tableau.any { pile -> pile.withIndex().any { (i, c) -> i > 0 && c.faceUp && c.rank == Card.KING } }

    private fun drawCanHelp(state: KlondikeState): Boolean = (state.stock + state.waste).any { card ->
        val up = card.copy(faceUp = true)
        foundationTarget(state, up, PileRef.WASTE) != null ||
            (0 until TABLEAU_COUNT).any { canPlace(state, listOf(up), PileRef.WASTE, PileRef.tableau(it)) }
    }

    private fun transferScore(from: PileType, to: PileType): Int = when (options.scoring) {
        ScoringMode.STANDARD -> when {
            from == PileType.WASTE && to == PileType.TABLEAU -> 5
            from == PileType.WASTE && to == PileType.FOUNDATION -> 10
            from == PileType.TABLEAU && to == PileType.FOUNDATION -> 10
            from == PileType.FOUNDATION && to == PileType.TABLEAU -> -15
            else -> 0
        }
        ScoringMode.VEGAS -> when {
            to == PileType.FOUNDATION -> 5
            from == PileType.FOUNDATION -> -5
            else -> 0
        }
        ScoringMode.NONE -> 0
    }

    private fun clampScore(score: Int) = if (options.scoring == ScoringMode.STANDARD) maxOf(0, score) else score

    companion object {
        const val TABLEAU_COUNT = 7
        const val FOUNDATION_COUNT = 4
        const val VEGAS_START_SCORE = -52
        const val FLIP_SCORE = 5
        const val RECYCLE_PENALTY_DRAW_ONE = -100
        const val RECYCLE_PENALTY_DRAW_THREE = -20
    }
}
