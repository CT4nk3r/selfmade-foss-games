package io.github.ct4nk3r.solitaire.engine

/**
 * Depth-first Klondike solver with a node budget. It never moves cards off foundations
 * and skips moves that achieve nothing, which keeps the search small; "unsolvable"
 * therefore means "not solvable without those moves", which is fine for picking deals.
 */
class KlondikeSolver(
    private val game: Klondike,
    private val nodeLimit: Int = 40_000,
) {
    enum class Result { SOLVED, UNSOLVABLE, GAVE_UP }

    fun solve(start: KlondikeState): Result {
        val root = game.autoPlaySafe(start)
        if (root.isWon) return Result.SOLVED
        val visited = HashSet<Long>()
        visited += key(root)
        val stack = ArrayDeque<Iterator<KlondikeState>>()
        stack.addLast(children(root))
        var nodes = 0
        while (stack.isNotEmpty()) {
            val siblings = stack.last()
            if (!siblings.hasNext()) {
                stack.removeLast()
                continue
            }
            val next = game.autoPlaySafe(siblings.next())
            if (next.isWon) return Result.SOLVED
            if (!visited.add(key(next))) continue
            if (++nodes > nodeLimit) return Result.GAVE_UP
            stack.addLast(children(next))
        }
        return Result.UNSOLVABLE
    }

    /**
     * Returns the first seed (starting from [startSeed], stepping deterministically) whose deal
     * the solver can win, so the same start always yields the same deal on every device.
     */
    fun findWinnableSeed(startSeed: Long, attempts: Int = 30): Long {
        var seed = startSeed
        repeat(attempts) {
            if (solve(game.deal(seed)) == Result.SOLVED) return seed
            seed += SEED_STEP
        }
        return startSeed
    }

    private fun children(state: KlondikeState): Iterator<KlondikeState> {
        val ranked = game.legalTransfers(state)
            .mapNotNull { m -> game.rate(state, m)?.let { m to it } }
            .sortedByDescending { it.second }
            .map { it.first }
        return sequence {
            for (m in ranked) game.move(state, m.from, m.index, m.to)?.let { yield(it) }
            game.draw(state)?.let { yield(it) }
        }.iterator()
    }

    /** Position hash that ignores column order and score/move counters. */
    private fun key(s: KlondikeState): Long {
        var h = FNV_OFFSET
        s.tableau.map { pileHash(it) }.sorted().forEach { h = mix(h, it) }
        Suit.entries.forEach { h = mix(h, s.foundationRank(it).toLong()) }
        h = mix(h, pileHash(s.stock))
        h = mix(h, pileHash(s.waste))
        return h
    }

    private fun pileHash(cards: List<Card>): Long {
        var h = FNV_OFFSET
        for (c in cards) h = mix(h, (c.id * 2 + if (c.faceUp) 1 else 0).toLong())
        return mix(h, cards.size.toLong() + 1000)
    }

    private fun mix(h: Long, value: Long): Long = (h xor value) * FNV_PRIME

    private companion object {
        const val FNV_OFFSET = -0x340d631b7bdddcdbL
        const val FNV_PRIME = 0x100000001b3L
        const val SEED_STEP = 0x9E3779B9L
    }
}
