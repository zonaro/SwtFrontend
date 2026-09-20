package br.com.redclaw.swt.ra.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import br.com.redclaw.swt.theme.AccentManager
import br.com.redclaw.swt.ui.theme.SwtTheme
import br.com.redclaw.swt.ui.theme.rememberSwtAccent

/**
 * Grid/list of achievements for the current game.
 * Ported from XML to Compose — pure [ComponentActivity] with [setContent].
 */
class AchievementsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(AccentManager.getThemeOverlay(this))
        super.onCreate(savedInstanceState)

        val gameTitle = intent.getStringExtra(EXTRA_GAME_TITLE)?.takeIf { it.isNotBlank() }

        setContent {
            val accent = rememberSwtAccent()
            SwtTheme(accent = accent) {
                AchievementsScreen(
                    gameTitle = gameTitle,
                    onBack = { finish() },
                )
            }
        }
    }

    companion object {
        const val EXTRA_GAME_TITLE = "extra_game_title"
    }
}
