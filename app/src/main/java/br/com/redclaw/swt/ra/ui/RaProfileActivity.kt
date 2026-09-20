package br.com.redclaw.swt.ra.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import br.com.redclaw.swt.theme.AccentManager
import br.com.redclaw.swt.ui.theme.SwtTheme
import br.com.redclaw.swt.ui.theme.rememberSwtAccent

/**
 * RetroAchievements profile — metrics, recently played, progress.
 * Ported from XML to Compose — pure [ComponentActivity] with [setContent].
 */
class RaProfileActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(AccentManager.getThemeOverlay(this))
        super.onCreate(savedInstanceState)

        val sessionState = intent.getStringExtra("ra_session_state")

        setContent {
            val accent = rememberSwtAccent()
            SwtTheme(accent = accent) {
                RaProfileScreen(
                    sessionState = sessionState,
                    onBack = { finish() },
                )
            }
        }
    }
}
