package io.github.ct4nk3r.mahjong

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
import io.github.ct4nk3r.mahjong.engine.Campaign
import io.github.ct4nk3r.mahjong.engine.Layout
import io.github.ct4nk3r.mahjong.engine.Layouts
import io.github.ct4nk3r.mahjong.engine.Mahjong
import io.github.ct4nk3r.mahjong.engine.MahjongOptions
import io.github.ct4nk3r.mahjong.engine.MahjongState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.time.LocalDate
import kotlin.random.Random

data class MahjongUiState(
    val timeline: Timeline<MahjongState>? = null,
    val dailyEpochDay: Long? = null,
    val dealing: Boolean = true,
    val selected: Int? = null,
    val hint: Pair<Int, Int>? = null,
    val won: Boolean = false,
    /** Campaign level being played, or null for a free game. */
    val campaignLevel: Int? = null,
    /** Hints are counted outside the undo history, so undoing never gives them back. */
    val hintsUsed: Int = 0,
    /** Stars earned when a campaign level was just won. */
    val starsEarned: Int? = null,
) {
    val state: MahjongState? get() = timeline?.current
    val layout: Layout? get() = state?.let { Layouts.byId(it.layoutId) }
    val inProgress: Boolean get() = !won && (state?.moves ?: 0) > 0
    val isDaily: Boolean get() = dailyEpochDay != null
    val isCampaign: Boolean get() = campaignLevel != null
}

/** Best stars per campaign level; finishing a level unlocks the next one. */
@Serializable
data class CampaignProgress(val stars: Map<Int, Int> = emptyMap()) {
    val unlocked: Int get() = minOf(Campaign.LEVELS.size, (stars.keys.maxOrNull() ?: 0) + 1)
    val totalStars: Int get() = stars.values.sum()
}

enum class MahjongEvent { NO_HINT }

/** What the Saved games screen needs to know about one save slot. */
data class SlotInfo(
    val savedAtMillis: Long,
    val elapsedSeconds: Long,
    val moves: Int,
    val progress: Float,
    val isDaily: Boolean,
    val layoutId: String,
    val campaignLevel: Int?,
)

@Serializable
private data class SavedGame(
    val timeline: Timeline<MahjongState>,
    val elapsedSeconds: Long,
    val dailyEpochDay: Long? = null,
    val options: MahjongOptions,
    val campaignLevel: Int? = null,
    val hintsUsed: Int = 0,
)

class MahjongViewModel(app: Application) : AndroidViewModel(app) {

    /** The rules in play: the default rules, or the current campaign level's. */
    val game: Mahjong get() = gameFor(_ui.value.campaignLevel)
    private val games = HashMap<MahjongOptions, Mahjong>()
    private fun optionsFor(campaignLevel: Int?) = campaignLevel?.let { Campaign.level(it).options } ?: MahjongOptions.DEFAULT
    private fun gameFor(campaignLevel: Int?) = optionsFor(campaignLevel).let { games.getOrPut(it) { Mahjong(it) } }
    private val store = KeyValueStore(app)
    private val statsRepo = StatsRepository(store)
    private val clock = GameClock(viewModelScope)

    val elapsed: StateFlow<Long> = clock.elapsed
    val common: StateFlow<CommonSettings> = store.observe(KEY_COMMON, CommonSettings.serializer(), CommonSettings())
        .stateIn(viewModelScope, SharingStarted.Eagerly, CommonSettings())
    val settings: StateFlow<MahjongSettings> = store.observe(KEY_SETTINGS, MahjongSettings.serializer(), MahjongSettings())
        .stateIn(viewModelScope, SharingStarted.Eagerly, MahjongSettings())
    private val slotRepo = SaveSlotRepository(store, KEY_SLOT_PREFIX, SavedGame.serializer())
    val slots: StateFlow<List<SlotInfo?>> = slotRepo.slots
        .map { list ->
            list.map { slot ->
                slot?.let {
                    val s = it.game.timeline.current
                    SlotInfo(
                        savedAtMillis = it.savedAtMillis,
                        elapsedSeconds = it.game.elapsedSeconds,
                        moves = s.moves,
                        progress = 1f - s.remaining.toFloat() / s.present.size,
                        isDaily = it.game.dailyEpochDay != null,
                        layoutId = s.layoutId,
                        campaignLevel = it.game.campaignLevel,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, List(slotRepo.count) { null })
    val stats: StateFlow<Map<String, GameStats>> = statsRepo.all
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val campaign: StateFlow<CampaignProgress> = store.observe(KEY_CAMPAIGN, CampaignProgress.serializer(), CampaignProgress())
        .stateIn(viewModelScope, SharingStarted.Eagerly, CampaignProgress())

    private val _ui = MutableStateFlow(MahjongUiState())
    val ui: StateFlow<MahjongUiState> = _ui.asStateFlow()

    private val _events = Channel<MahjongEvent>(Channel.BUFFERED)
    val events: Flow<MahjongEvent> = _events.receiveAsFlow()

    private var hintJob: Job? = null
    private var foreground = true

    init {
        viewModelScope.launch {
            val saved = store.read(KEY_SAVED_GAME, SavedGame.serializer())
            if (saved != null && !saved.timeline.current.isWon && restore(saved)) {
                resumeClockIfPlaying()
            } else {
                val last = store.read(KEY_SETTINGS, MahjongSettings.serializer())?.lastLayout ?: Layouts.TURTLE.id
                startGame(Layouts.byId(last), daily = null, seed = null)
            }
        }
    }

    fun statsKey(layoutId: String) = "mahjong-$layoutId"
    val dailyStatsKey = "mahjong-daily"
    val campaignStatsKey = "mahjong-campaign"

    private fun statsKeyFor(ui: MahjongUiState, layoutId: String) = when {
        ui.isCampaign -> campaignStatsKey
        ui.isDaily -> dailyStatsKey
        else -> statsKey(layoutId)
    }

    private val acceptsInput: Boolean get() = _ui.value.let { !it.dealing && !it.won && it.timeline != null }

    // ---------------------------------------------------------------- player actions

    /** What a tap did, so the screen can give matching sound and haptic feedback. */
    enum class TapResult { SELECTED, DESELECTED, MATCHED, BLOCKED, IGNORED }

    fun tapTile(index: Int): TapResult {
        if (!acceptsInput) return TapResult.IGNORED
        val ui = _ui.value
        val state = ui.state ?: return TapResult.IGNORED
        val layout = ui.layout ?: return TapResult.IGNORED
        if (!state.present[index]) return TapResult.IGNORED
        if (!game.isFree(layout, state, index)) return TapResult.BLOCKED
        val selected = ui.selected
        return when {
            selected == null -> {
                _ui.update { it.copy(selected = index) }
                TapResult.SELECTED
            }
            selected == index -> {
                _ui.update { it.copy(selected = null) }
                TapResult.DESELECTED
            }
            game.matches(state, selected, index) -> {
                val next = game.remove(layout, state, selected, index) ?: return TapResult.BLOCKED
                commit(next)
                TapResult.MATCHED
            }
            else -> {
                _ui.update { it.copy(selected = index) }
                TapResult.SELECTED
            }
        }
    }

    fun shuffle() {
        if (!acceptsInput) return
        val ui = _ui.value
        val state = ui.state ?: return
        val layout = ui.layout ?: return
        val next = game.shuffle(layout, state, Random.Default) ?: return
        commit(next)
    }

    fun undo() {
        val t = _ui.value.timeline ?: return
        if (!game.options.allowUndo || !t.canUndo || _ui.value.won || _ui.value.dealing) return
        _ui.update { it.copy(timeline = t.undo(), selected = null, hint = null) }
        save()
    }

    fun redo() {
        val t = _ui.value.timeline ?: return
        if (!t.canRedo || _ui.value.won || _ui.value.dealing) return
        _ui.update { it.copy(timeline = t.redo(), selected = null, hint = null) }
        if (_ui.value.state?.isWon == true) onWin() else save()
    }

    fun hint() {
        if (!acceptsInput || game.hintsRemaining(_ui.value.hintsUsed) == 0) return
        val ui = _ui.value
        val pair = game.hint(ui.layout ?: return, ui.state ?: return)
        if (pair == null) {
            _events.trySend(MahjongEvent.NO_HINT)
            return
        }
        _ui.update { it.copy(hint = pair, selected = null, hintsUsed = it.hintsUsed + 1) }
        save()
        hintJob?.cancel()
        hintJob = viewModelScope.launch {
            delay(HINT_DURATION_MS)
            _ui.update { it.copy(hint = null) }
        }
    }

    fun newGame(layoutId: String) = viewModelScope.launch {
        recordLossIfInProgress()
        updateSettings { it.copy(lastLayout = layoutId) }
        startGame(Layouts.byId(layoutId), daily = null, seed = null)
    }

    fun restart() = viewModelScope.launch {
        val ui = _ui.value
        val state = ui.state ?: return@launch
        recordLossIfInProgress()
        val level = ui.campaignLevel
        if (level != null) startLevel(level)
        else startGame(Layouts.byId(state.layoutId), daily = ui.dailyEpochDay?.let(LocalDate::ofEpochDay), seed = state.seed)
    }

    /** Starts a campaign level; locked levels are ignored. */
    fun playLevel(number: Int) = viewModelScope.launch {
        if (number !in 1..campaign.value.unlocked) return@launch
        recordLossIfInProgress()
        startLevel(number)
    }

    fun nextLevel() {
        val next = (_ui.value.campaignLevel ?: return) + 1
        if (next <= Campaign.LEVELS.size) playLevel(next)
    }

    fun dailyChallenge() = viewModelScope.launch {
        recordLossIfInProgress()
        startGame(Layouts.TURTLE, daily = LocalDate.now(), seed = null)
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

    fun updateSettings(transform: (MahjongSettings) -> MahjongSettings) {
        viewModelScope.launch { store.update(KEY_SETTINGS, MahjongSettings.serializer(), MahjongSettings(), transform) }
    }

    fun updateCommon(value: CommonSettings) {
        viewModelScope.launch { store.write(KEY_COMMON, CommonSettings.serializer(), value) }
    }

    fun resetStats() {
        viewModelScope.launch { statsRepo.reset() }
    }

    // ---------------------------------------------------------------- internals

    private fun commit(next: MahjongState) {
        hintJob?.cancel()
        _ui.update { it.copy(timeline = it.timeline!!.push(next), selected = null, hint = null) }
        if (foreground) clock.start()
        if (next.isWon) onWin() else save()
    }

    private fun onWin() {
        clock.pause()
        val ui = _ui.value
        val state = ui.state ?: return
        val level = ui.campaignLevel
        val stars = level?.let { Campaign.stars(ui.hintsUsed, state.shufflesUsed) }
        _ui.update { it.copy(won = true, selected = null, hint = null, starsEarned = stars) }
        viewModelScope.launch {
            statsRepo.recordWin(statsKeyFor(ui, state.layoutId), clock.elapsed.value, moves = null, score = null)
            if (level != null && stars != null) {
                store.update(KEY_CAMPAIGN, CampaignProgress.serializer(), CampaignProgress()) { p ->
                    p.copy(stars = p.stars + (level to maxOf(stars, p.stars[level] ?: 0)))
                }
            }
            store.remove(KEY_SAVED_GAME)
        }
    }

    private suspend fun recordLossIfInProgress() {
        val ui = _ui.value
        val state = ui.state ?: return
        if (ui.inProgress) statsRepo.recordLoss(statsKeyFor(ui, state.layoutId), clock.elapsed.value)
    }

    private suspend fun startLevel(number: Int) {
        val level = Campaign.level(number)
        startGame(level.layout, daily = null, seed = level.seed, campaignLevel = number, faceKinds = level.faceKinds)
    }

    private suspend fun startGame(
        layout: Layout,
        daily: LocalDate?,
        seed: Long?,
        campaignLevel: Int? = null,
        faceKinds: Int? = null,
    ) {
        hintJob?.cancel()
        clock.reset()
        _ui.update { it.copy(dealing = true, selected = null, hint = null, won = false) }
        val chosenSeed = seed ?: daily?.let { DailySeed.forDate(it, DAILY_SALT) } ?: Random.nextLong()
        val rules = gameFor(campaignLevel)
        val state = withContext(Dispatchers.Default) { rules.newGame(layout, chosenSeed, faceKinds) }
        _ui.value = MahjongUiState(
            timeline = Timeline(state),
            dailyEpochDay = daily?.toEpochDay(),
            dealing = false,
            campaignLevel = campaignLevel,
        )
        save()
    }

    /** Puts a saved game on the table. Returns false if it was made with rules we no longer use. */
    private fun restore(saved: SavedGame): Boolean {
        if (saved.options != optionsFor(saved.campaignLevel)) return false
        hintJob?.cancel()
        _ui.value = MahjongUiState(
            timeline = saved.timeline,
            dailyEpochDay = saved.dailyEpochDay,
            dealing = false,
            campaignLevel = saved.campaignLevel,
            hintsUsed = saved.hintsUsed,
        )
        clock.reset(saved.elapsedSeconds)
        return true
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
            if (!restore(saved)) return@launch
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
        return SavedGame(
            timeline = timeline.trimmed(SAVED_HISTORY),
            elapsedSeconds = clock.elapsed.value,
            dailyEpochDay = ui.dailyEpochDay,
            options = game.options,
            campaignLevel = ui.campaignLevel,
            hintsUsed = ui.hintsUsed,
        )
    }

    private fun save() {
        val saved = snapshot() ?: return
        viewModelScope.launch { store.write(KEY_SAVED_GAME, SavedGame.serializer(), saved) }
    }

    private companion object {
        const val KEY_COMMON = "common_settings"
        const val KEY_SETTINGS = "mahjong_settings"
        const val KEY_SAVED_GAME = "mahjong_saved_game"
        const val KEY_SLOT_PREFIX = "mahjong_slot"
        const val KEY_CAMPAIGN = "mahjong_campaign"
        const val SAVED_HISTORY = 40
        const val DAILY_SALT = 0x4D_41_48_4AL
        const val HINT_DURATION_MS = 2500L
    }
}
