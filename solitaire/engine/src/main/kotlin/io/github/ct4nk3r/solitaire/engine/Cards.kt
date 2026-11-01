package io.github.ct4nk3r.solitaire.engine

import kotlinx.serialization.Serializable

@Serializable
enum class Suit(val isRed: Boolean) {
    CLUBS(false),
    DIAMONDS(true),
    HEARTS(true),
    SPADES(false),
}

@Serializable
data class Card(val suit: Suit, val rank: Int, val faceUp: Boolean = false) {
    /** Stable 0..51 identity, independent of face-up state. */
    val id: Int get() = suit.ordinal * 13 + rank - 1

    companion object {
        const val ACE = 1
        const val JACK = 11
        const val QUEEN = 12
        const val KING = 13
    }
}

@Serializable
enum class PileType { STOCK, WASTE, FOUNDATION, TABLEAU }

@Serializable
data class PileRef(val type: PileType, val index: Int = 0) {
    companion object {
        val STOCK = PileRef(PileType.STOCK)
        val WASTE = PileRef(PileType.WASTE)
        fun foundation(index: Int) = PileRef(PileType.FOUNDATION, index)
        fun tableau(index: Int) = PileRef(PileType.TABLEAU, index)
    }
}

sealed interface Move {
    /** Turn cards from the stock, or recycle the waste back into the stock when the stock is empty. */
    data object Draw : Move

    /** Move the cards from [index] to the top of pile [from] onto pile [to]. */
    data class Transfer(val from: PileRef, val index: Int, val to: PileRef) : Move
}
