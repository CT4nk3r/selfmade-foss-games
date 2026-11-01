package io.github.ct4nk3r.solitaire.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ct4nk3r.games.core.common.GameStats
import io.github.ct4nk3r.games.core.ui.AboutScreen
import io.github.ct4nk3r.games.core.ui.ColorSetting
import io.github.ct4nk3r.games.core.ui.CommonSettingsItems
import io.github.ct4nk3r.games.core.ui.SaveSlotsScreen
import io.github.ct4nk3r.games.core.ui.SettingsHeader
import io.github.ct4nk3r.games.core.ui.SlotSummary
import io.github.ct4nk3r.games.core.ui.SimpleScreen
import io.github.ct4nk3r.games.core.ui.StatsScreen
import io.github.ct4nk3r.games.core.ui.StatsSection
import io.github.ct4nk3r.games.core.ui.SwitchSetting
import io.github.ct4nk3r.games.core.ui.TextScreen
import io.github.ct4nk3r.solitaire.CardBack
import io.github.ct4nk3r.solitaire.R
import io.github.ct4nk3r.solitaire.SolitaireViewModel
import io.github.ct4nk3r.solitaire.TableColor
import io.github.ct4nk3r.solitaire.engine.ScoringMode
import io.github.ct4nk3r.games.core.ui.R as CoreR

enum class Screen { GAME, SETTINGS, STATS, SAVES, RULES, ABOUT }

@Composable
fun SolitaireApp(vm: SolitaireViewModel) {
    var screen by rememberSaveable { mutableStateOf(Screen.GAME) }
    val back = { screen = Screen.GAME }
    when (screen) {
        Screen.GAME -> GameScreen(vm, onNavigate = { screen = it })
        Screen.SETTINGS -> SettingsScreen(vm, back)
        Screen.STATS -> {
            val stats by vm.stats.collectAsStateWithLifecycle()
            val showScore = vm.game.options.scoring != ScoringMode.NONE
            StatsScreen(
                sections = listOf(
                    StatsSection(stringResource(R.string.variant_klondike), stats[vm.statsKey] ?: GameStats(), showScore = showScore),
                    StatsSection(stringResource(CoreR.string.action_daily), stats[vm.dailyStatsKey] ?: GameStats(), showScore = showScore),
                ),
                onReset = vm::resetStats,
                onBack = back,
            )
        }
        Screen.SAVES -> {
            val slots by vm.slots.collectAsStateWithLifecycle()
            val ui by vm.ui.collectAsStateWithLifecycle()
            val klondike = stringResource(R.string.variant_klondike)
            val daily = stringResource(CoreR.string.action_daily)
            SaveSlotsScreen(
                slots = slots.map { slot ->
                    slot?.let { SlotSummary(it.savedAtMillis, if (it.isDaily) daily else klondike, it.elapsedSeconds, it.moves, it.progress) }
                },
                canSave = vm.canSaveToSlot,
                gameInProgress = ui.inProgress,
                onSave = vm::saveToSlot,
                onLoad = {
                    vm.loadSlot(it)
                    back()
                },
                onDelete = vm::deleteSlot,
                onBack = back,
            )
        }
        Screen.RULES -> TextScreen(
            title = stringResource(CoreR.string.action_how_to_play),
            sections = listOf(
                stringResource(R.string.rules_goal_title) to stringResource(R.string.rules_goal),
                stringResource(R.string.rules_table_title) to stringResource(R.string.rules_table),
                stringResource(R.string.rules_moves_title) to stringResource(R.string.rules_moves),
                stringResource(R.string.rules_stock_title) to stringResource(R.string.rules_stock),
                stringResource(R.string.rules_controls_title) to stringResource(R.string.rules_controls),
                stringResource(R.string.rules_scoring_title) to stringResource(R.string.rules_scoring),
            ),
            onBack = back,
        )
        Screen.ABOUT -> {
            val context = LocalContext.current
            val version = remember(context) {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
            }
            AboutScreen(appName = stringResource(R.string.app_name), versionName = version, onBack = back)
        }
    }
}

@Composable
private fun SettingsScreen(vm: SolitaireViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val common by vm.common.collectAsStateWithLifecycle()
    SimpleScreen(title = stringResource(CoreR.string.action_settings), onBack = onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsHeader(stringResource(CoreR.string.settings_section_game))
            SwitchSetting(
                title = stringResource(R.string.setting_tap_to_select),
                summary = stringResource(R.string.setting_tap_to_select_summary),
                checked = settings.tapToSelect,
                onCheckedChange = { v -> vm.updateSettings { it.copy(tapToSelect = v) } },
            )
            SwitchSetting(
                title = stringResource(R.string.setting_tap_to_move),
                summary = stringResource(R.string.setting_tap_to_move_summary),
                checked = settings.tapToMove,
                onCheckedChange = { v -> vm.updateSettings { it.copy(tapToMove = v) } },
            )
            SwitchSetting(
                title = stringResource(R.string.setting_auto_foundation),
                summary = stringResource(R.string.setting_auto_foundation_summary),
                checked = settings.autoMoveToFoundation,
                onCheckedChange = { v -> vm.updateSettings { it.copy(autoMoveToFoundation = v) } },
            )
            SwitchSetting(
                title = stringResource(R.string.setting_winnable_only),
                summary = stringResource(R.string.setting_winnable_only_summary),
                checked = settings.winnableOnly,
                onCheckedChange = { v -> vm.updateSettings { it.copy(winnableOnly = v) } },
            )
            SwitchSetting(
                title = stringResource(R.string.setting_left_handed),
                summary = stringResource(R.string.setting_left_handed_summary),
                checked = settings.leftHanded,
                onCheckedChange = { v -> vm.updateSettings { it.copy(leftHanded = v) } },
            )

            SettingsHeader(stringResource(CoreR.string.settings_section_appearance))
            SwitchSetting(
                title = stringResource(R.string.setting_large_labels),
                summary = stringResource(R.string.setting_large_labels_summary),
                checked = settings.largeLabels,
                onCheckedChange = { v -> vm.updateSettings { it.copy(largeLabels = v) } },
            )
            SwitchSetting(
                title = stringResource(R.string.setting_four_colors),
                summary = stringResource(R.string.setting_four_colors_summary),
                checked = settings.fourColorDeck,
                onCheckedChange = { v -> vm.updateSettings { it.copy(fourColorDeck = v) } },
            )
            ColorSetting(
                title = stringResource(R.string.setting_table_color),
                options = TableColor.entries,
                selected = settings.tableColor,
                color = { it.color },
                label = { it.label() },
                onSelect = { v -> vm.updateSettings { it.copy(tableColor = v) } },
            )
            ColorSetting(
                title = stringResource(R.string.setting_card_back),
                options = CardBack.entries,
                selected = settings.cardBack,
                color = { it.color },
                label = { it.label() },
                onSelect = { v -> vm.updateSettings { it.copy(cardBack = v) } },
            )

            SettingsHeader(stringResource(CoreR.string.settings_section_general))
            CommonSettingsItems(common, vm::updateCommon)
        }
    }
}

@Composable
private fun TableColor.label(): String = stringResource(
    when (this) {
        TableColor.GREEN -> CoreR.string.color_green
        TableColor.BLUE -> CoreR.string.color_blue
        TableColor.RED -> CoreR.string.color_red
        TableColor.GRAY -> CoreR.string.color_gray
        TableColor.PURPLE -> CoreR.string.color_purple
    },
)

@Composable
private fun CardBack.label(): String = stringResource(
    when (this) {
        CardBack.BLUE -> CoreR.string.color_blue
        CardBack.RED -> CoreR.string.color_red
        CardBack.GREEN -> CoreR.string.color_green
        CardBack.PURPLE -> CoreR.string.color_purple
        CardBack.BLACK -> CoreR.string.color_black
    },
)
