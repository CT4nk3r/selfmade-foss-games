package io.github.ct4nk3r.mahjong.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.ct4nk3r.mahjong.R
import io.github.ct4nk3r.mahjong.engine.Layout
import io.github.ct4nk3r.mahjong.engine.MahjongState
import io.github.ct4nk3r.mahjong.engine.TileFace
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val TILE_RATIO = 1.3f
private const val DEPTH_RATIO = 0.1f
private const val MATCH_EFFECT_MS = 520
private val ZOOM_STEPS = listOf(1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 3.5f, 4f)

/** A matched pair playing its "pop" animation above the board. */
private data class MatchEffect(val id: Long, val tiles: List<Pair<Int, TileFace>>)

@Composable
fun MahjongBoard(
    layout: Layout,
    state: MahjongState,
    selected: Int?,
    hint: Pair<Int, Int>?,
    highlightFree: Boolean,
    showLabels: Boolean,
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val density = LocalDensity.current
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val pad = with(density) { 8.dp.toPx() }

        // Fit the whole layout, including the 3D offsets of the upper layers, on screen.
        val columns = (layout.maxX + 2) / 2f
        val rows = (layout.maxY + 2) / 2f
        val layers = layout.maxZ + 1
        // The board can be measured tiny (e.g. mid-rotation); never let sizes go to zero or below.
        val tileW = max(
            min(
                (width - pad * 2) / (columns + DEPTH_RATIO * layers),
                (height - pad * 2) / (rows * TILE_RATIO + DEPTH_RATIO * layers),
            ),
            with(density) { 8.dp.toPx() },
        )
        val tileH = tileW * TILE_RATIO
        val depth = tileW * DEPTH_RATIO
        val boardW = columns * tileW + layers * depth
        val boardH = rows * tileH + layers * depth
        val originX = (width - boardW) / 2 + layout.maxZ * depth
        val originY = (height - boardH) / 2 + layout.maxZ * depth

        fun tileOffset(i: Int): IntOffset {
            val p = layout.positions[i]
            return IntOffset(
                (originX + p.x * tileW / 2 - p.z * depth).roundToInt(),
                (originY + p.y * tileH / 2 - p.z * depth).roundToInt(),
            )
        }

        val free = remember(state, layout) {
            val present = state.present.toBooleanArray()
            BooleanArray(layout.size) { layout.isFree(it, present) }
        }

        // ---- zoom & pan
        var scale by remember(layout.id) { mutableFloatStateOf(zoom.coerceIn(ZOOM_STEPS.first(), ZOOM_STEPS.last())) }
        var pan by remember(layout.id) { mutableStateOf(Offset.Zero) }
        // Extra scroll room so tiles can be dragged out from under the zoom buttons.
        val buttonRoom = with(density) { 90.dp.toPx() }
        fun clampPan(p: Offset, s: Float): Offset {
            // Allow panning only as far as the zoomed board reaches past the screen edges.
            if (s <= 1.01f) return Offset.Zero
            val maxX = max(0f, (boardW * s - width) / 2 + pad) + buttonRoom
            val maxY = max(0f, (boardH * s - height) / 2 + pad) + buttonRoom
            return Offset(p.x.coerceIn(-maxX, maxX), p.y.coerceIn(-maxY, maxY))
        }
        fun setZoom(newScale: Float, persist: Boolean) {
            val s = newScale.coerceIn(ZOOM_STEPS.first(), ZOOM_STEPS.last())
            pan = clampPan(pan * (s / scale), s)
            scale = s
            if (persist) onZoomChange(s)
        }

        // ---- matched-pair effects: spot tiles that just disappeared because of a match
        val effects = remember { mutableStateListOf<MatchEffect>() }
        val previous = remember { arrayOf(state) }
        LaunchedEffect(state) {
            val before = previous[0]
            previous[0] = state
            if (before.layoutId != state.layoutId || state.moves != before.moves + 1) return@LaunchedEffect
            val removed = state.present.indices.filter { before.present[it] && !state.present[it] }
            if (removed.isNotEmpty()) {
                effects += MatchEffect(System.nanoTime(), removed.map { it to before.faces[it] })
            }
        }

        val wDp = with(density) { tileW.toDp() }
        val hDp = with(density) { tileH.toDp() }
        val dDp = with(density) { depth.toDp() }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(layout.id, boardW, boardH) {
                    detectTransformGestures { _, panChange, zoomChange, _ ->
                        if (zoomChange != 1f) setZoom(scale * zoomChange, persist = false)
                        pan = clampPan(pan + panChange, scale)
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = pan.x
                    translationY = pan.y
                },
        ) {
            layout.drawOrder.forEachIndexed { order, i ->
                key(i) {
                    AnimatedVisibility(
                        visible = state.present[i],
                        enter = fadeIn() + scaleIn(initialScale = 0.85f),
                        // Removal is shown by the effect layer on top, so neighbours never hide it.
                        exit = ExitTransition.None,
                        modifier = Modifier
                            .offset { tileOffset(i) }
                            .zIndex(order.toFloat()),
                    ) {
                        TileView(
                            face = state.faces[i],
                            width = wDp,
                            height = hDp,
                            depth = dDp,
                            selected = selected == i,
                            hinted = hint != null && (hint.first == i || hint.second == i),
                            dimmed = highlightFree && !free[i],
                            showLabel = showLabels,
                            onTap = { onTap(i) },
                        )
                    }
                }
            }

            effects.forEach { effect ->
                key(effect.id) {
                    MatchPop(
                        effect = effect,
                        offsetOf = ::tileOffset,
                        width = wDp,
                        height = hDp,
                        depth = dDp,
                        showLabels = showLabels,
                        onFinished = { effects.remove(effect) },
                        modifier = Modifier.zIndex(10_000f),
                    )
                }
            }
        }

        // ---- big zoom buttons, bottom left, easy to hit
        Column(
            Modifier.align(Alignment.BottomStart).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (scale > 1.01f) {
                ZoomButton(Icons.Filled.ZoomOutMap, stringResource(R.string.action_reset_zoom)) {
                    pan = Offset.Zero
                    setZoom(1f, persist = true)
                }
            }
            ZoomButton(Icons.Filled.ZoomIn, stringResource(R.string.action_zoom_in), enabled = scale < ZOOM_STEPS.last()) {
                setZoom(ZOOM_STEPS.firstOrNull { it > scale + 0.01f } ?: ZOOM_STEPS.last(), persist = true)
            }
            ZoomButton(Icons.Filled.ZoomOut, stringResource(R.string.action_zoom_out), enabled = scale > 1.01f) {
                setZoom(ZOOM_STEPS.lastOrNull { it < scale - 0.01f } ?: 1f, persist = true)
            }
        }
    }
}

@Composable
private fun ZoomButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    FloatingActionButton(
        onClick = { if (enabled) onClick() },
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (enabled) 0.92f else 0.5f),
        contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f),
        modifier = Modifier.size(60.dp),
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(32.dp))
    }
}

/** Matched tiles lift, swell and fade while a ring and sparks burst out of them. */
@Composable
private fun MatchPop(
    effect: MatchEffect,
    offsetOf: (Int) -> IntOffset,
    width: Dp,
    height: Dp,
    depth: Dp,
    showLabels: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(MATCH_EFFECT_MS, easing = FastOutSlowInEasing))
        onFinished()
    }
    val density = LocalDensity.current
    val heightPx = with(density) { height.toPx() }
    effect.tiles.forEach { (index, face) ->
        val base = offsetOf(index)
        Box(
            modifier
                .offset { base }
                .graphicsLayer {
                    val p = progress.value
                    val grow = if (p < 0.3f) p / 0.3f * 0.18f else 0.18f + (p - 0.3f) * 0.25f
                    scaleX = 1f + grow
                    scaleY = 1f + grow
                    translationY = -heightPx * 0.35f * p
                    alpha = if (p < 0.35f) 1f else 1f - (p - 0.35f) / 0.65f
                },
        ) {
            TileView(face, width, height, depth, selected = true, hinted = false, dimmed = false, showLabel = showLabels, onTap = null)
        }
        // Sparks are drawn around the tile centre, larger than the tile itself.
        val sparkArea = height * 2.2f
        Canvas(
            modifier
                .offset {
                    val half = with(density) { (sparkArea / 2).roundToPx() }
                    IntOffset(base.x + with(density) { (width / 2).roundToPx() } - half, base.y + with(density) { (height / 2).roundToPx() } - half)
                }
                .size(sparkArea),
        ) {
            val p = progress.value
            val c = Offset(size.width / 2, size.height / 2 - heightPx * 0.35f * p)
            val maxR = size.width / 2
            drawCircle(
                Color(0xFFFFE082).copy(alpha = (1f - p) * 0.9f),
                radius = maxR * (0.25f + 0.75f * p),
                center = c,
                style = Stroke(width = maxR * 0.06f * (1f - p) + 1f),
            )
            repeat(12) { k ->
                val angle = (k / 12f) * 2f * Math.PI.toFloat() + index
                val r = maxR * (0.15f + 0.8f * p)
                val dot = Offset(c.x + cos(angle) * r, c.y + sin(angle) * r)
                drawCircle(
                    (if (k % 2 == 0) Color(0xFFFFD54F) else Color.White).copy(alpha = 1f - p),
                    radius = maxR * 0.045f * (1f - p * 0.6f),
                    center = dot,
                )
            }
        }
    }
}
