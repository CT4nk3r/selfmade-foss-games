package io.github.ct4nk3r.solitaire.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import io.github.ct4nk3r.solitaire.engine.Card
import io.github.ct4nk3r.solitaire.engine.Klondike
import io.github.ct4nk3r.solitaire.engine.KlondikeState
import io.github.ct4nk3r.solitaire.engine.PileRef
import io.github.ct4nk3r.solitaire.engine.PileType
import kotlin.math.max
import kotlin.math.min

/** Where one card sits on screen (pixels) and how high it is stacked. */
data class Placement(val card: Card, val pile: PileRef, val index: Int, val position: Offset, val z: Int)

/** Pixel layout of the Klondike table for a given board size. Pure maths, no Compose state. */
class BoardGeometry private constructor(
    val cardWidth: Float,
    val cardHeight: Float,
    private val gap: Float,
    private val originX: Float,
    private val topY: Float,
    private val tableauTop: Float,
    private val bottom: Float,
    private val leftHanded: Boolean,
) {
    private fun columnX(column: Int) = originX + gap + column * (cardWidth + gap)

    fun slot(ref: PileRef): Offset = when (ref.type) {
        PileType.STOCK -> Offset(columnX(if (leftHanded) 6 else 0), topY)
        PileType.WASTE -> Offset(columnX(if (leftHanded) 5 else 1), topY)
        PileType.FOUNDATION -> Offset(columnX(if (leftHanded) ref.index else 3 + ref.index), topY)
        PileType.TABLEAU -> Offset(columnX(ref.index), tableauTop)
    }

    fun slotRect(ref: PileRef) = Rect(slot(ref), Size(cardWidth, cardHeight))

    /** A whole tableau column, from its top to the bottom of the table: one big tap target. */
    fun columnZone(column: Int) = Rect(Offset(columnX(column), tableauTop), Size(cardWidth, bottom - tableauTop))

    /** Vertical offsets of each card in a tableau column, squeezed to fit the screen. */
    fun tableauOffsets(pile: List<Card>): List<Float> {
        if (pile.isEmpty()) return emptyList()
        val faceDownGaps = pile.dropLast(1).count { !it.faceUp }
        val faceUpGaps = pile.size - 1 - faceDownGaps
        // Generous spacing so every face-up card shows a big strip; squeezed only when space runs out.
        var down = cardHeight * 0.14f
        var up = cardHeight * 0.46f
        val available = bottom - tableauTop - cardHeight
        if (faceDownGaps * down + faceUpGaps * up > available) {
            if (faceUpGaps > 0) up = max(cardHeight * 0.13f, (available - faceDownGaps * down) / faceUpGaps)
            if (faceDownGaps > 0 && faceDownGaps * down + faceUpGaps * up > available) {
                down = max(cardHeight * 0.04f, (available - faceUpGaps * up) / faceDownGaps)
            }
        }
        var y = 0f
        return pile.map { card ->
            val current = y
            y += if (card.faceUp) up else down
            current
        }
    }

    fun place(state: KlondikeState, drawCount: Int): Map<Int, Placement> {
        val result = LinkedHashMap<Int, Placement>(64)
        var z = 0

        val stockSlot = slot(PileRef.STOCK)
        state.stock.forEachIndexed { i, c -> result[c.id] = Placement(c, PileRef.STOCK, i, stockSlot, z++) }

        val wasteSlot = slot(PileRef.WASTE)
        val fanned = if (drawCount > 1) min(drawCount, state.waste.size) else 1
        val fanStep = cardWidth * 0.3f * if (leftHanded) -1f else 1f
        state.waste.forEachIndexed { i, c ->
            val fanIndex = max(0, i - (state.waste.size - fanned))
            result[c.id] = Placement(c, PileRef.WASTE, i, wasteSlot + Offset(fanIndex * fanStep, 0f), z++)
        }

        state.foundations.forEachIndexed { f, pile ->
            val ref = PileRef.foundation(f)
            val pos = slot(ref)
            pile.forEachIndexed { i, c -> result[c.id] = Placement(c, ref, i, pos, z++) }
        }

        state.tableau.forEachIndexed { t, pile ->
            val ref = PileRef.tableau(t)
            val pos = slot(ref)
            val offsets = tableauOffsets(pile)
            pile.forEachIndexed { i, c -> result[c.id] = Placement(c, ref, i, pos + Offset(0f, offsets[i]), z++) }
        }
        return result
    }

    /** The legal pile under the dragged cards that they overlap the most, if any. */
    fun dropTarget(state: KlondikeState, game: Klondike, from: PileRef, index: Int, leadPosition: Offset): PileRef? {
        val source = state.pile(from)
        val cards = source.subList(index, source.size)
        val dragged = Rect(leadPosition, Size(cardWidth, cardHeight))
        val candidates = List(Klondike.FOUNDATION_COUNT) { PileRef.foundation(it) } +
            List(Klondike.TABLEAU_COUNT) { PileRef.tableau(it) }
        return candidates
            .filter { it != from && game.canPlace(state, cards, from, it) }
            .map { it to overlap(dragged, landingRect(state, it)) }
            .filter { it.second > 0f }
            .maxByOrNull { it.second }
            ?.first
    }

    /** The area a dropped card should overlap to land on [ref]: the top card, a bit taller for tableau. */
    fun landingRect(state: KlondikeState, ref: PileRef): Rect {
        if (ref.type != PileType.TABLEAU) return slotRect(ref)
        val pile = state.pile(ref)
        val y = tableauOffsets(pile).lastOrNull() ?: 0f
        val base = slot(ref)
        return Rect(Offset(base.x, base.y + y), Size(cardWidth, cardHeight * 1.4f))
    }

    private fun overlap(a: Rect, b: Rect): Float {
        val i = a.intersect(b)
        return if (i.width > 0f && i.height > 0f) i.width * i.height else 0f
    }

    companion object {
        const val CARD_RATIO = 1.45f
        private const val MIN_CARD_WIDTH_DP = 16f

        fun create(width: Float, height: Float, density: Float, leftHanded: Boolean): BoardGeometry {
            val gap = 4f * density
            val byWidth = (width - gap * 8) / 7
            // Leave room for the top row plus a reasonably long tableau, also in landscape.
            val byHeight = (height - gap * 6) / (CARD_RATIO * 3.4f)
            // The board can be measured tiny (e.g. mid-rotation); never let sizes go to zero or below.
            val cardWidth = max(min(byWidth, byHeight), MIN_CARD_WIDTH_DP * density)
            val cardHeight = cardWidth * CARD_RATIO
            val boardWidth = cardWidth * 7 + gap * 8
            val originX = (width - boardWidth) / 2
            val topY = gap * 2
            return BoardGeometry(
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                gap = gap,
                originX = originX,
                topY = topY,
                tableauTop = topY + cardHeight + gap * 3,
                bottom = height - gap,
                leftHanded = leftHanded,
            )
        }
    }
}
