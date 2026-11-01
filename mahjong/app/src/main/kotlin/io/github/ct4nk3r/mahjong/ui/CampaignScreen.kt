package io.github.ct4nk3r.mahjong.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ct4nk3r.games.core.ui.ConfirmDialog
import io.github.ct4nk3r.games.core.ui.SimpleScreen
import io.github.ct4nk3r.mahjong.CampaignProgress
import io.github.ct4nk3r.mahjong.R
import io.github.ct4nk3r.mahjong.engine.Campaign
import io.github.ct4nk3r.games.core.ui.R as CoreR

private val STAR_GOLD = Color(0xFFFFB300)

/** Stars as text, e.g. "★★☆". */
fun starsText(stars: Int, max: Int = 3): String = "★".repeat(stars) + "☆".repeat(max - stars)

/** The 25 campaign levels as big, easy-to-hit tiles, grouped by difficulty. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CampaignScreen(
    progress: CampaignProgress,
    gameInProgress: Boolean,
    onPlay: (Int) -> Unit,
    onBack: () -> Unit,
) {
    var confirmLevel by remember { mutableStateOf<Int?>(null) }
    val groups = listOf(
        stringResource(R.string.campaign_easy) to (1..8),
        stringResource(R.string.campaign_medium) to (9..15),
        stringResource(R.string.campaign_hard) to (16..Campaign.LEVELS.size),
    )
    SimpleScreen(title = stringResource(R.string.campaign_title), onBack = onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                stringResource(R.string.campaign_total_stars, progress.totalStars, Campaign.LEVELS.size * 3),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(stringResource(R.string.campaign_stars_help), style = MaterialTheme.typography.bodyMedium)
            groups.forEach { (title, range) ->
                Spacer(Modifier.height(20.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    range.forEach { n ->
                        LevelTile(
                            number = n,
                            stars = progress.stars[n] ?: 0,
                            locked = n > progress.unlocked,
                            onClick = { if (gameInProgress) confirmLevel = n else onPlay(n) },
                        )
                    }
                }
            }
        }
    }
    confirmLevel?.let { n ->
        ConfirmDialog(
            title = stringResource(CoreR.string.confirm_new_game_title),
            message = stringResource(CoreR.string.confirm_new_game_message),
            confirmLabel = stringResource(CoreR.string.confirm_continue),
            onConfirm = { onPlay(n) },
            onDismiss = { confirmLevel = null },
        )
    }
}

@Composable
private fun LevelTile(number: Int, stars: Int, locked: Boolean, onClick: () -> Unit) {
    val description = if (locked) stringResource(R.string.level_locked, number)
    else pluralStringResource(R.plurals.level_stars, stars, number, stars)
    Card(
        onClick = onClick,
        enabled = !locked,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (stars > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier
            .size(76.dp)
            .semantics { contentDescription = description },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (locked) {
                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(28.dp))
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(number.toString(), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(starsText(stars), color = if (stars > 0) STAR_GOLD else MaterialTheme.colorScheme.outline, fontSize = 15.sp)
                }
            }
        }
    }
}
