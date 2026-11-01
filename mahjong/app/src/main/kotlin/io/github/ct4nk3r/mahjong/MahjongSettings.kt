package io.github.ct4nk3r.mahjong

import androidx.compose.ui.graphics.Color
import io.github.ct4nk3r.mahjong.engine.Layouts
import kotlinx.serialization.Serializable

@Serializable
enum class Background(val color: Color) {
    GREEN(Color(0xFF1E5E3A)),
    BLUE(Color(0xFF1D4E89)),
    RED(Color(0xFF6E1F2A)),
    BROWN(Color(0xFF4E3524)),
    GRAY(Color(0xFF37474F)),
}

@Serializable
data class MahjongSettings(
    val highlightFree: Boolean = true,
    val showLabels: Boolean = true,
    val background: Background = Background.GREEN,
    val lastLayout: String = Layouts.TURTLE.id,
    /** Board zoom set with the zoom buttons; remembered so it only needs setting once. */
    val zoom: Float = 1f,
)
