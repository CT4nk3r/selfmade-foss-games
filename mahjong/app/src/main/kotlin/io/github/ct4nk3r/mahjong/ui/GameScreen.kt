package io.github.ct4nk3r.mahjong.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
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
import io.github.ct4nk3r.mahjong.MahjongEvent
import io.github.ct4nk3r.mahjong.MahjongViewModel
import io.github.ct4nk3r.mahjong.MahjongViewModel.TapResult
import io.github.ct4nk3r.mahjong.R
import io.github.ct4nk3r.mahjong.engine.Campaign
import io.github.ct4nk3r.mahjong.engine.Layouts
import io.github.ct4nk3r.games.core.ui.R as CoreR

private sealed interface Pending {
    data class NewGame(val layoutId: String) : Pending
    data object Restart : Pending
    data object Daily : Pending
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(vm: MahjongViewModel, onNavigate: (Screen) -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val common by vm.common.collectAsStateWithLifecycle()
    val elapsed by vm.elapsed.collectAsStateWithLifecycle()
    val campaign by vm.campaign.collectAsStateWithLifecycle()
    val haptics = rememberGameHaptics(common.haptics)
    val sounds = rememberGameSounds(common.sound)
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(ui.won) { if (ui.won) sounds.play(SoundEffect.WIN) }
    var menuOpen by remember { mutableStateOf(false) }
    var pickLayout by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pending?>(null) }
    var winDialogDismissed by rememberSaveable(ui.state?.seed, ui.won) { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { vm.onPause() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResume() }

    val noHint = stringResource(CoreR.string.no_hint)
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                MahjongEvent.NO_HINT -> {
                    haptics.error()
                    snackbar.showSnackbar(noHint)
                }
            }
        }
    }

    fun perform(action: Pending) {
        when (action) {
            is Pending.NewGame -> vm.newGame(action.layoutId)
            Pending.Restart -> vm.restart()
            Pending.Daily -> vm.dailyChallenge()
        }
    }

    fun request(action: Pending) {
        if (ui.inProgress) pending = action else perform(action)
    }

    val state = ui.state
    val layout = ui.layout
    val stuck = state != null && layout != null && !ui.dealing && vm.game.isStuck(layout, state)
    var stuckDismissed by rememberSaveable(state?.moves, state?.shufflesUsed) { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            when {
                                ui.campaignLevel != null -> stringResource(R.string.level_title, ui.campaignLevel!!)
                                ui.isDaily -> stringResource(CoreR.string.action_daily)
                                else -> layout?.let { layoutName(it.id) } ?: stringResource(R.string.app_name)
                            },
                            maxLines = 1,
                        )
                        if (state != null && layout != null) {
                            val items = buildList {
                                if (common.showTimer) add(stringResource(CoreR.string.label_time) to formatDuration(elapsed))
                                add(stringResource(R.string.label_tiles) to state.remaining.toString())
                                add(stringResource(R.string.label_pairs) to vm.game.availablePairs(layout, state).size.toString())
                            }
                            GameInfo(items)
                        }
                    }
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                BarAction(Icons.Filled.Add, stringResource(CoreR.string.action_new_game), { pickLayout = true }, Modifier.weight(1f))
                BarAction(
                    Icons.AutoMirrored.Filled.Undo,
                    stringResource(CoreR.string.bar_undo),
                    { vm.undo() },
                    Modifier.weight(1f),
                    enabled = vm.game.options.allowUndo && ui.timeline?.canUndo == true && !ui.won,
                )
                // Limited help (campaign) shows what is left, e.g. "Tipp (2)".
                val hintsLeft = vm.game.hintsRemaining(ui.hintsUsed)
                val shufflesLeft = state?.let { vm.game.shufflesRemaining(it) }
                BarAction(
                    Icons.Filled.Lightbulb,
                    withCount(stringResource(CoreR.string.action_hint), hintsLeft),
                    { vm.hint() },
                    Modifier.weight(1f),
                    enabled = hintsLeft != 0 && !ui.won && !ui.dealing,
                )
                BarAction(
                    Icons.Filled.Shuffle,
                    withCount(stringResource(R.string.action_shuffle), shufflesLeft),
                    { vm.shuffle() },
                    Modifier.weight(1f),
                    enabled = state != null && vm.game.canShuffle(state) && !ui.dealing,
                )
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
                        MenuItem(Icons.Filled.EmojiEvents, stringResource(R.string.campaign_title)) {
                            menuOpen = false
                            onNavigate(Screen.CAMPAIGN)
                        }
                        MenuItem(Icons.AutoMirrored.Filled.Redo, stringResource(CoreR.string.action_redo), enabled = ui.timeline?.canRedo == true) {
                            menuOpen = false
                            vm.redo()
                        }
                        MenuItem(Icons.Filled.Replay, stringResource(CoreR.string.action_restart)) {
                            menuOpen = false
                            request(Pending.Restart)
                        }
                        MenuItem(Icons.Filled.Today, stringResource(CoreR.string.action_daily)) {
                            menuOpen = false
                            request(Pending.Daily)
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
                .background(settings.background.color),
        ) {
            if (state == null || layout == null || ui.dealing) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Text(stringResource(CoreR.string.dealing), color = Color.White)
                }
            } else {
                MahjongBoard(
                    layout = layout,
                    state = state,
                    selected = ui.selected,
                    hint = ui.hint,
                    highlightFree = settings.highlightFree,
                    showLabels = settings.showLabels,
                    zoom = settings.zoom,
                    onZoomChange = { z -> vm.updateSettings { it.copy(zoom = z) } },
                    onTap = { i ->
                        when (vm.tapTile(i)) {
                            TapResult.SELECTED, TapResult.DESELECTED -> {
                                sounds.play(SoundEffect.SELECT)
                                haptics.move()
                            }
                            TapResult.MATCHED -> {
                                sounds.play(SoundEffect.MATCH)
                                haptics.move()
                            }
                            TapResult.BLOCKED -> {
                                sounds.play(SoundEffect.ERROR)
                                haptics.error()
                            }
                            TapResult.IGNORED -> Unit
                        }
                    },
                )
            }
            if (ui.won) Confetti()
        }
    }

    if (pickLayout) {
        LayoutPicker(
            initial = layout?.takeIf { !ui.isCampaign }?.id ?: settings.lastLayout,
            campaignLevel = campaign.unlocked,
            onCampaign = {
                pickLayout = false
                onNavigate(Screen.CAMPAIGN)
            },
            onPick = { id ->
                pickLayout = false
                request(Pending.NewGame(id))
            },
            onDismiss = { pickLayout = false },
        )
    }

    val wonLevel = ui.campaignLevel
    if (ui.won && state != null && wonLevel != null) {
        // Stays up until the player picks where to go next.
        LevelCompleteDialog(
            level = wonLevel,
            stars = ui.starsEarned ?: 0,
            time = formatDuration(elapsed),
            onNext = vm::nextLevel,
            onRestart = { vm.restart() },
            onMenu = { onNavigate(Screen.CAMPAIGN) },
        )
    } else if (ui.won && state != null && !winDialogDismissed) {
        WinDialog(
            title = stringResource(if (ui.isDaily) CoreR.string.win_daily_title else CoreR.string.win_title),
            lines = listOf(stringResource(CoreR.string.label_time) to formatDuration(elapsed)),
            onNewGame = { pickLayout = true },
            onDismiss = { winDialogDismissed = true },
        )
    }

    if (stuck && !stuckDismissed && state != null) {
        AlertDialog(
            onDismissRequest = { stuckDismissed = true },
            title = { Text(stringResource(R.string.stuck_title)) },
            text = { Text(stringResource(R.string.stuck_message)) },
            confirmButton = {
                if (vm.game.canShuffle(state)) {
                    TextButton(onClick = { vm.shuffle() }) { Text(stringResource(R.string.action_shuffle)) }
                } else if (ui.isCampaign) {
                    TextButton(onClick = {
                        stuckDismissed = true
                        vm.restart()
                    }) { Text(stringResource(R.string.action_restart_level)) }
                } else {
                    TextButton(onClick = { pickLayout = true }) { Text(stringResource(CoreR.string.action_new_game)) }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    stuckDismissed = true
                    vm.undo()
                }) { Text(stringResource(CoreR.string.action_undo)) }
            },
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
private fun LayoutPicker(
    initial: String,
    campaignLevel: Int,
    onCampaign: () -> Unit,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var choice by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_layout)) },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                // The campaign first: the natural place to start.
                FilledTonalButton(
                    onClick = onCampaign,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    Icon(Icons.Filled.EmojiEvents, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.campaign_entry, campaignLevel), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                Layouts.ALL.forEach { l ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = l.id == choice, role = Role.RadioButton, onClick = { choice = l.id })
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = l.id == choice, onClick = null)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(layoutName(l.id), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                pluralStringResource(R.plurals.tile_count, l.size, l.size),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(choice) }) { Text(stringResource(R.string.action_play)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreR.string.action_cancel)) } },
    )
}

/** End of a campaign level: cannot be dismissed, only left through one of its buttons. */
@Composable
private fun LevelCompleteDialog(
    level: Int,
    stars: Int,
    time: String,
    onNext: () -> Unit,
    onRestart: () -> Unit,
    onMenu: () -> Unit,
) {
    val last = level >= Campaign.LEVELS.size
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = {
            Text(
                stringResource(if (last) R.string.campaign_complete else R.string.level_complete),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.level_title, level), style = MaterialTheme.typography.titleMedium)
                Text(starsText(stars), fontSize = 44.sp, color = Color(0xFFFFB300))
                Text("${stringResource(CoreR.string.label_time)} $time", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                val buttonModifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
                if (!last) {
                    Button(onClick = onNext, modifier = buttonModifier) {
                        Text(stringResource(R.string.action_next_level), style = MaterialTheme.typography.titleMedium)
                    }
                }
                OutlinedButton(onClick = onRestart, modifier = buttonModifier) {
                    Text(stringResource(R.string.action_restart_level), style = MaterialTheme.typography.titleMedium)
                }
                OutlinedButton(onClick = onMenu, modifier = buttonModifier) {
                    Text(stringResource(R.string.action_main_menu), style = MaterialTheme.typography.titleMedium)
                }
            }
        },
        confirmButton = {},
    )
}

/** "Hint (2)" when the count is limited, plain "Hint" when it is not. */
private fun withCount(label: String, count: Int?) = if (count == null) label else "$label ($count)"

@Composable
fun layoutName(id: String): String = stringResource(
    when (id) {
        "turtle" -> R.string.layout_turtle
        "pyramid" -> R.string.layout_pyramid
        "fortress" -> R.string.layout_fortress
        "bridge" -> R.string.layout_bridge
        "cross" -> R.string.layout_cross
        "twin_peaks" -> R.string.layout_twin_peaks
        "diamond" -> R.string.layout_diamond
        "beginner" -> R.string.layout_beginner
        else -> R.string.app_name
    },
)

@Composable
private fun MenuItem(icon: ImageVector, text: String, enabled: Boolean = true, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        enabled = enabled,
        onClick = onClick,
    )
}
