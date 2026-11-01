package io.github.ct4nk3r.mahjong.engine

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A tile position. [x] and [y] are in half-tile units so tiles can sit offset by half a tile;
 * a tile covers [x, x+2) × [y, y+2) on layer [z].
 */
data class TilePos(val x: Int, val y: Int, val z: Int)

class Layout(val id: String, val positions: List<TilePos>) {
    val size: Int get() = positions.size
    val maxX: Int = positions.maxOf { it.x }
    val maxY: Int = positions.maxOf { it.y }
    val maxZ: Int = positions.maxOf { it.z }

    /** Tiles that sit on top of each tile (overlapping, one or more layers higher). */
    internal val above: Array<IntArray>
    internal val left: Array<IntArray>
    internal val right: Array<IntArray>

    init {
        require(size % 2 == 0) { "Layout $id has an odd number of tiles ($size)" }
        val n = size
        above = Array(n) { i ->
            val p = positions[i]
            (0 until n).filter { j -> positions[j].let { q -> q.z > p.z && abs(q.x - p.x) < 2 && abs(q.y - p.y) < 2 } }.toIntArray()
        }
        left = Array(n) { i ->
            val p = positions[i]
            (0 until n).filter { j -> positions[j].let { q -> q.z == p.z && q.x == p.x - 2 && abs(q.y - p.y) < 2 } }.toIntArray()
        }
        right = Array(n) { i ->
            val p = positions[i]
            (0 until n).filter { j -> positions[j].let { q -> q.z == p.z && q.x == p.x + 2 && abs(q.y - p.y) < 2 } }.toIntArray()
        }
    }

    /** A tile is free when nothing lies on it and its left or right side is open. */
    fun isFree(i: Int, present: BooleanArray): Boolean {
        if (!present[i]) return false
        if (above[i].any { present[it] }) return false
        return left[i].none { present[it] } || right[i].none { present[it] }
    }

    /** Order to draw tiles so that higher and lower-right tiles are painted over their neighbours. */
    val drawOrder: List<Int> = positions.indices.sortedWith(compareBy({ positions[it].z }, { positions[it].x + positions[it].y }, { positions[it].x }))
}

/** Small DSL for describing layouts in whole-tile coordinates (fractions allowed for half offsets). */
class LayoutBuilder {
    private val positions = mutableListOf<TilePos>()

    fun tile(x: Double, y: Double, z: Int) {
        positions += TilePos((x * 2).roundToInt(), (y * 2).roundToInt(), z)
    }

    fun rect(x: Double, y: Double, columns: Int, rows: Int, z: Int) {
        for (r in 0 until rows) for (c in 0 until columns) tile(x + c, y + r, z)
    }

    /** One row of [count] tiles centred on [centerX]. */
    fun centeredRow(centerX: Double, y: Double, count: Int, z: Int) = rect(centerX - count / 2.0, y, count, 1, z)

    fun build(id: String) = Layout(id, positions.toList())
}

fun layout(id: String, block: LayoutBuilder.() -> Unit): Layout = LayoutBuilder().apply(block).build(id)
