/*
 * SwtFrontend - native Android launcher frontend for RetroArch/libretro cores.
 * Copyright (C) 2026 RedClaw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package br.com.redclaw.swt.views

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import br.com.redclaw.swt.theme.AccentManager
import br.com.redclaw.swt.ui.theme.SwtTheme
import br.com.redclaw.swt.ui.theme.rememberSwtAccent
import br.com.redclaw.swt.views.settings.SettingsScreen

/**
 * Settings screen for SwtFrontend: ROM folders via SAF, accent/theme selection,
 * provider API keys (IGDB/TGDB/SGDB), RetroAchievements login, dashboard toggle.
 *
 * Pure Compose rewrite — delegates all UI to [SettingsScreen].
 * Keeps the same AccentManager theme overlay as the original
 * [ScaledAppCompatActivity]-based implementation.
 */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply the persisted accent overlay before the Compose tree is created,
        // matching ScaledAppCompatActivity behaviour.
        setTheme(AccentManager.getThemeOverlay(this))
        super.onCreate(savedInstanceState)

        setContent {
            val accent = rememberSwtAccent()
            SwtTheme(accent = accent) {
                SettingsScreen(onBack = { finish() })
            }
        }
    }
}
