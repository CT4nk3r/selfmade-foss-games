package io.github.ct4nk3r.games.core.data

import io.github.ct4nk3r.games.core.common.GameStats
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/** Per-variant statistics, keyed by a variant id such as "klondike-d1-unlimited-standard". */
class StatsRepository(private val store: KeyValueStore) {
    private val serializer = MapSerializer(String.serializer(), GameStats.serializer())

    val all: Flow<Map<String, GameStats>> = store.observe(KEY, serializer, emptyMap())

    suspend fun recordWin(variant: String, seconds: Long, moves: Int?, score: Int?) = edit(variant) {
        it.withWin(seconds, moves, score)
    }

    suspend fun recordLoss(variant: String, seconds: Long) = edit(variant) { it.withLoss(seconds) }

    suspend fun reset() = store.remove(KEY)

    private suspend fun edit(variant: String, transform: (GameStats) -> GameStats) {
        store.update(KEY, serializer, emptyMap()) { all ->
            all + (variant to transform(all[variant] ?: GameStats()))
        }
    }

    private companion object {
        const val KEY = "stats"
    }
}
