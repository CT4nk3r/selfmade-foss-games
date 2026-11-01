package io.github.ct4nk3r.solitaire

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.ct4nk3r.games.core.common.DailySeed
import io.github.ct4nk3r.games.core.common.GameClock
import io.github.ct4nk3r.games.core.common.GameStats
import io.github.ct4nk3r.games.core.common.Timeline
import io.github.ct4nk3r.games.core.data.KeyValueStore
import io.github.ct4nk3r.games.core.data.SaveSlotRepository
import io.github.ct4nk3r.games.core.data.StatsRepository
import io.github.ct4nk3r.games.core.ui.CommonSettings
import io.github.ct4nk3r.solitaire.engine.Klondike
import io.github.ct4nk3r.solitaire.engine.KlondikeOptions
import io.github.ct4nk3r.solitaire.engine.KlondikeSolver
import io.github.ct4nk3r.solitaire.engine.KlondikeState
import io.github.ct4nk3r.solitaire.engine.Move
import io.github.ct4nk3r.solitaire.engine.PileRef
import io.github.ct4nk3r.solitaire.engine.PileType
import io.github.ct4nk3r.solitaire.engine.ScoringMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.time.LocalDate
import kotlin.random.Random

data class SolitaireUiState(
    val timeline: Timeline<KlondikeState>? = null,
    val dailyEpochDay: Long? = null,
    val dealing: Boolean = true,
    val hint: Move? = null,
    /** Column (or waste/foundation) picked with "tap a column, then tap where it goes". */
    val selected: PileRef? = null,
    val won: Boolean = false,
    val autoCompleting: Boolean = false,
) {
    val state: KlondikeState? get() = timeline?.current
    val inProgress: Boolean get() = !won && (timeline?.current?.moves ?: 0) > 0
    val isDaily: Boolean get() = dailyEpochDay != null
}

enum class GameEvent { NO_HINT }

/** What a tap did, so the screen can give matching sound and haptic feedback. */
enum class TapFeedback { SELECTED, DESELECTED, MOVED, INVALID, NONE }

/** What the Saved games screen needs to know about one save slot. */
data class SlotInfo(val savedAtMillis: Long, val elapsedSeconds: Long, val moves: Int, val progress: Float, val isDaily: Boolean)

@Serializable
private data class SavedGame(
    val timeline: Timeline<KlondikeState>,
    val elapsedSeconds: Long,
    val dailyEpochDay: Long? = null,
    val options: KlondikeOptions,
)

class SolitaireViewModel(app: Application) : AndroidViewModel(app) {

    /** The rules in play. Harder variants are a different [KlondikeOptions] preset. */
    val game = Klondike(KlondikeOptions.DEFAULT)
    private val solver = KlondikeSolver(game)
    private val store = KeyValueStore(app)
    private val statsRepo = StatsRepository(store)
    private val clock = GameClock(viewModelScope)

    val elapsed: StateFlow<Long> = clock.elapsed
    val common: StateFlow<CommonSettings> = store.observe(KEY_COMMON, CommonSettings.serializer(), CommonSettings())
        .stateIn(viewModelScope, SharingStarted.Eagerly, CommonSettings())
    val settings: StateFlow<SolitaireSettings> = store.observe(KEY_SETTINGS, SolitaireSettings.serializer(), SolitaireSettings())
        .stateIn(viewModelScope, SharingStarted.Eagerly, SolitaireSettings())
    private val slotRepo = SaveSlotRepository(store, KEY_SLOT_PREFIX, SavedGame.serializer())
    val slots: StateFlow<List<SlotInfo?>> = slotRepo.slots
        .map { list ->
            list.map { slot ->
                slot?.let {
                    val s = it.game.timeline.current
                    SlotInfo(it.savedAtMillis, it.game.elapsedSeconds, s.moves, s.foundations.sumOf { f -> f.size } / 52f, it.game.dailyEpochDay != null)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, List(slotRepo.count) { null })
    val stats: StateFlow<Map<String, GameStats>> = statsRepo.all
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _ui = MutableStateFlow(SolitaireUiState())
    val ui: StateFlow<SolitaireUiState> = _ui.asStateFlow()

    private val _events = Channel<GameEvent>(Channel.BUFFERED)
    val events: Flow<GameEvent> = _events.receiveAsFlow()

    val statsKey: String get() = game.options.statsKey
    val dailyStatsKey: String get() = game.options.statsKey + "-daily"

    private var autoJob: Job? = null
    private var hintJob: Job? = null
    private var foreground = true

    init {
        viewModelScope.launch {
            val saved = store.read(KEY_SAVED_GAME, SavedGame.serializer())
            if (saved != null && saved.options == game.options && !saved.timeline.current.isWon) {
                _ui.value = SolitaireUiState(timeline = saved.timeline, dailyEpochDay = saved.dailyEpochDay, dealing = false)
                clock.reset(saved.elapsedSeconds)
                resumeClockIfPlaying()
            } else {
                startGame(daily = null, seed = null)
            }
        }
    }

    private val current: KlondikeState? get() = _ui.value.state
    private val acceptsInput: Boolean
        get() = _ui.value.let { !it.dealing && !it.won && !it.autoCompleting && it.timeline != null }

    // ---------------------------------------------------------------- player actions

    fun draw(): Boolean {
        val s = current?.takeIf { acceptsInput } ?: return false
        return commit(game.draw(s))
    }

    fun tapCard(from: PileRef, index: Int): Boolean {
        val s = current?.takeIf { acceptsInput } ?: return false
        val to = game.bestTarget(s, from, index) ?: return false
        return commit(game.move(s, from, index, to))
    }

    /**
     * "Tap a column, then tap where it goes". The first tap selects a pile, the second tap on a
     * destination moves the right part of the stack there; the player never aims at a single card.
     */
    fun tapPile(ref: PileRef): TapFeedback {
        val s = current?.takeIf { acceptsInput } ?: return TapFeedback.NONE
        if (ref.type == PileType.STOCK) return if (draw()) TapFeedback.MOVED else TapFeedback.INVALID
        val selected = _ui.value.selected
        if (selected == ref) {
            clearSelection()
            return TapFeedback.DESELECTED
        }
        if (selected != null) {
            game.cutIndexFor(s, selected, ref)?.let { index ->
                return if (commit(game.move(s, selected, index, ref))) TapFeedback.MOVED else TapFeedback.INVALID
            }
        }
        // Not a destination for the current selection: select this pile instead, if it can go anywhere.
        return if (game.destinationsFrom(s, ref).isNotEmpty()) {
            _ui.update { it.copy(selected = ref, hint = null) }
            TapFeedback.SELECTED
        } else {
            clearSelection()
            TapFeedback.INVALID
        }
    }

    fun clearSelection() {
        if (_ui.value.selected != null) _ui.update { it.copy(selected = null) }
    }

    fun drop(from: PileRef, index: Int, to: PileRef): Boolean {
        val s = current?.takeIf { acceptsInput } ?: return false
        return commit(game.move(s, from, index, to))
    }

    fun undo() {
        val t = _ui.value.timeline ?: return
        if (!game.options.allowUndo || !t.canUndo || _ui.value.won || _ui.value.dealing) return
        autoJob?.cancel()
        _ui.update { it.copy(timeline = t.undo(), hint = null, selected = null, autoCompleting = false) }
        save()
    }

    fun redo() {
        val t = _ui.value.timeline ?: return
        if (!t.canRedo || _ui.value.won || _ui.value.dealing) return
        autoJob?.cancel()
        _ui.update { it.copy(timeline = t.redo(), hint = null, selected = null, autoCompleting = false) }
        if (current?.isWon == true) onWin() else save()
    }

    fun hint() {
        val s = current?.takeIf { acceptsInput && game.options.allowHints } ?: return
        val hint = game.hint(s)
        if (hint == null) {
            _events.trySend(GameEvent.NO_HINT)
            return
        }
        _ui.update { it.copy(hint = hint, selected = null) }
        hintJob?.cancel()
        hintJob = viewModelScope.launch {
            delay(HINT_DURATION_MS)
            _ui.update { it.copy(hint = null) }
        }
    }

    fun autoComplete() {
        val s = current?.takeIf { acceptsInput } ?: return
        if (!game.canAutoComplete(s)) return
        autoJob?.cancel()
        autoJob = viewModelScope.launch {
            _ui.update { it.copy(autoCompleting = true, hint = null, selected = null) }
            if (foreground) clock.start()
            var first = true
            while (true) {
                val next = game.autoCompleteStep(current ?: break) ?: break
                // The whole auto-complete is a single undo step.
                _ui.update { it.copy(timeline = if (first) it.timeline!!.push(next) else it.timeline!!.replace(next)) }
                first = false
                if (next.isWon) break
                delay(AUTO_COMPLETE_STEP_MS)
            }
            _ui.update { it.copy(autoCompleting = false) }
            if (current?.isWon == true) onWin() else save()
        }
    }

    fun newGame() = viewModelScope.launch {
        recordLossIfInProgress()
        startGame(daily = null, seed = null)
    }

    fun restart() = viewModelScope.launch {
        val ui = _ui.value
        val seed = ui.state?.seed ?: return@launch
        recordLossIfInProgress()
        startGame(daily = ui.dailyEpochDay?.let(LocalDate::ofEpochDay), seed = seed)
    }

    fun dailyChallenge() = viewModelScope.launch {
        recordLossIfInProgress()
        startGame(daily = LocalDate.now(), seed = null)
    }

    // ---------------------------------------------------------------- lifecycle & settings

    fun onPause() {
        foreground = false
        clock.pause()
        save()
    }

    fun onResume() {
        foreground = true
        resumeClockIfPlaying()
    }

    fun updateSettings(transform: (SolitaireSettings) -> SolitaireSettings) {
        viewModelScope.launch { store.update(KEY_SETTINGS, SolitaireSettings.serializer(), SolitaireSettings(), transform) }
    }

    fun updateCommon(value: CommonSettings) {
        viewModelScope.launch { store.write(KEY_COMMON, CommonSettings.serializer(), value) }
    }

    fun resetStats() {
        viewModelScope.launch { statsRepo.reset() }
    }

    // ---------------------------------------------------------------- internals

    private fun commit(next: KlondikeState?): Boolean {
        if (next == null) return false
        autoJob?.cancel()
        hintJob?.cancel()
        _ui.update { it.copy(timeline = it.timeline!!.push(next), hint = null, selected = null) }
        if (foreground) clock.start()
        afterMove()
        return true
    }

    private fun afterMove() {
        if (current?.isWon == true) {
            onWin()
            return
        }
        if (!settings.value.autoMoveToFoundation) {
            save()
            return
        }
        autoJob = viewModelScope.launch {
            // Safe foundation moves belong to the player's move: they are undone together.
            while (true) {
                val s = current ?: break
                val m = game.nextSafeFoundationMove(s) ?: break
                delay(AUTO_MOVE_STEP_MS)
                val next = game.move(s, m.from, m.index, m.to) ?: break
                _ui.update { it.copy(timeline = it.timeline!!.replace(next)) }
            }
            if (current?.isWon == true) onWin() else save()
        }
    }

    private fun onWin() {
        clock.pause()
        val s = current ?: return
        val dailyKey = _ui.value.isDaily
        _ui.update { it.copy(won = true, hint = null, autoCompleting = false) }
        viewModelScope.launch {
            val score = s.score.takeIf { game.options.scoring != ScoringMode.NONE }
            statsRepo.recordWin(if (dailyKey) dailyStatsKey else statsKey, clock.elapsed.value, s.moves, score)
            store.remove(KEY_SAVED_GAME)
        }
    }

    private suspend fun recordLossIfInProgress() {
        val ui = _ui.value
        if (ui.inProgress) statsRepo.recordLoss(if (ui.isDaily) dailyStatsKey else statsKey, clock.elapsed.value)
    }

    private suspend fun startGame(daily: LocalDate?, seed: Long?) {
        autoJob?.cancel()
        hintJob?.cancel()
        clock.reset()
        _ui.update { it.copy(dealing = true, hint = null, won = false, autoCompleting = false) }
        val winnableOnly = store.observe(KEY_SETTINGS, SolitaireSettings.serializer(), SolitaireSettings()).first().winnableOnly
        val chosenSeed = seed ?: withContext(Dispatchers.Default) {
            val base = daily?.let { DailySeed.forDate(it, DAILY_SALT) } ?: Random.nextLong()
            // Daily challenges are always winnable so everyone gets a fair, identical deal.
            if (winnableOnly || daily != null) solver.findWinnableSeed(base) else base
        }
        _ui.value = SolitaireUiState(
            timeline = Timeline(game.deal(chosenSeed)),
            dailyEpochDay = daily?.toEpochDay(),
            dealing = false,
        )
        save()
    }

    private fun resumeClockIfPlaying() {
        if (foreground && _ui.value.inProgress && !_ui.value.dealing) clock.start()
    }

    // ---------------------------------------------------------------- save slots

    val canSaveToSlot: Boolean get() = _ui.value.let { !it.won && !it.dealing && it.timeline != null }

    fun saveToSlot(index: Int) {
        val snapshot = snapshot() ?: return
        viewModelScope.launch { slotRepo.save(index, snapshot) }
    }

    /** Replaces the current game with a saved one. Statistics are not touched. */
    fun loadSlot(index: Int) {
        viewModelScope.launch {
            val saved = slotRepo.load(index) ?: return@launch
            if (saved.options != game.options) return@launch
            autoJob?.cancel()
            hintJob?.cancel()
            _ui.value = SolitaireUiState(timeline = saved.timeline, dailyEpochDay = saved.dailyEpochDay, dealing = false)
            clock.reset(saved.elapsedSeconds)
            resumeClockIfPlaying()
            save()
        }
    }

    fun deleteSlot(index: Int) {
        viewModelScope.launch { slotRepo.delete(index) }
    }

    private fun snapshot(): SavedGame? {
        val ui = _ui.value
        val timeline = ui.timeline ?: return null
        if (ui.won || ui.dealing) return null
        return SavedGame(timeline.trimmed(SAVED_HISTORY), clock.elapsed.value, ui.dailyEpochDay, game.options)
    }

    private fun save() {
        val saved = snapshot() ?: return
        viewModelScope.launch { store.write(KEY_SAVED_GAME, SavedGame.serializer(), saved) }
    }

    private companion object {
        const val KEY_COMMON = "common_settings"
        const val KEY_SETTINGS = "solitaire_settings"
        const val KEY_SAVED_GAME = "solitaire_saved_game"
        const val KEY_SLOT_PREFIX = "solitaire_slot"
        const val SAVED_HISTORY = 40
        const val DAILY_SALT = 0x50_4C_49_54L
        const val HINT_DURATION_MS = 2500L
        const val AUTO_MOVE_STEP_MS = 140L
        const val AUTO_COMPLETE_STEP_MS = 90L
    }
}
