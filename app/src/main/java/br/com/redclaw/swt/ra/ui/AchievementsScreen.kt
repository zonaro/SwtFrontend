package br.com.redclaw.swt.ra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.redclaw.swt.R
import br.com.redclaw.swt.ra.jni.RcheevosJni
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * UI data for a single achievement. Immutable.
 */
data class UiAchievement(
    val id: Long,
    val title: String,
    val description: String,
    val points: Int,
    val unlocked: Boolean,
    val hardcoreUnlocked: Boolean,
)

// ── Screen ──────────────────────────────────────────────────────────

/**
 * Grid/list of achievements for the current game.
 * Ported from the XML-based AchievementsActivity to Compose.
 */
@Composable
fun AchievementsScreen(
    gameTitle: String?,
    onBack: () -> Unit = {},
) {
    var statusText by remember { mutableStateOf("") }
    var achievements by remember { mutableStateOf(emptyList<UiAchievement>()) }
    var isEmpty by remember { mutableStateOf(false) }

    val loadingLabel = stringResource(R.string.ra_profile_loading)
    val emptyLabel = stringResource(R.string.ra_achievements_empty)

    LaunchedEffect(Unit) {
        statusText = loadingLabel
        val list = withContext(Dispatchers.IO) { readAchievementsFromNative() }
        if (list == null || list.isEmpty()) {
            statusText = emptyLabel
            isEmpty = true
            achievements = emptyList()
        } else {
            val unlocked = list.count { it.unlocked }
            statusText = "$unlocked / ${list.size} \u2014 ${list.sumOf { it.points }} pts"
            achievements = list
            isEmpty = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
    ) {
        // ── Header ───────────────────────────────────────────────
        Text(
            text = gameTitle
                ?: stringResource(R.string.ra_achievements_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (isEmpty) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = emptyLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(achievements, key = { it.id }) { achievement ->
                    AchievementRow(achievement)
                }
            }
        }
    }
}

// ── Item ────────────────────────────────────────────────────────────

@Composable
private fun AchievementRow(a: UiAchievement) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.shapes.small,
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = a.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (a.description.isNotBlank()) {
                Text(
                    text = a.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${a.points} pts",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        a.hardcoreUnlocked -> context.getString(R.string.ra_achievements_hardcore)
                        a.unlocked -> context.getString(R.string.ra_achievements_unlocked)
                        else -> context.getString(R.string.ra_achievements_locked)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Native bridge ───────────────────────────────────────────────────

internal fun readAchievementsFromNative(): List<UiAchievement>? = runCatching {
    val json = RcheevosJni.nativeGetAchievementListJson()
    if (json.isBlank() || json == "[]") return null
    val arr = JSONArray(json)
    if (arr.length() == 0) return emptyList()
    val out = ArrayList<UiAchievement>(arr.length())
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val title = o.optString("title").takeIf { it.isNotBlank() }
            ?: o.optString("Title") ?: "\u2014"
        val desc = o.optString("description").takeIf { it.isNotBlank() }
            ?: o.optString("Description") ?: ""
        out.add(
            UiAchievement(
                id = o.optLong("id", o.optLong("ID", -1)),
                title = title,
                description = desc,
                points = o.optInt("points", o.optInt("Points", 0)),
                unlocked = o.optInt("unlocked", 0) != 0,
                hardcoreUnlocked = o.optInt("hardcoreUnlocked", 0) != 0,
            ),
        )
    }
    out
}.getOrNull()
