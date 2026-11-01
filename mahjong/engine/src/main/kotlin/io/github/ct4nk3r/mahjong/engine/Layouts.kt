package io.github.ct4nk3r.mahjong.engine

/** Built-in layouts. To add one, describe it here and add a name string in the app (`layout_<id>`). */
object Layouts {

    /** The classic 144-tile "Turtle". */
    val TURTLE = layout("turtle") {
        rect(1.0, 0.0, 12, 1, 0)
        rect(3.0, 1.0, 8, 1, 0)
        rect(2.0, 2.0, 10, 1, 0)
        rect(1.0, 3.0, 12, 2, 0)
        rect(2.0, 5.0, 10, 1, 0)
        rect(3.0, 6.0, 8, 1, 0)
        rect(1.0, 7.0, 12, 1, 0)
        tile(0.0, 3.5, 0)
        tile(13.0, 3.5, 0)
        tile(14.0, 3.5, 0)
        rect(4.0, 1.0, 6, 6, 1)
        rect(5.0, 2.0, 4, 4, 2)
        rect(6.0, 3.0, 2, 2, 3)
        tile(6.5, 3.5, 4)
    }

    val PYRAMID = layout("pyramid") {
        rect(0.0, 0.0, 8, 8, 0)
        rect(1.0, 1.0, 6, 6, 1)
        rect(2.0, 2.0, 4, 4, 2)
        rect(3.0, 3.0, 2, 2, 3)
    }

    val FORTRESS = layout("fortress") {
        rect(0.0, 0.0, 10, 6, 0)
        rect(0.0, 0.0, 2, 2, 1)
        rect(8.0, 0.0, 2, 2, 1)
        rect(0.0, 4.0, 2, 2, 1)
        rect(8.0, 4.0, 2, 2, 1)
        rect(3.0, 2.0, 4, 2, 1)
        tile(0.5, 0.5, 2)
        tile(8.5, 0.5, 2)
        tile(0.5, 4.5, 2)
        tile(8.5, 4.5, 2)
        rect(4.0, 2.0, 2, 2, 2)
    }

    val BRIDGE = layout("bridge") {
        rect(0.0, 0.0, 3, 6, 0)
        rect(11.0, 0.0, 3, 6, 0)
        rect(3.0, 2.0, 8, 2, 0)
        rect(0.5, 0.5, 2, 5, 1)
        rect(11.5, 0.5, 2, 5, 1)
        rect(3.0, 2.5, 8, 1, 1)
        rect(1.0, 1.5, 1, 3, 2)
        rect(12.0, 1.5, 1, 3, 2)
    }

    val CROSS = layout("cross") {
        rect(0.0, 3.0, 12, 2, 0)
        rect(5.0, 0.0, 2, 3, 0)
        rect(5.0, 5.0, 2, 3, 0)
        rect(1.0, 3.0, 10, 2, 1)
        rect(5.0, 1.0, 2, 2, 1)
        rect(5.0, 5.0, 2, 2, 1)
        rect(3.0, 3.0, 6, 2, 2)
        rect(5.0, 2.0, 2, 1, 2)
        rect(5.0, 5.0, 2, 1, 2)
        rect(5.0, 3.0, 2, 2, 3)
        tile(5.5, 3.0, 4)
        tile(5.5, 4.0, 4)
    }

    val TWIN_PEAKS = layout("twin_peaks") {
        for (offset in listOf(0.0, 7.0)) {
            rect(offset, 0.0, 6, 6, 0)
            rect(offset + 1, 1.0, 4, 4, 1)
            rect(offset + 2, 2.0, 2, 2, 2)
            tile(offset + 2.5, 2.5, 3)
        }
        rect(6.0, 2.0, 1, 2, 0)
    }

    val DIAMOND = layout("diamond") {
        val center = 6.0
        listOf(2, 4, 6, 8, 10, 12, 12, 10, 8, 6, 4, 2).forEachIndexed { y, count -> centeredRow(center, y.toDouble(), count, 0) }
        listOf(2, 4, 6, 8, 8, 6, 4, 2).forEachIndexed { i, count -> centeredRow(center, (i + 2).toDouble(), count, 1) }
        listOf(2, 4, 4, 2).forEachIndexed { i, count -> centeredRow(center, (i + 4).toDouble(), count, 2) }
        rect(5.0, 5.0, 2, 2, 3)
    }

    val BEGINNER = layout("beginner") {
        rect(0.0, 0.0, 8, 4, 0)
        rect(1.0, 0.5, 6, 3, 1)
        rect(2.5, 1.0, 3, 2, 2)
    }

    val ALL: List<Layout> = listOf(TURTLE, PYRAMID, FORTRESS, BRIDGE, CROSS, TWIN_PEAKS, DIAMOND, BEGINNER)

    /**
     * A simple stepped pyramid: layer k is a (columns-2k) × (rows-2k) block. Used for the small
     * early campaign levels. The id encodes the shape, so it can be rebuilt from a saved game.
     */
    fun pyramid(columns: Int, rows: Int, layers: Int): Layout = layout("pyramid-${columns}x${rows}x$layers") {
        for (k in 0 until layers) rect(k.toDouble(), k.toDouble(), columns - 2 * k, rows - 2 * k, k)
    }

    private val PYRAMID_ID = Regex("""pyramid-(\d+)x(\d+)x(\d+)""")
    private val generated = java.util.concurrent.ConcurrentHashMap<String, Layout>()

    fun byId(id: String): Layout {
        ALL.firstOrNull { it.id == id }?.let { return it }
        val match = PYRAMID_ID.matchEntire(id) ?: return TURTLE
        return generated.getOrPut(id) {
            val (c, r, l) = match.destructured
            pyramid(c.toInt(), r.toInt(), l.toInt())
        }
    }
}
