package io.github.ct4nk3r.solitaire

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

@Serializable
enum class TableColor(val color: Color) {
    GREEN(Color(0xFF1E5E3A)),
    BLUE(Color(0xFF1D4E89)),
    RED(Color(0xFF6E1F2A)),
    GRAY(Color(0xFF37474F)),
    PURPLE(Color(0xFF4A2C6B)),
}

@Serializable
enum class CardBack(val color: Color) {
    BLUE(Color(0xFF1E4FA3)),
    RED(Color(0xFFB3261E)),
    GREEN(Color(0xFF2E7D32)),
    PURPLE(Color(0xFF6A1B9A)),
    BLACK(Color(0xFF263238)),
}

@Serializable
data class SolitaireSettings(
    /** Easy mode: tapping a card moves it to the best place. */
    val tapToMove: Boolean = false,
    /** Easy mode: cards that are no longer needed go to the foundations by themselves. */
    val autoMoveToFoundation: Boolean = false,
    /** Tap a column, then tap where it goes; the game picks the card to cut the stack at. */
    val tapToSelect: Boolean = true,
    /** Bigger, bolder rank and suit labels on the cards. */
    val largeLabels: Boolean = false,
    /** Diamonds blue and clubs green, so every suit has its own colour. */
    val fourColorDeck: Boolean = false,
    val winnableOnly: Boolean = true,
    val leftHanded: Boolean = false,
    val tableColor: TableColor = TableColor.GREEN,
    val cardBack: CardBack = CardBack.BLUE,
)
