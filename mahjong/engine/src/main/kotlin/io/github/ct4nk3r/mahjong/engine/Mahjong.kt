package io.github.ct4nk3r.mahjong.engine

import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Every rule that can vary lives here, so harder modes are only a different set of values.
 * The app currently ships [DEFAULT].
 */
@Serializable
data class MahjongOptions(
    /** Deals are generated so that at least one full solution exists. */
    val solvableDeals: Boolean = true,
    /** Shuffles rearrange the remaining tiles so that they stay solvable (when geometrically possible). */
    val solvableShuffles: Boolean = true,
    /** Maximum shuffles per game; `null` means unlimited. */
    val maxShuffles: Int? = null,
    /** Maximum hints per game; `null` means unlimited. Only used when [allowHints] is true. */
    val maxHints: Int? = null,
    /** Any flower matches any flower, any season matches any season (the traditional rule). */
    val bonusTilesMatchWithinGroup: Boolean = true,
    val allowUndo: Boolean = true,
    val allowHints: Boolean = true,
) {
    companion object {
        val DEFAULT = MahjongOptions()
        val HARD = MahjongOptions(solvableDeals = false, solvableShuffles = false, maxShuffles = 2)
    }
}

/** A game position. [faces] and [present] are indexed by the layout's position index. */
@Serializable
data class MahjongState(
    val layoutId: String,
    val seed: Long,
    val faces: List<TileFace>,
    val present: List<Boolean>,
    val moves: Int = 0,
    val shufflesUsed: Int = 0,
) {
    val remaining: Int get() = present.count { it }
    val isWon: Boolean get() = remaining == 0
}

class Mahjong(val options: MahjongOptions = MahjongOptions.DEFAULT) {

    /**
     * Deals [layout]. With [faceKinds] set, only that many different tiles are used (repeated as
     * needed), which makes matches plentiful: the main lever for easy campaign levels.
     */
    fun newGame(layout: Layout, seed: Long, faceKinds: Int? = null): MahjongState = deal(layout, seed, faceKinds, null)

    /** Deals a game; when [solution] is given, it receives a winning order of pairs (solvable deals only). */
    internal fun deal(layout: Layout, seed: Long, faceKinds: Int?, solution: MutableList<Pair<Int, Int>>?): MahjongState {
        val random = Random(seed)
        val pairs = if (faceKinds == null) {
            Dealer.pairs(TileFace.FULL_SET, options).shuffled(random).take(layout.size / 2)
        } else {
            val kinds = TileFace.FULL_SET.filter { !it.isBonus }.distinct().shuffled(random).take(faceKinds)
            List(layout.size / 2) { i -> kinds[i % kinds.size].let { it to it } }.shuffled(random)
        }
        val all = BooleanArray(layout.size) { true }
        val assignment = (if (options.solvableDeals) Dealer.solvableAssignment(layout, all, pairs, random, removalOrder = solution) else null)
            ?: Dealer.randomAssignment(all, pairs, random)
        return MahjongState(
            layoutId = layout.id,
            seed = seed,
            faces = List(layout.size) { assignment.getValue(it) },
            present = List(layout.size) { true },
        )
    }

    fun isFree(layout: Layout, state: MahjongState, i: Int): Boolean = layout.isFree(i, state.present.toBooleanArray())

    fun freeTiles(layout: Layout, state: MahjongState): List<Int> {
        val present = state.present.toBooleanArray()
        return layout.positions.indices.filter { layout.isFree(it, present) }
    }

    fun matches(state: MahjongState, a: Int, b: Int): Boolean =
        a != b && state.faces[a].matchKey(options) == state.faces[b].matchKey(options)

    fun canRemove(layout: Layout, state: MahjongState, a: Int, b: Int): Boolean =
        matches(state, a, b) && isFree(layout, state, a) && isFree(layout, state, b)

    fun remove(layout: Layout, state: MahjongState, a: Int, b: Int): MahjongState? {
        if (!canRemove(layout, state, a, b)) return null
        val present = state.present.toMutableList()
        present[a] = false
        present[b] = false
        return state.copy(present = present, moves = state.moves + 1)
    }

    /** Every pair of free, matching tiles. */
    fun availablePairs(layout: Layout, state: MahjongState): List<Pair<Int, Int>> =
        freeTiles(layout, state)
            .groupBy { state.faces[it].matchKey(options) }
            .values
            .flatMap { group -> group.indices.flatMap { i -> (i + 1 until group.size).map { j -> group[i] to group[j] } } }

    fun hint(layout: Layout, state: MahjongState): Pair<Int, Int>? = availablePairs(layout, state).firstOrNull()

    fun isStuck(layout: Layout, state: MahjongState): Boolean = !state.isWon && availablePairs(layout, state).isEmpty()

    fun shufflesRemaining(state: MahjongState): Int? = options.maxShuffles?.let { it - state.shufflesUsed }

    /** Hints left given how many were used, or null if unlimited. 0 when hints are switched off. */
    fun hintsRemaining(hintsUsed: Int): Int? = when {
        !options.allowHints -> 0
        else -> options.maxHints?.let { maxOf(0, it - hintsUsed) }
    }

    fun canShuffle(state: MahjongState): Boolean = !state.isWon && shufflesRemaining(state) != 0

    /** Rearranges the remaining tiles. Returns null if no shuffles are left. */
    fun shuffle(layout: Layout, state: MahjongState, random: Random): MahjongState? {
        if (!canShuffle(state)) return null
        val present = state.present.toBooleanArray()
        val pairs = Dealer.pairs(state.faces.filterIndexed { i, _ -> present[i] }, options)
        val assignment = (if (options.solvableShuffles) Dealer.solvableAssignment(layout, present, pairs, random) else null)
            ?: Dealer.randomAssignment(present, pairs, random)
        val faces = state.faces.mapIndexed { i, face -> assignment[i] ?: face }
        return state.copy(faces = faces, shufflesUsed = state.shufflesUsed + 1)
    }
}

internal object Dealer {
    /** Groups faces into matching pairs. Every match group must have an even count. */
    fun pairs(faces: List<TileFace>, options: MahjongOptions): List<Pair<TileFace, TileFace>> =
        faces.groupBy { it.matchKey(options) }.values.flatMap { group ->
            check(group.size % 2 == 0) { "Unpaired tiles: $group" }
            group.chunked(2).map { it[0] to it[1] }
        }

    /**
     * Assigns pairs by repeatedly taking two currently free positions and removing them.
     * The order of removal is therefore a valid solution, so the deal is guaranteed winnable.
     */
    fun solvableAssignment(
        layout: Layout,
        startPresent: BooleanArray,
        pairs: List<Pair<TileFace, TileFace>>,
        random: Random,
        attempts: Int = 300,
        removalOrder: MutableList<Pair<Int, Int>>? = null,
    ): Map<Int, TileFace>? {
        repeat(attempts) {
            val present = startPresent.copyOf()
            val result = HashMap<Int, TileFace>()
            removalOrder?.clear()
            var ok = true
            for ((a, b) in pairs.shuffled(random)) {
                val free = present.indices.filter { layout.isFree(it, present) }
                if (free.size < 2) {
                    ok = false
                    break
                }
                val i = free.random(random)
                val j = free.filter { it != i }.random(random)
                present[i] = false
                present[j] = false
                result[i] = a
                result[j] = b
                removalOrder?.add(i to j)
            }
            if (ok) return result
        }
        return null
    }

    fun randomAssignment(present: BooleanArray, pairs: List<Pair<TileFace, TileFace>>, random: Random): Map<Int, TileFace> {
        val slots = present.indices.filter { present[it] }.shuffled(random)
        val faces = pairs.flatMap { listOf(it.first, it.second) }
        return slots.zip(faces).toMap()
    }
}
