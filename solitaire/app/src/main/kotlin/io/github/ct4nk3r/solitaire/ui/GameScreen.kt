package io.github.ct4nk3r.solitaire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ct4nk3r.games.core.common.formatDuration
import io.github.ct4nk3r.games.core.ui.BarAction
import io.github.ct4nk3r.games.core.ui.Confetti
import io.github.ct4nk3r.games.core.ui.ConfirmDialog
import io.github.ct4nk3r.games.core.ui.GameInfo
import io.github.ct4nk3r.games.core.ui.WinDialog
import io.github.ct4nk3r.games.core.ui.SoundEffect
import io.github.ct4nk3r.games.core.ui.rememberGameHaptics
import io.github.ct4nk3r.games.core.ui.rememberGameSounds
import io.github.ct4nk3r.solitaire.GameEvent
import io.github.ct4nk3r.solitaire.TapFeedback
import io.github.ct4nk3r.solitaire.engine.PileType
import io.github.ct4nk3r.solitaire.R
import io.github.ct4nk3r.solitaire.SolitaireViewModel
import io.github.ct4nk3r.solitaire.engine.ScoringMode
import io.github.ct4nk3r.games.core.ui.R as CoreR

private enum class Pending { NEW_GAME, RESTART, DAILY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(vm: SolitaireViewModel, onNavigate: (Screen) -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val common by vm.common.collectAsStateWithLifecycle()
    val elapsed by vm.elapsed.collectAsStateWithLifecycle()
    val haptics = rememberGameHaptics(common.haptics)
    val sounds = rememberGameSounds(common.sound)
    LaunchedEffect(ui.won) { if (ui.won) sounds.play(SoundEffect.WIN) }
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pending?>(null) }
    var winDialogDismissed by rememberSaveable(ui.state?.seed, ui.won) { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { vm.onPause() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResume() }

    val noHint = stringResource(CoreR.string.no_hint)
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                GameEvent.NO_HINT -> {
                    haptics.error()
                    snackbar.showSnackbar(noHint)
                }
            }
        }
    }

    fun perform(action: Pending) = when (action) {
        Pending.NEW_GAME -> vm.newGame()
        Pending.RESTART -> vm.restart()
        Pending.DAILY -> vm.dailyChallenge()
    }

    fun request(action: Pending) {
        if (ui.inProgress) pending = action else perform(action)
    }

    val state = ui.state
    val timeline = ui.timeline

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (ui.isDaily) stringResource(CoreR.string.action_daily) else stringResource(R.string.variant_klondike),
                            maxLines = 1,
                        )
                        if (state != null) {
                            val items = buildList {
                                if (common.showTimer) add(stringResource(CoreR.string.label_time) to formatDuration(elapsed))
                                add(stringResource(CoreR.string.label_moves) to state.moves.toString())
                                if (vm.game.options.scoring != ScoringMode.NONE) {
                                    add(stringResource(CoreR.string.label_score) to state.score.toString())
                                }
                            }
                            GameInfo(items)
                        }
                    }
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                BarAction(Icons.Filled.Add, stringResource(CoreR.string.action_new_game), { request(Pending.NEW_GAME) }, Modifier.weight(1f))
                BarAction(
                    Icons.AutoMirrored.Filled.Undo,
                    stringResource(CoreR.string.bar_undo),
                    { vm.undo() },
                    Modifier.weight(1f),
                    enabled = vm.game.options.allowUndo && timeline?.canUndo == true && !ui.won,
                )
                if (state != null && vm.game.canAutoComplete(state) && !ui.autoCompleting) {
                    BarAction(Icons.Filled.DoneAll, stringResource(R.string.action_auto_complete), { vm.autoComplete() }, Modifier.weight(1f))
                } else {
                    BarAction(
                        Icons.Filled.Lightbulb,
                        stringResource(CoreR.string.action_hint),
                        { vm.hint() },
                        Modifier.weight(1f),
                        enabled = vm.game.options.allowHints && !ui.won && !ui.dealing,
                    )
                }
                BarAction(
                    Icons.Filled.Save,
                    stringResource(CoreR.string.action_save),
                    { onNavigate(Screen.SAVES) },
                    Modifier.weight(1f),
                    enabled = !ui.dealing,
                )
                Box(Modifier.weight(1f)) {
                    BarAction(Icons.Filled.MoreVert, stringResource(CoreR.string.action_more), { menuOpen = true }, Modifier.fillMaxWidth())
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        MenuItem(Icons.AutoMirrored.Filled.Redo, stringResource(CoreR.string.action_redo), enabled = timeline?.canRedo == true) {
                            menuOpen = false
                            vm.redo()
                        }
                        MenuItem(Icons.Filled.Replay, stringResource(CoreR.string.action_restart)) {
                            menuOpen = false
                            request(Pending.RESTART)
                        }
                        MenuItem(Icons.Filled.Today, stringResource(CoreR.string.action_daily)) {
                            menuOpen = false
                            request(Pending.DAILY)
                        }
                        MenuItem(Icons.Filled.Save, stringResource(CoreR.string.saves_title)) {
                            menuOpen = false
                            onNavigate(Screen.SAVES)
                        }
                        MenuItem(Icons.Filled.BarChart, stringResource(CoreR.string.action_statistics)) {
                            menuOpen = false
                            onNavigate(Screen.STATS)
                        }
                        MenuItem(Icons.Filled.Settings, stringResource(CoreR.string.action_settings)) {
                            menuOpen = false
                            onNavigate(Screen.SETTINGS)
                        }
                        MenuItem(Icons.AutoMirrored.Filled.HelpOutline, stringResource(CoreR.string.action_how_to_play)) {
                            menuOpen = false
                            onNavigate(Screen.RULES)
                        }
                        MenuItem(Icons.Filled.Info, stringResource(CoreR.string.action_about)) {
                            menuOpen = false
                            onNavigate(Screen.ABOUT)
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(settings.tableColor.color),
        ) {
            if (state == null || ui.dealing) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Text(stringResource(CoreR.string.dealing), color = Color.White)
                }
            } else {
                SolitaireBoard(
                    state = state,
                    game = vm.game,
                    leftHanded = settings.leftHanded,
                    style = CardStyle(settings.cardBack.color, settings.largeLabels, settings.fourColorDeck),
                    hint = ui.hint,
                    selected = ui.selected,
                    onTap = { pile, index ->
                        val result = when {
                            pile.type == PileType.STOCK -> if (vm.draw()) TapFeedback.MOVED else TapFeedback.INVALID
                            // Easy mode: a tap on a card sends it to the best place at once.
                            settings.tapToMove -> when {
                                index == null -> TapFeedback.NONE
                                vm.tapCard(pile, index) -> TapFeedback.MOVED
                                else -> TapFeedback.INVALID
                            }
                            settings.tapToSelect -> vm.tapPile(pile)
                            else -> TapFeedback.NONE
                        }
                        when (result) {
                            TapFeedback.MOVED -> {
                                sounds.play(SoundEffect.CARD)
                                haptics.move()
                            }
                            TapFeedback.SELECTED, TapFeedback.DESELECTED -> {
                                sounds.play(SoundEffect.SELECT)
                                haptics.move()
                            }
                            TapFeedback.INVALID -> {
                                sounds.play(SoundEffect.ERROR)
                                haptics.error()
                            }
                            TapFeedback.NONE -> Unit
                        }
                    },
                    onDragStart = vm::clearSelection,
                    onDrop = vm::drop,
                    onFeedback = { ok ->
                        if (ok) {
                            sounds.play(SoundEffect.CARD)
                            haptics.move()
                        } else {
                            sounds.play(SoundEffect.ERROR)
                            haptics.error()
                        }
                    },
                )
                vm.game.redealsRemaining(state)?.let { left ->
                    Text(
                        pluralStringResource(R.plurals.redeals_left, left, left),
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                    )
                }
            }
            if (ui.won) Confetti()
        }
    }

    if (ui.won && state != null && !winDialogDismissed) {
        WinDialog(
            title = stringResource(if (ui.isDaily) CoreR.string.win_daily_title else CoreR.string.win_title),
            lines = buildList {
                add(stringResource(CoreR.string.label_time) to formatDuration(elapsed))
                add(stringResource(CoreR.string.label_moves) to state.moves.toString())
                if (vm.game.options.scoring != ScoringMode.NONE) add(stringResource(CoreR.string.label_score) to state.score.toString())
            },
            onNewGame = { vm.newGame() },
            onDismiss = { winDialogDismissed = true },
        )
    }

    pending?.let { action ->
        ConfirmDialog(
            title = stringResource(CoreR.string.confirm_new_game_title),
            message = stringResource(CoreR.string.confirm_new_game_message),
            confirmLabel = stringResource(CoreR.string.confirm_continue),
            onConfirm = { perform(action) },
            onDismiss = { pending = null },
        )
    }
}

@Composable
private fun MenuItem(icon: ImageVector, text: String, enabled: Boolean = true, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        enabled = enabled,
        onClick = onClick,
    )
}
