package io.github.ct4nk3r.solitaire.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import io.github.ct4nk3r.solitaire.R
import io.github.ct4nk3r.solitaire.engine.Klondike
import io.github.ct4nk3r.solitaire.engine.KlondikeState
import io.github.ct4nk3r.solitaire.engine.Move
import io.github.ct4nk3r.solitaire.engine.PileRef
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val MOVE_ANIMATION_MS = 220

/** Hints and the selected stack. */
val HIGHLIGHT_SOURCE = Color(0xFFFFC107)

/** Places the selected stack can go. */
val HIGHLIGHT_TARGET = Color(0xFF4DD0E1)

private class DragInfo(val from: PileRef, val index: Int, val leadId: Int)

/**
 * The Klondike table. Taps are reported as (pile, card index), with a null index for taps on
 * empty areas or face-down cards; the screen decides what a tap means for the current settings.
 */
@Composable
fun SolitaireBoard(
    state: KlondikeState,
    game: Klondike,
    leftHanded: Boolean,
    style: CardStyle,
    hint: Move?,
    selected: PileRef?,
    onTap: (pile: PileRef, index: Int?) -> Unit,
    onDragStart: () -> Unit,
    onDrop: (PileRef, Int, PileRef) -> Boolean,
    onFeedback: (success: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val geometry = remember(constraints.maxWidth, constraints.maxHeight, leftHanded, density.density) {
            BoardGeometry.create(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), density.density, leftHanded)
        }
        val placements = remember(state, geometry) { geometry.place(state, game.options.drawCount) }
        val currentState by rememberUpdatedState(state)
        val currentPlacements by rememberUpdatedState(placements)
        // Gesture handlers outlive recompositions; always call the latest callbacks.
        val currentOnTap by rememberUpdatedState(onTap)
        val currentOnDragStart by rememberUpdatedState(onDragStart)

        // Cards whose pile changed in the last update fly above everything else.
        val previous = remember { arrayOf(placements) }
        val recentlyMoved = remember(placements) {
            placements.filter { (id, p) -> previous[0][id]?.pile != p.pile }.keys.also { previous[0] = placements }
        }

        val anims = remember { HashMap<Int, Animatable<Offset, AnimationVector2D>>() }
        val scope = rememberCoroutineScope()
        var dragInfo by remember { mutableStateOf<DragInfo?>(null) }
        var draggedIds by remember { mutableStateOf(emptySet<Int>()) }
        var dragOffset by remember { mutableStateOf(Offset.Zero) }

        val cardW = with(density) { geometry.cardWidth.toDp() }
        val cardH = with(density) { geometry.cardHeight.toDp() }
        val labels = rankLabels()

        // ---- what to highlight: a hint, or the selected stack and where it can go
        val sourceIds: Set<Int>
        val targets: Set<PileRef>
        when {
            selected != null -> {
                val start = game.movableStart(state, selected)
                sourceIds = if (start == null) emptySet() else state.pile(selected).drop(start).map { it.id }.toSet()
                targets = remember(state, selected) { game.destinationsFrom(state, selected).toSet() }
            }
            hint is Move.Transfer -> {
                sourceIds = state.pile(hint.from).drop(hint.index).map { it.id }.toSet()
                targets = setOf(hint.to)
            }
            hint == Move.Draw -> {
                sourceIds = emptySet()
                targets = setOf(PileRef.STOCK)
            }
            else -> {
                sourceIds = emptySet()
                targets = emptySet()
            }
        }
        val targetColor = if (selected != null) HIGHLIGHT_TARGET else HIGHLIGHT_SOURCE
        fun slotHighlight(ref: PileRef): Color? = if (ref in targets && state.pile(ref).isEmpty()) targetColor else null

        fun endDrag(drop: Boolean) {
            val info = dragInfo ?: return
            val ids = draggedIds
            val offset = dragOffset
            dragInfo = null
            scope.launch {
                // Freeze the cards where they were released so they animate from there.
                ids.forEach { id -> anims[id]?.let { it.snapTo(it.value + offset) } }
                draggedIds = emptySet()
                dragOffset = Offset.Zero
                val lead = currentPlacements[info.leadId]
                val target = if (drop && lead != null) {
                    geometry.dropTarget(currentState, game, info.from, info.index, lead.position + offset)
                } else null
                val moved = target != null && onDrop(info.from, info.index, target)
                if (!moved) {
                    if (drop) onFeedback(false)
                    ids.forEach { id ->
                        val p = currentPlacements[id] ?: return@forEach
                        launch { anims[id]?.animateTo(p.position, tween(MOVE_ANIMATION_MS, easing = FastOutSlowInEasing)) }
                    }
                } else {
                    onFeedback(true)
                }
            }
        }

        // ---- tap areas and empty-pile outlines, underneath the cards
        repeat(Klondike.TABLEAU_COUNT) { t ->
            val ref = PileRef.tableau(t)
            val description = stringResource(R.string.pile_column, t + 1)
            // The whole column, down to the bottom of the table, is one big tap target.
            TapZone(geometry.columnZone(t), description) { currentOnTap(ref, null) }
            Slot(geometry, ref, cardW, cardH, label = null, highlight = slotHighlight(ref))
        }
        Slot(geometry, PileRef.STOCK, cardW, cardH, label = null, highlight = slotHighlight(PileRef.STOCK))
        TapZone(geometry.slotRect(PileRef.STOCK), stringResource(R.string.pile_stock)) { currentOnTap(PileRef.STOCK, null) }
        if (state.stock.isEmpty() && game.canDraw(state)) {
            val pos = geometry.slot(PileRef.STOCK)
            Box(
                Modifier
                    .offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }
                    .size(cardW, cardH),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(cardW * 0.6f),
                )
            }
        }
        Slot(geometry, PileRef.WASTE, cardW, cardH, label = null, highlight = null)
        repeat(Klondike.FOUNDATION_COUNT) { f ->
            val ref = PileRef.foundation(f)
            Slot(geometry, ref, cardW, cardH, label = labels[0], highlight = slotHighlight(ref))
            TapZone(geometry.slotRect(ref), stringResource(R.string.pile_foundation)) { currentOnTap(ref, null) }
        }

        // ---- cards
        for (p in placements.values) {
            key(p.card.id) {
                val target = p.position
                val anim = anims.getOrPut(p.card.id) { Animatable(target, Offset.VectorConverter) }
                LaunchedEffect(target) {
                    anim.animateTo(target, tween(MOVE_ANIMATION_MS, easing = FastOutSlowInEasing))
                }
                val pile = state.pile(p.pile)
                val isTop = p.index == pile.lastIndex
                val isDragged = p.card.id in draggedIds
                val isSource = p.card.id in sourceIds
                val highlight = when {
                    isSource -> HIGHLIGHT_SOURCE
                    isTop && p.pile in targets -> targetColor
                    else -> null
                }
                // The selected stack lifts a little so it is obvious what was picked.
                val lift by animateFloatAsState(if (isSource && selected != null) 1f else 0f, tween(150), label = "lift")
                val z = when {
                    isDragged -> 2000f + p.z
                    p.card.id in recentlyMoved -> 1000f + p.z
                    else -> p.z.toFloat()
                }
                val description = cardDescription(p.card)

                CardView(
                    card = p.card,
                    width = cardW,
                    height = cardH,
                    style = style,
                    labels = labels,
                    highlight = highlight,
                    modifier = Modifier
                        .offset {
                            val base = anim.value
                            val extra = if (isDragged) dragOffset else Offset.Zero
                            IntOffset((base.x + extra.x).roundToInt(), (base.y + extra.y).roundToInt())
                        }
                        .zIndex(z)
                        .size(cardW, cardH)
                        .graphicsLayer { translationY = -lift * geometry.cardHeight * 0.06f }
                        .semantics { contentDescription = description }
                        .pointerInput(p.card.id, p.pile, p.index) {
                            detectTapGestures {
                                val faceUp = currentState.pile(p.pile).getOrNull(p.index)?.faceUp == true
                                currentOnTap(p.pile, if (faceUp) p.index else null)
                            }
                        }
                        .pointerInput(p.card.id, p.pile, p.index) {
                            detectDragGestures(
                                onDragStart = {
                                    val s = currentState
                                    if (game.canPickUp(s, p.pile, p.index)) {
                                        currentOnDragStart()
                                        val cards = s.pile(p.pile).drop(p.index)
                                        dragInfo = DragInfo(p.pile, p.index, p.card.id)
                                        draggedIds = cards.map { it.id }.toSet()
                                        dragOffset = Offset.Zero
                                    }
                                },
                                onDrag = { change, amount ->
                                    if (dragInfo != null) {
                                        change.consume()
                                        dragOffset += amount
                                    }
                                },
                                onDragEnd = { endDrag(drop = true) },
                                onDragCancel = { endDrag(drop = false) },
                            )
                        },
                )
            }
        }
    }
}

/** An invisible tap target covering [rect]. */
@Composable
private fun TapZone(rect: Rect, description: String, onTap: () -> Unit) {
    val density = LocalDensity.current
    val currentOnTap by rememberUpdatedState(onTap)
    Box(
        Modifier
            .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
            .semantics { contentDescription = description }
            .pointerInput(Unit) { detectTapGestures { currentOnTap() } },
    )
}

@Composable
private fun Slot(
    geometry: BoardGeometry,
    ref: PileRef,
    width: Dp,
    height: Dp,
    label: String?,
    highlight: Color?,
) {
    val pos = geometry.slot(ref)
    EmptySlot(
        width = width,
        height = height,
        label = label,
        highlight = highlight,
        modifier = Modifier
            .offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }
            .size(width, height),
    )
}
