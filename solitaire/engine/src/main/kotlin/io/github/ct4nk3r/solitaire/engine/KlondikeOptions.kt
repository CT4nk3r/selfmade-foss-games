package io.github.ct4nk3r.solitaire.engine

import kotlinx.serialization.Serializable

@Serializable
enum class ScoringMode {
    /** Windows-style: points for useful moves, penalty for recycling the waste. */
    STANDARD,

    /** Las Vegas: start at -52, +5 per card on a foundation. */
    VEGAS,
    NONE,
}

/**
 * Every rule that differs between Klondike variants lives here, so harder modes are only
 * a different set of values. The app currently ships [DEFAULT]; the other presets are
 * ready for when we expose difficulty settings.
 */
@Serializable
data class KlondikeOptions(
    /** Cards turned from the stock at a time: 1 (easy) or 3 (classic). */
    val drawCount: Int = 1,
    /** How many times the waste may be turned back into the stock. `null` means unlimited. */
    val maxRedeals: Int? = null,
    val scoring: ScoringMode = ScoringMode.STANDARD,
    /** Only a King (or a run headed by a King) may be placed on an empty column. */
    val kingOnlyOnEmptyColumn: Boolean = true,
    /** Cards may be taken back from a foundation onto the tableau. */
    val allowFoundationToTableau: Boolean = true,
    val allowUndo: Boolean = true,
    val allowHints: Boolean = true,
) {
    init {
        require(drawCount in 1..3) { "drawCount must be 1..3" }
        require(maxRedeals == null || maxRedeals >= 0) { "maxRedeals must be null or >= 0" }
    }

    /** Statistics key; games with different rules are tracked separately. */
    val statsKey: String
        get() = "klondike-d$drawCount-r${maxRedeals ?: "inf"}-${scoring.name.lowercase()}"

    companion object {
        /** Draw one card, unlimited passes through the stock. */
        val DEFAULT = KlondikeOptions()

        val DRAW_THREE = KlondikeOptions(drawCount = 3)
        val DRAW_THREE_THREE_PASSES = KlondikeOptions(drawCount = 3, maxRedeals = 2)
        val VEGAS_DRAW_ONE = KlondikeOptions(drawCount = 1, maxRedeals = 0, scoring = ScoringMode.VEGAS)
        val VEGAS_DRAW_THREE = KlondikeOptions(drawCount = 3, maxRedeals = 2, scoring = ScoringMode.VEGAS)
    }
}
