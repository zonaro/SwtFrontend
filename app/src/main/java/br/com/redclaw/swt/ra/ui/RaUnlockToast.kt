package br.com.redclaw.swt.ra.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.redclaw.swt.R
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay

/**
 * Compose-based achievement unlock toast with AnimatedVisibility + AsyncImage badge.
 * Can be used in any Compose tree. Replaces the View-based RaOverlayView for new screens.
 */
@Composable
fun RaUnlockToast(
    visible: Boolean,
    achievementTitle: String,
    achievementDesc: String? = null,
    badgeUrl: String? = null,
    durationMs: Long = 4000L,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(visible, durationMs) {
        if (visible) {
            delay(durationMs)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xCC1E1E1E))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(badgeUrl)
                    .crossfade(true)
                    .build(),
                placeholder = painterResource(R.drawable.ic_trophy),
                error = painterResource(R.drawable.ic_trophy),
                contentDescription = achievementTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = achievementTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            if (!achievementDesc.isNullOrBlank()) {
                Text(
                    text = achievementDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xB3FFFFFF),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
