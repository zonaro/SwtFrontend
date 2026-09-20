package br.com.redclaw.swt.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Compose overlay for the in-game menu.
 * Replaces the XML-based menu_overlay LinearLayout.
 * Renders as a semi-transparent fullscreen overlay with menu buttons.
 */
@Composable
fun GameMenuOverlay(
    visible: Boolean,
    showSecondaryButton: Boolean,
    onResume: () -> Unit,
    onSaveState: () -> Unit,
    onLoadState: () -> Unit,
    onReset: () -> Unit,
    onRestart: () -> Unit,
    onSecondaryDisplay: () -> Unit,
    onQuit: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC000000))
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Menu",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(12.dp))

                MenuButton("Resume", onClick = onResume)
                MenuButton("Save State", onClick = onSaveState)
                MenuButton("Load State", onClick = onLoadState)
                MenuButton("Reset", onClick = onReset)
                MenuButton("Restart Game", onClick = onRestart)

                if (showSecondaryButton) {
                    MenuButton("DS Bottom Screen", onClick = onSecondaryDisplay)
                }

                MenuButton(
                    label = "Quit",
                    onClick = onQuit,
                    color = Color(0xFFFF4444),
                )
            }
        }
    }
}

@Composable
private fun MenuButton(
    label: String,
    onClick: () -> Unit,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .width(240.dp)
            .height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color.White,
        ),
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
        )
    }
}
