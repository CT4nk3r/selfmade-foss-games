package io.github.ct4nk3r.games.core.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** A pausable game timer with one-second resolution. */
class GameClock(private val scope: CoroutineScope) {
    private val _elapsed = MutableStateFlow(0L)
    val elapsed: StateFlow<Long> = _elapsed.asStateFlow()

    private var ticker: Job? = null
    val isRunning: Boolean get() = ticker?.isActive == true

    fun start() {
        if (isRunning) return
        ticker = scope.launch {
            while (isActive) {
                delay(1_000)
                _elapsed.update { it + 1 }
            }
        }
    }

    fun pause() {
        ticker?.cancel()
        ticker = null
    }

    fun reset(seconds: Long = 0) {
        pause()
        _elapsed.value = seconds
    }
}
