package io.github.ct4nk3r.games.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ct4nk3r.games.core.common.formatDuration
import java.text.DateFormat
import java.util.Date

/** What the Saved games screen shows about one occupied slot. */
data class SlotSummary(
    val savedAtMillis: Long,
    /** Game-specific heading, e.g. "Klondike" or a Mahjong layout name. */
    val title: String,
    val elapsedSeconds: Long,
    val moves: Int,
    /** 0..1, how far the game has got. */
    val progress: Float,
)

private sealed interface SlotAction {
    val index: Int
    data class Overwrite(override val index: Int) : SlotAction
    data class Load(override val index: Int) : SlotAction
    data class Delete(override val index: Int) : SlotAction
}

/** Manual save slots. Large rows and buttons so they are easy to read and hit. */
@Composable
fun SaveSlotsScreen(
    slots: List<SlotSummary?>,
    canSave: Boolean,
    gameInProgress: Boolean,
    onSave: (Int) -> Unit,
    onLoad: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onBack: () -> Unit,
) {
    var confirm by remember { mutableStateOf<SlotAction?>(null) }
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale) }

    SimpleScreen(title = stringResource(R.string.saves_title), onBack = onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            slots.forEachIndexed { index, slot ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.saves_slot, index + 1),
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.weight(1f),
                            )
                            if (slot != null) {
                                IconButton(onClick = { confirm = SlotAction.Delete(index) }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete))
                                }
                            }
                        }
                        if (slot == null) {
                            Text(stringResource(R.string.saves_empty), style = MaterialTheme.typography.bodyLarge)
                        } else {
                            Text(slot.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOf(
                                    stringResource(R.string.saves_progress, (slot.progress * 100).toInt()),
                                    "${stringResource(R.string.label_time)} ${formatDuration(slot.elapsedSeconds)}",
                                    "${stringResource(R.string.label_moves)} ${slot.moves}",
                                ).joinToString("  ·  "),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                stringResource(R.string.saves_saved_at, dateFormat.format(Date(slot.savedAtMillis))),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.size(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (slot != null) {
                                Button(
                                    onClick = { if (gameInProgress) confirm = SlotAction.Load(index) else onLoad(index) },
                                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                                ) { Text(stringResource(R.string.action_load), style = MaterialTheme.typography.titleMedium) }
                            }
                            OutlinedButton(
                                onClick = { if (slot != null) confirm = SlotAction.Overwrite(index) else onSave(index) },
                                enabled = canSave,
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                            ) { Text(stringResource(R.string.action_save_here), style = MaterialTheme.typography.titleMedium) }
                        }
                    }
                }
            }
        }
    }

    when (val action = confirm) {
        is SlotAction.Overwrite -> ConfirmDialog(
            title = stringResource(R.string.saves_overwrite_title),
            message = stringResource(R.string.saves_overwrite_message),
            confirmLabel = stringResource(R.string.action_save_here),
            onConfirm = { onSave(action.index) },
            onDismiss = { confirm = null },
        )
        is SlotAction.Load -> ConfirmDialog(
            title = stringResource(R.string.saves_load_title),
            message = stringResource(R.string.saves_load_message),
            confirmLabel = stringResource(R.string.action_load),
            onConfirm = { onLoad(action.index) },
            onDismiss = { confirm = null },
        )
        is SlotAction.Delete -> ConfirmDialog(
            title = stringResource(R.string.saves_delete_title),
            message = stringResource(R.string.saves_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { onDelete(action.index) },
            onDismiss = { confirm = null },
        )
        null -> Unit
    }
}
