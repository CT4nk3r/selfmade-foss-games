package io.github.ct4nk3r.games.core.common

import kotlinx.serialization.Serializable

/**
 * Immutable undo/redo history. Every game keeps its states in one of these:
 * [push] records a new move, [replace] amends the current state without creating
 * an undo step (used for automatic follow-up moves).
 */
@Serializable
data class Timeline<T>(
    val current: T,
    val past: List<T> = emptyList(),
    val future: List<T> = emptyList(),
) {
    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    fun push(next: T, limit: Int = DEFAULT_LIMIT): Timeline<T> =
        Timeline(next, (past + current).takeLast(limit), emptyList())

    fun replace(next: T): Timeline<T> = copy(current = next)

    fun undo(): Timeline<T> =
        if (past.isEmpty()) this else Timeline(past.last(), past.dropLast(1), listOf(current) + future)

    fun redo(): Timeline<T> =
        if (future.isEmpty()) this else Timeline(future.first(), past + current, future.drop(1))

    /** A smaller copy for persisting to disk. */
    fun trimmed(keep: Int): Timeline<T> = copy(past = past.takeLast(keep), future = future.take(keep))

    companion object {
        const val DEFAULT_LIMIT = 1000
    }
}
