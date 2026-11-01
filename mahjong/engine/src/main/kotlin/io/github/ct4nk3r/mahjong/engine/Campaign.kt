package io.github.ct4nk3r.mahjong.engine

/**
 * One campaign level. Difficulty comes from four knobs: the layout (size and stacking),
 * how many different tile kinds appear ([faceKinds], null = the full 144-tile set), and the
 * hint and shuffle limits in [options]. Each level always deals the same (solvable) game.
 */
data class CampaignLevel(
    val number: Int,
    val layoutId: String,
    val faceKinds: Int?,
    val options: MahjongOptions,
) {
    val layout: Layout get() = Layouts.byId(layoutId)
    val seed: Long get() = number * 7_919L + 20_261_101L
}

object Campaign {
    private fun level(n: Int, layout: Layout, kinds: Int?, hints: Int? = null, shuffles: Int? = null) =
        CampaignLevel(n, layout.id, kinds, MahjongOptions(maxHints = hints, maxShuffles = shuffles))

    /** 25 levels, from a dozen tiles of three kinds to the full Turtle with no help at all. */
    val LEVELS: List<CampaignLevel> = listOf(
        // Easy: tiny boards, very few kinds of tile, unlimited help.
        level(1, Layouts.pyramid(4, 3, 1), kinds = 3),
        level(2, Layouts.pyramid(5, 4, 1), kinds = 4),
        level(3, Layouts.pyramid(4, 4, 2), kinds = 5),
        level(4, Layouts.pyramid(6, 4, 2), kinds = 6),
        level(5, Layouts.pyramid(8, 4, 2), kinds = 8),
        level(6, Layouts.pyramid(6, 6, 2), kinds = 8),
        level(7, Layouts.BEGINNER, kinds = 10),
        level(8, Layouts.pyramid(8, 6, 3), kinds = 12),
        // Medium: real layouts, more kinds, help starts to run out.
        level(9, Layouts.CROSS, kinds = 14),
        level(10, Layouts.BRIDGE, kinds = 16, hints = 5),
        level(11, Layouts.FORTRESS, kinds = 18, hints = 5),
        level(12, Layouts.TWIN_PEAKS, kinds = 20, hints = 5),
        level(13, Layouts.PYRAMID, kinds = 22, hints = 4),
        level(14, Layouts.DIAMOND, kinds = 24, hints = 4),
        level(15, Layouts.TURTLE, kinds = 26, hints = 4, shuffles = 3),
        // Hard: the full tile set, fewer and fewer hints and shuffles.
        level(16, Layouts.BRIDGE, kinds = null, hints = 3, shuffles = 3),
        level(17, Layouts.CROSS, kinds = null, hints = 3, shuffles = 2),
        level(18, Layouts.FORTRESS, kinds = null, hints = 3, shuffles = 2),
        level(19, Layouts.TWIN_PEAKS, kinds = null, hints = 2, shuffles = 2),
        level(20, Layouts.PYRAMID, kinds = null, hints = 2, shuffles = 1),
        level(21, Layouts.DIAMOND, kinds = null, hints = 2, shuffles = 1),
        level(22, Layouts.TURTLE, kinds = null, hints = 1, shuffles = 1),
        level(23, Layouts.TWIN_PEAKS, kinds = null, hints = 1, shuffles = 1),
        level(24, Layouts.DIAMOND, kinds = null, hints = 0, shuffles = 1),
        level(25, Layouts.TURTLE, kinds = null, hints = 0, shuffles = 0),
    )

    fun level(number: Int): CampaignLevel = LEVELS[number - 1]

    /** ★★★ without hints or shuffles, ★★ with at most one of them, ★ for finishing. */
    fun stars(hintsUsed: Int, shufflesUsed: Int): Int = when (hintsUsed + shufflesUsed) {
        0 -> 3
        1 -> 2
        else -> 1
    }
}
