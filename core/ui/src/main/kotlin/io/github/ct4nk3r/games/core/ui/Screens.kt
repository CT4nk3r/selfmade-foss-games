package io.github.ct4nk3r.games.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ct4nk3r.games.core.common.GameStats
import io.github.ct4nk3r.games.core.common.formatDuration
import java.text.NumberFormat

data class StatsSection(
    val title: String,
    val stats: GameStats,
    val showMoves: Boolean = true,
    val showScore: Boolean = false,
)

@Composable
fun StatsScreen(sections: List<StatsSection>, onReset: () -> Unit, onBack: () -> Unit) {
    var confirmReset by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val percent = remember(locale) { NumberFormat.getPercentInstance(locale) }
    val none = stringResource(R.string.stats_none)

    SimpleScreen(
        title = stringResource(R.string.action_statistics),
        onBack = onBack,
        actions = {
            IconButton(onClick = { confirmReset = true }) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.stats_reset_title))
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (sections.isEmpty()) {
                Text(stringResource(R.string.stats_empty), style = MaterialTheme.typography.bodyLarge)
            }
            sections.forEachIndexed { i, section ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 16.dp))
                Text(section.title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                val s = section.stats
                StatRow(stringResource(R.string.stats_played), s.played.toString())
                StatRow(stringResource(R.string.stats_won), s.won.toString())
                StatRow(stringResource(R.string.stats_win_rate), percent.format(s.winRate))
                StatRow(stringResource(R.string.stats_current_streak), s.currentStreak.toString())
                StatRow(stringResource(R.string.stats_best_streak), s.bestStreak.toString())
                StatRow(stringResource(R.string.stats_best_time), s.bestTimeSeconds?.let(::formatDuration) ?: none)
                if (section.showMoves) StatRow(stringResource(R.string.stats_best_moves), s.bestMoves?.toString() ?: none)
                if (section.showScore) StatRow(stringResource(R.string.stats_best_score), s.bestScore?.toString() ?: none)
                StatRow(stringResource(R.string.stats_total_time), formatDuration(s.totalTimeSeconds))
            }
        }
    }

    if (confirmReset) {
        ConfirmDialog(
            title = stringResource(R.string.stats_reset_title),
            message = stringResource(R.string.stats_reset_message),
            confirmLabel = stringResource(R.string.action_reset),
            onConfirm = onReset,
            onDismiss = { confirmReset = false },
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

/** A scrollable page of headed text sections, used for the rules. */
@Composable
fun TextScreen(title: String, sections: List<Pair<String, String>>, onBack: () -> Unit) {
    SimpleScreen(title = title, onBack = onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            sections.forEach { (heading, body) ->
                Text(heading, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
fun AboutScreen(appName: String, versionName: String, onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val sourceUrl = stringResource(R.string.source_url)
    SimpleScreen(title = stringResource(R.string.action_about), onBack = onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(appName, style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.about_version, versionName), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.about_license), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.about_privacy), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = { runCatching { uriHandler.openUri(sourceUrl) } }) {
                Text(stringResource(R.string.about_source))
            }
        }
    }
}
