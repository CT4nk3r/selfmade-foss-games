package io.github.ct4nk3r.solitaire.engine

import kotlinx.serialization.Serializable

/** A complete, immutable Klondike position. In every list the top card is the last element. */
@Serializable
data class KlondikeState(
    val seed: Long,
    val stock: List<Card>,
    val waste: List<Card>,
    val foundations: List<List<Card>>,
    val tableau: List<List<Card>>,
    val redealsUsed: Int = 0,
    val score: Int = 0,
    val moves: Int = 0,
) {
    val isWon: Boolean get() = foundations.all { it.size == 13 }

    fun pile(ref: PileRef): List<Card> = when (ref.type) {
        PileType.STOCK -> stock
        PileType.WASTE -> waste
        PileType.FOUNDATION -> foundations[ref.index]
        PileType.TABLEAU -> tableau[ref.index]
    }

    internal fun withPile(ref: PileRef, cards: List<Card>): KlondikeState = when (ref.type) {
        PileType.STOCK -> copy(stock = cards)
        PileType.WASTE -> copy(waste = cards)
        PileType.FOUNDATION -> copy(foundations = foundations.replaced(ref.index, cards))
        PileType.TABLEAU -> copy(tableau = tableau.replaced(ref.index, cards))
    }

    /** Highest rank of [suit] already on a foundation (0 if none). */
    fun foundationRank(suit: Suit): Int = foundations.firstOrNull { it.firstOrNull()?.suit == suit }?.size ?: 0
}

private fun <T> List<T>.replaced(index: Int, value: T): List<T> = toMutableList().also { it[index] = value }
