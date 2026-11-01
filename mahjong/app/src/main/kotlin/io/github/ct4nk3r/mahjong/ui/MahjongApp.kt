package io.github.ct4nk3r.mahjong.ui

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
import io.github.ct4nk3r.mahjong.Background
import io.github.ct4nk3r.mahjong.MahjongViewModel
import io.github.ct4nk3r.mahjong.R
import io.github.ct4nk3r.mahjong.engine.Campaign
import io.github.ct4nk3r.mahjong.engine.Layouts
import io.github.ct4nk3r.games.core.ui.R as CoreR

enum class Screen { GAME, CAMPAIGN, SETTINGS, STATS, SAVES, RULES, ABOUT }

@Composable
fun MahjongApp(vm: MahjongViewModel) {
    var screen by rememberSaveable { mutableStateOf(Screen.GAME) }
    val back = { screen = Screen.GAME }
    when (screen) {
        Screen.GAME -> GameScreen(vm, onNavigate = { screen = it })
        Screen.CAMPAIGN -> {
            val progress by vm.campaign.collectAsStateWithLifecycle()
            val ui by vm.ui.collectAsStateWithLifecycle()
            CampaignScreen(
                progress = progress,
                gameInProgress = ui.inProgress,
                onPlay = {
                    vm.playLevel(it)
                    back()
                },
                onBack = back,
            )
        }
        Screen.SETTINGS -> SettingsScreen(vm, back)
        Screen.STATS -> {
            val stats by vm.stats.collectAsStateWithLifecycle()
            val sections = buildList {
                stats[vm.campaignStatsKey]?.takeIf { it.played > 0 }?.let {
                    add(StatsSection(stringResource(R.string.campaign_title), it, showMoves = false))
                }
                Layouts.ALL.forEach { l ->
                    stats[vm.statsKey(l.id)]?.takeIf { it.played > 0 }?.let { add(StatsSection(layoutName(l.id), it, showMoves = false)) }
                }
                stats[vm.dailyStatsKey]?.takeIf { it.played > 0 }?.let {
                    add(StatsSection(stringResource(CoreR.string.action_daily), it, showMoves = false))
                }
            }
            StatsScreen(sections = sections, onReset = vm::resetStats, onBack = back)
        }
        Screen.SAVES -> {
            val slots by vm.slots.collectAsStateWithLifecycle()
            val ui by vm.ui.collectAsStateWithLifecycle()
            val names = Layouts.ALL.associate { it.id to layoutName(it.id) }
            val daily = stringResource(CoreR.string.action_daily)
            val campaignTitle = stringResource(R.string.campaign_title)
            val levelTitles = (1..Campaign.LEVELS.size).associateWith { stringResource(R.string.level_title, it) }
            SaveSlotsScreen(
                slots = slots.map { slot ->
                    slot?.let {
                        val name = names[it.layoutId] ?: it.layoutId
                        val title = when {
                            it.campaignLevel != null -> "$campaignTitle · ${levelTitles[it.campaignLevel]}"
                            it.isDaily -> "$daily · $name"
                            else -> name
                        }
                        SlotSummary(it.savedAtMillis, title, it.elapsedSeconds, it.moves, it.progress)
                    }
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
                stringResource(R.string.campaign_title) to stringResource(R.string.rules_campaign),
                stringResource(R.string.rules_free_title) to stringResource(R.string.rules_free),
                stringResource(R.string.rules_matching_title) to stringResource(R.string.rules_matching),
                stringResource(R.string.rules_help_title) to stringResource(R.string.rules_help),
                stringResource(R.string.rules_controls_title) to stringResource(R.string.rules_controls),
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
private fun SettingsScreen(vm: MahjongViewModel, onBack: () -> Unit) {
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
                title = stringResource(R.string.setting_highlight_free),
                summary = stringResource(R.string.setting_highlight_free_summary),
                checked = settings.highlightFree,
                onCheckedChange = { v -> vm.updateSettings { it.copy(highlightFree = v) } },
            )
            SwitchSetting(
                title = stringResource(R.string.setting_show_labels),
                summary = stringResource(R.string.setting_show_labels_summary),
                checked = settings.showLabels,
                onCheckedChange = { v -> vm.updateSettings { it.copy(showLabels = v) } },
            )

            SettingsHeader(stringResource(CoreR.string.settings_section_appearance))
            ColorSetting(
                title = stringResource(R.string.setting_background),
                options = Background.entries,
                selected = settings.background,
                color = { it.color },
                label = { it.label() },
                onSelect = { v -> vm.updateSettings { it.copy(background = v) } },
            )

            SettingsHeader(stringResource(CoreR.string.settings_section_general))
            CommonSettingsItems(common, vm::updateCommon)
        }
    }
}

@Composable
private fun Background.label(): String = stringResource(
    when (this) {
        Background.GREEN -> CoreR.string.color_green
        Background.BLUE -> CoreR.string.color_blue
        Background.RED -> CoreR.string.color_red
        Background.BROWN -> CoreR.string.color_brown
        Background.GRAY -> CoreR.string.color_gray
    },
)
