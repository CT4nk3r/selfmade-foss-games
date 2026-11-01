package io.github.ct4nk3r.games.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable

@Serializable
data class SaveSlot<T>(val savedAtMillis: Long, val game: T)

/** A fixed number of manual save slots for one game. [T] is the game's own save format. */
class SaveSlotRepository<T : Any>(
    private val store: KeyValueStore,
    private val keyPrefix: String,
    gameSerializer: KSerializer<T>,
    val count: Int = DEFAULT_COUNT,
) {
    private val serializer = SaveSlot.serializer(gameSerializer)

    /** One entry per slot, null when the slot is empty. */
    val slots: Flow<List<SaveSlot<T>?>> =
        combine((0 until count).map { store.observe(key(it), serializer.nullable, null) }) { it.toList() }

    suspend fun save(index: Int, game: T) =
        store.write(key(index), serializer, SaveSlot(System.currentTimeMillis(), game))

    suspend fun load(index: Int): T? = store.read(key(index), serializer)?.game

    suspend fun delete(index: Int) = store.remove(key(index))

    private fun key(index: Int): String {
        require(index in 0 until count) { "No save slot $index" }
        return "${keyPrefix}_$index"
    }

    companion object {
        const val DEFAULT_COUNT = 5
    }
}
