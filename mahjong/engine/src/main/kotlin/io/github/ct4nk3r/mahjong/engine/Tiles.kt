package io.github.ct4nk3r.mahjong.engine

import kotlinx.serialization.Serializable

@Serializable
enum class TileSuit(val values: Int, val copies: Int) {
    DOTS(9, 4),
    BAMBOO(9, 4),
    CHARACTERS(9, 4),
    /** East, South, West, North */
    WINDS(4, 4),
    /** Red, Green, White */
    DRAGONS(3, 4),
    /** Plum, Orchid, Chrysanthemum, Bamboo: each tile is unique. */
    FLOWERS(4, 1),
    /** Spring, Summer, Autumn, Winter: each tile is unique. */
    SEASONS(4, 1),
}

@Serializable
data class TileFace(val suit: TileSuit, val value: Int) {
    val isBonus: Boolean get() = suit == TileSuit.FLOWERS || suit == TileSuit.SEASONS

    /** Two tiles can be paired when their match keys are equal. */
    fun matchKey(options: MahjongOptions): Int =
        if (isBonus && options.bonusTilesMatchWithinGroup) suit.ordinal * 100 else suit.ordinal * 100 + value

    companion object {
        /** The standard 144-tile set. */
        val FULL_SET: List<TileFace> = TileSuit.entries.flatMap { suit ->
            (1..suit.values).flatMap { value -> List(suit.copies) { TileFace(suit, value) } }
        }
    }
}
