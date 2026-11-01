package io.github.ct4nk3r.solitaire.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.github.ct4nk3r.solitaire.R
import io.github.ct4nk3r.solitaire.engine.Card
import io.github.ct4nk3r.solitaire.engine.Suit

private val RED = Color(0xFFC62828)
private val BLACK = Color(0xFF1B1B1B)
private val BLUE = Color(0xFF1565C0)
private val GREEN = Color(0xFF2E7D32)

/** How cards are drawn, from the appearance settings. */
data class CardStyle(val back: Color, val largeLabels: Boolean, val fourColors: Boolean)

/** Suit colour; the four-colour deck makes diamonds blue and clubs green. */
fun suitColor(suit: Suit, fourColors: Boolean): Color = when (suit) {
    Suit.HEARTS -> RED
    Suit.DIAMONDS -> if (fourColors) BLUE else RED
    Suit.CLUBS -> if (fourColors) GREEN else BLACK
    Suit.SPADES -> BLACK
}

// U+FE0E forces text (not emoji) rendering so suits take the card's colour.
fun Suit.symbol(): String = when (this) {
    Suit.CLUBS -> "♣︎"
    Suit.DIAMONDS -> "♦︎"
    Suit.HEARTS -> "♥︎"
    Suit.SPADES -> "♠︎"
}

/** Short corner labels, localised (e.g. J/Q/K in English, B/D/K in Hungarian). */
@Composable
fun rankLabels(): List<String> = listOf(stringResource(R.string.rank_short_ace)) +
    (2..10).map { it.toString() } +
    listOf(
        stringResource(R.string.rank_short_jack),
        stringResource(R.string.rank_short_queen),
        stringResource(R.string.rank_short_king),
    )

/** Full spoken name for TalkBack, e.g. "Queen of hearts" / "kőr dáma". */
@Composable
fun cardDescription(card: Card): String {
    if (!card.faceUp) return stringResource(R.string.card_face_down)
    val rank = stringArrayResource(R.array.rank_names)[card.rank - 1]
    val suit = stringArrayResource(R.array.suit_names)[card.suit.ordinal]
    return stringResource(R.string.card_description, rank, suit)
}

@Composable
fun CardView(
    card: Card,
    width: Dp,
    height: Dp,
    style: CardStyle,
    labels: List<String>,
    highlight: Color?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val rotation by animateFloatAsState(if (card.faceUp) 0f else 180f, tween(260), label = "flip")
    val shape = RoundedCornerShape(width * 0.09f)
    Box(
        modifier
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 14f * density.density
            }
            .clip(shape)
            .border(
                width = if (highlight != null) 3.5.dp else 0.5.dp,
                color = highlight ?: Color(0x66000000),
                shape = shape,
            ),
    ) {
        if (rotation < 90f) {
            CardFace(card, width, labels, style)
        } else {
            CardBackView(style.back)
        }
    }
}

@Composable
private fun CardFace(card: Card, width: Dp, labels: List<String>, style: CardStyle) {
    val color = suitColor(card.suit, style.fourColors)
    val density = LocalDensity.current
    // Large labels: about a third bigger and heavier, for players who find the corners hard to read.
    val scale = if (style.largeLabels) 1.35f else 1f
    fun size(fraction: Float): TextUnit = with(density) { (width * fraction * scale).toSp() }
    val tight = TextStyle(lineHeight = size(0.34f))
    val cornerWeight = if (style.largeLabels) FontWeight.ExtraBold else FontWeight.Bold
    Box(Modifier.fillMaxSize().background(Color(0xFFFDFDFB))) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = width * (if (style.largeLabels) 0.03f else 0.06f), vertical = width * 0.02f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                labels[card.rank - 1],
                color = color,
                fontSize = size(if (card.rank == 10) 0.25f else 0.31f),
                fontWeight = cornerWeight,
                style = tight,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                softWrap = false,
            )
            Text(card.suit.symbol(), color = color, fontSize = size(0.29f), style = tight, maxLines = 1)
        }
        Column(
            Modifier.align(Alignment.Center).padding(top = width * (if (style.largeLabels) 0.42f else 0.30f)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (card.rank >= Card.JACK) {
                Text(
                    labels[card.rank - 1],
                    color = color,
                    fontSize = size(0.46f / scale),
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(lineHeight = size(0.5f / scale)),
                )
            } else {
                Text(card.suit.symbol(), color = color, fontSize = size(0.56f / scale), style = TextStyle(lineHeight = size(0.6f / scale)))
            }
        }
    }
}

@Composable
private fun CardBackView(back: Color) {
    Canvas(Modifier.fillMaxSize().background(back)) {
        val inset = size.width * 0.08f
        val inner = Size(size.width - inset * 2, size.height - inset * 2)
        val line = Color.White.copy(alpha = 0.18f)
        clipRect(inset, inset, size.width - inset, size.height - inset) {
            val step = size.width * 0.14f
            var x = -size.height
            while (x < size.width + size.height) {
                drawLine(line, Offset(x, 0f), Offset(x + size.height, size.height), strokeWidth = step * 0.18f)
                drawLine(line, Offset(x + size.height, 0f), Offset(x, size.height), strokeWidth = step * 0.18f)
                x += step
            }
        }
        drawRoundRect(
            Color.White.copy(alpha = 0.55f),
            topLeft = Offset(inset, inset),
            size = inner,
            cornerRadius = CornerRadius(size.width * 0.05f),
            style = Stroke(width = size.width * 0.025f),
        )
    }
}

/** Outline shown where a pile is empty. */
@Composable
fun EmptySlot(width: Dp, height: Dp, label: String?, highlight: Color?, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val shape = RoundedCornerShape(width * 0.09f)
    Box(
        modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(if (highlight != null) 3.5.dp else 1.5.dp, highlight ?: Color.White.copy(alpha = 0.35f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) {
            Text(label, color = Color.White.copy(alpha = 0.45f), fontSize = with(density) { (width * 0.42f).toSp() })
        }
    }
}
