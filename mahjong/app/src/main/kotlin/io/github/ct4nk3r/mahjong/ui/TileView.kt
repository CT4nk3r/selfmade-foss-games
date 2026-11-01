package io.github.ct4nk3r.mahjong.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.github.ct4nk3r.mahjong.R
import io.github.ct4nk3r.mahjong.engine.TileFace
import io.github.ct4nk3r.mahjong.engine.TileSuit

private val IVORY = Color(0xFFFBF5E6)
private val SIDE = Color(0xFFD9BE86)
private val SIDE_DARK = Color(0xFFA9864B)
private val INK_BLUE = Color(0xFF1E4FA3)
private val INK_RED = Color(0xFFC62828)
private val INK_GREEN = Color(0xFF2E7D32)
private val INK_BLACK = Color(0xFF1B1B1B)
private val SELECTED = Color(0xFFFFC107)
private val HINTED = Color(0xFF29B6F6)

private val NUMERALS = listOf("一", "二", "三", "四", "五", "六", "七", "八", "九")
private val WINDS = listOf("東", "南", "西", "北")
private val FLOWERS = listOf("梅", "蘭", "菊", "竹")
private val SEASONS = listOf("春", "夏", "秋", "冬")

@Composable
fun TileView(
    face: TileFace,
    width: Dp,
    height: Dp,
    depth: Dp,
    selected: Boolean,
    hinted: Boolean,
    dimmed: Boolean,
    showLabel: Boolean,
    onTap: (() -> Unit)?,
) {
    val shape = RoundedCornerShape(width * 0.12f)
    val description = tileDescription(face)
    val label = if (showLabel) tileLabel(face) else null
    Box(Modifier.size(width + depth, height + depth)) {
        // The tile's thickness, drawn down and to the right of the face.
        Canvas(Modifier.matchParentSize()) {
            val d = depth.toPx()
            val r = CornerRadius(width.toPx() * 0.12f)
            val faceSize = Size(width.toPx(), height.toPx())
            drawRoundRect(SIDE_DARK, topLeft = Offset(d, d), size = faceSize, cornerRadius = r)
            drawRoundRect(SIDE, topLeft = Offset(d * 0.5f, d * 0.5f), size = faceSize, cornerRadius = r)
        }
        Box(
            Modifier
                .size(width, height)
                .clip(shape)
                .background(IVORY)
                .border(
                    width = if (selected || hinted) 3.dp else 0.75.dp,
                    color = when {
                        selected -> SELECTED
                        hinted -> HINTED
                        else -> Color(0x885D4A2A)
                    },
                    shape = shape,
                )
                .semantics { contentDescription = description }
                .then(
                    if (onTap == null) Modifier
                    else Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onTap,
                    ),
                ),
        ) {
            TileArt(face, height, Modifier.fillMaxSize().padding(horizontal = width * 0.1f, vertical = height * 0.08f))
            if (label != null) {
                Text(
                    label,
                    color = Color(0xFF5D4A2A),
                    fontSize = with(LocalDensity.current) { (height * 0.15f).toSp() },
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                    modifier = Modifier.align(Alignment.TopStart).padding(start = width * 0.06f, top = height * 0.02f),
                )
            }
            if (selected) Box(Modifier.matchParentSize().background(SELECTED.copy(alpha = 0.25f)))
            if (dimmed) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.30f)))
        }
    }
}

@Composable
private fun TileArt(face: TileFace, tileHeight: Dp, modifier: Modifier) {
    val density = LocalDensity.current
    fun sp(fraction: Float): TextUnit = with(density) { (tileHeight * fraction).toSp() }
    val glyphStyle = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false), lineHeight = sp(0.5f))
    when (face.suit) {
        TileSuit.DOTS -> Canvas(modifier) { drawDots(face.value) }
        TileSuit.BAMBOO -> Canvas(modifier) { drawBamboo(face.value) }
        TileSuit.CHARACTERS -> Column(
            modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(NUMERALS[face.value - 1], color = INK_BLACK, fontSize = sp(0.28f), style = glyphStyle.copy(lineHeight = sp(0.32f)))
            Text("萬", color = INK_RED, fontSize = sp(0.32f), style = glyphStyle.copy(lineHeight = sp(0.36f)))
        }
        TileSuit.WINDS -> Glyph(WINDS[face.value - 1], INK_BLACK, sp(0.46f), glyphStyle, modifier)
        TileSuit.DRAGONS -> when (face.value) {
            1 -> Glyph("中", INK_RED, sp(0.46f), glyphStyle, modifier)
            2 -> Glyph("發", INK_GREEN, sp(0.46f), glyphStyle, modifier)
            else -> Canvas(modifier) {
                val inset = size.width * 0.12f
                drawRoundRect(
                    INK_BLUE,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius(size.width * 0.08f),
                    style = Stroke(size.width * 0.09f),
                )
                drawRoundRect(
                    INK_BLUE,
                    topLeft = Offset(inset * 2.2f, inset * 2.2f),
                    size = Size(size.width - inset * 4.4f, size.height - inset * 4.4f),
                    cornerRadius = CornerRadius(size.width * 0.04f),
                    style = Stroke(size.width * 0.04f),
                )
            }
        }
        TileSuit.FLOWERS -> Glyph(FLOWERS[face.value - 1], Color(0xFFAD1457), sp(0.42f), glyphStyle, modifier)
        TileSuit.SEASONS -> Glyph(SEASONS[face.value - 1], Color(0xFF00796B), sp(0.42f), glyphStyle, modifier)
    }
}

@Composable
private fun Glyph(text: String, color: Color, size: TextUnit, style: TextStyle, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(text, color = color, fontSize = size, style = style)
    }
}

// Fractional centres of the pips, per value.
private val DOT_LAYOUTS: List<List<Pair<Float, Float>>> = listOf(
    listOf(.5f to .5f),
    listOf(.5f to .27f, .5f to .73f),
    listOf(.22f to .2f, .5f to .5f, .78f to .8f),
    listOf(.28f to .28f, .72f to .28f, .28f to .72f, .72f to .72f),
    listOf(.25f to .22f, .75f to .22f, .5f to .5f, .25f to .78f, .75f to .78f),
    listOf(.28f to .2f, .72f to .2f, .28f to .5f, .72f to .5f, .28f to .8f, .72f to .8f),
    listOf(.2f to .13f, .5f to .24f, .8f to .35f, .28f to .62f, .72f to .62f, .28f to .87f, .72f to .87f),
    listOf(.28f to .13f, .72f to .13f, .28f to .38f, .72f to .38f, .28f to .62f, .72f to .62f, .28f to .87f, .72f to .87f),
    listOf(.2f to .17f, .5f to .17f, .8f to .17f, .2f to .5f, .5f to .5f, .8f to .5f, .2f to .83f, .5f to .83f, .8f to .83f),
)
private val DOT_RADIUS = listOf(.36f, .2f, .17f, .18f, .16f, .15f, .13f, .13f, .13f)
private val DOT_COLORS = listOf(INK_BLUE, INK_GREEN, INK_RED)

private fun DrawScope.drawDots(value: Int) {
    val r = size.width * DOT_RADIUS[value - 1]
    DOT_LAYOUTS[value - 1].forEachIndexed { i, (fx, fy) ->
        val c = Offset(size.width * fx, size.height * fy)
        val color = if (value == 1) INK_RED else DOT_COLORS[(i + value) % DOT_COLORS.size]
        drawCircle(color, r, c)
        drawCircle(IVORY, r * 0.55f, c)
        drawCircle(color, r * 0.3f, c)
    }
}

private val BAMBOO_LAYOUTS: List<List<Pair<Float, Float>>> = listOf(
    listOf(.5f to .5f),
    listOf(.5f to .27f, .5f to .73f),
    listOf(.5f to .27f, .28f to .73f, .72f to .73f),
    listOf(.3f to .27f, .7f to .27f, .3f to .73f, .7f to .73f),
    listOf(.22f to .27f, .78f to .27f, .5f to .5f, .22f to .73f, .78f to .73f),
    listOf(.2f to .27f, .5f to .27f, .8f to .27f, .2f to .73f, .5f to .73f, .8f to .73f),
    listOf(.5f to .15f, .2f to .5f, .5f to .5f, .8f to .5f, .2f to .84f, .5f to .84f, .8f to .84f),
    listOf(.14f to .27f, .38f to .27f, .62f to .27f, .86f to .27f, .14f to .73f, .38f to .73f, .62f to .73f, .86f to .73f),
    listOf(.2f to .17f, .5f to .17f, .8f to .17f, .2f to .5f, .5f to .5f, .8f to .5f, .2f to .83f, .5f to .83f, .8f to .83f),
)

private fun DrawScope.drawBamboo(value: Int) {
    val big = value == 1
    val stickW = size.width * if (big) 0.26f else if (value == 8) 0.13f else 0.16f
    val stickH = size.height * when {
        big -> 0.8f
        value >= 7 -> 0.25f
        else -> 0.36f
    }
    BAMBOO_LAYOUTS[value - 1].forEachIndexed { i, (fx, fy) ->
        val red = big || (value in setOf(5, 7, 9) && i == (if (value == 7) 0 else BAMBOO_LAYOUTS[value - 1].size / 2))
        val color = if (red) INK_RED else INK_GREEN
        val topLeft = Offset(size.width * fx - stickW / 2, size.height * fy - stickH / 2)
        drawRoundRect(color, topLeft, Size(stickW, stickH), CornerRadius(stickW / 2))
        drawLine(
            IVORY.copy(alpha = 0.7f),
            Offset(topLeft.x + stickW / 2, topLeft.y + stickH * 0.15f),
            Offset(topLeft.x + stickW / 2, topLeft.y + stickH * 0.85f),
            strokeWidth = stickW * 0.15f,
        )
        drawLine(
            IVORY,
            Offset(topLeft.x, topLeft.y + stickH / 2),
            Offset(topLeft.x + stickW, topLeft.y + stickH / 2),
            strokeWidth = stickH * 0.06f,
        )
    }
}

/** Corner label: numbers for suits, localised letters for winds and dragons. */
@Composable
private fun tileLabel(face: TileFace): String = when (face.suit) {
    TileSuit.WINDS -> stringResource(
        when (face.value) {
            1 -> R.string.wind_short_east
            2 -> R.string.wind_short_south
            3 -> R.string.wind_short_west
            else -> R.string.wind_short_north
        },
    )
    TileSuit.DRAGONS -> stringResource(
        when (face.value) {
            1 -> R.string.dragon_short_red
            2 -> R.string.dragon_short_green
            else -> R.string.dragon_short_white
        },
    )
    else -> face.value.toString()
}

@Composable
private fun tileDescription(face: TileFace): String = when (face.suit) {
    TileSuit.DOTS -> stringResource(R.string.tile_dots, face.value)
    TileSuit.BAMBOO -> stringResource(R.string.tile_bamboo, face.value)
    TileSuit.CHARACTERS -> stringResource(R.string.tile_characters, face.value)
    TileSuit.WINDS -> stringResource(
        when (face.value) {
            1 -> R.string.tile_wind_east
            2 -> R.string.tile_wind_south
            3 -> R.string.tile_wind_west
            else -> R.string.tile_wind_north
        },
    )
    TileSuit.DRAGONS -> stringResource(
        when (face.value) {
            1 -> R.string.tile_dragon_red
            2 -> R.string.tile_dragon_green
            else -> R.string.tile_dragon_white
        },
    )
    TileSuit.FLOWERS -> stringResource(R.string.tile_flower, face.value)
    TileSuit.SEASONS -> stringResource(R.string.tile_season, face.value)
}
