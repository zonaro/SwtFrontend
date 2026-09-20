package br.com.redclaw.swt.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Composable content for the secondary display Presentation.
 * Shows DS bottom-screen info on an external display.
 */
@Composable
fun SecondaryScreen(
    coreDisplayName: String? = null,
    screenWidthPx: Int = 0,
    screenHeightPx: Int = 0,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Swt Frontend - DS Bottom Screen",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White.copy(alpha = 0.7f),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(horizontal = 48.dp, vertical = 32.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            text = buildString {
                if (screenWidthPx > 0 && screenHeightPx > 0) {
                    appendLine("Display: ${screenWidthPx}x${screenHeightPx}")
                }
                appendLine("Status: Waiting for game output...")
                appendLine()
                appendLine("DS dual-screen layout is configured")
                appendLine("via core options at game load time.")
                appendLine()
                appendLine("MelonDS: melonds_screen_layout1")
                appendLine("DeSmuME: desmume_screens_layout")
            },
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.45f),
            textAlign = TextAlign.Center,
        )
    }
}
