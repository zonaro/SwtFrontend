package br.com.redclaw.swt.ra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.redclaw.swt.R
import br.com.redclaw.swt.ra.api.RaHttpClient
import br.com.redclaw.swt.ra.api.RaUserAgent
import br.com.redclaw.swt.ra.auth.RaCredentialStore
import br.com.redclaw.swt.ra.data.RaUserProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ── Screen ──────────────────────────────────────────────────────────

/**
 * RetroAchievements profile — metrics, recently played, progress.
 * Ported from the XML-based RaProfileActivity to Compose.
 */
@Composable
fun RaProfileScreen(
    sessionState: String? = null,
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val credentialStore = remember { RaCredentialStore(context) }
    val profileRepo = remember {
        RaUserProfileRepository(
            context = context,
            credentials = credentialStore,
            http = RaHttpClient(RaUserAgent.build(context)),
        )
    }

    val notLoggedIn = stringResource(R.string.ra_not_logged_in)
    val loginHint = stringResource(R.string.ra_login_in_settings)
    val loadingLabel = stringResource(R.string.ra_profile_loading)
    val loadedLabel = stringResource(R.string.ra_profile_loaded)
    val errorPrefix = stringResource(R.string.ra_profile_error)

    var titleText by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("") }
    var summaryText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val username = credentialStore.getUsername()?.trim().orEmpty()
        if (username.isBlank() || !credentialStore.hasApiKey()) {
            titleText = notLoggedIn
            statusText = loginHint
            return@LaunchedEffect
        }

        titleText = username
        statusText = loadingLabel

        val result = withContext(Dispatchers.IO) {
            profileRepo.getProfile(forceRefresh = false)
        }
        result.onSuccess { profile ->
            summaryText = buildString {
                appendLine("Points: ${profile.totalPoints ?: 0} (${profile.totalSoftcorePoints ?: 0} softcore)")
                appendLine("True points: ${profile.totalTruePoints ?: 0}")
                appendLine("Rank: ${profile.rank ?: "-"} / ${profile.totalRanked ?: "-"}")
                appendLine("Awards: ${profile.awards?.totalAwardsCount ?: 0} (mastery ${profile.awards?.masteryAwardsCount ?: 0})")
                appendLine("Completion: ${profile.completionProgress?.count ?: 0}/${profile.completionProgress?.total ?: 0}")
                profile.richPresence?.takeIf { it.isNotBlank() }?.let { appendLine("Presence: $it") }
                profile.lastGame?.let { lg ->
                    lg.title?.let { appendLine("Last: $it (${lg.console ?: ""})") }
                }
                if (profile.recentAchievements.isNotEmpty()) {
                    appendLine()
                    appendLine("Recent achievements: ${profile.recentAchievements.size}")
                }
            }
            statusText = loadedLabel
        }.onFailure { e ->
            statusText = errorPrefix.format(e.message ?: "unknown")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = titleText,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (summaryText.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = summaryText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
            )
        }

        if (!sessionState.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Session: $sessionState",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
