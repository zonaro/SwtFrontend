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

/**
 * Displays installed Android apps grouped by category, Switch-style.
 *
 * Pure Compose rewrite — delegates all UI to [AppsScreen].
 * Keeps the same AccentManager theme overlay as the original
 * [ScaledAppCompatActivity]-based implementation.
 */
class AppsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply the persisted accent overlay before the Compose tree is created,
        // matching ScaledAppCompatActivity behaviour.
        setTheme(AccentManager.getThemeOverlay(this))
        super.onCreate(savedInstanceState)

        setContent {
            val accent = rememberSwtAccent()
            SwtTheme(accent = accent) {
                AppsScreen(onBack = { finish() })
            }
        }
    }
}
