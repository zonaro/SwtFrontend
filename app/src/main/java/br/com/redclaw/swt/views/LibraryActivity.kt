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

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import br.com.redclaw.swt.theme.AccentManager
import br.com.redclaw.swt.ui.theme.SwtTheme
import br.com.redclaw.swt.ui.theme.rememberSwtAccent
import br.com.redclaw.swt.views.library.LibraryScreen

/**
 * Library home screen, matching the Nintendo Switch HOME menu aesthetic.
 * Delegates all UI to Compose via [LibraryScreen].
 *
 * Extends [ComponentActivity] and applies the accent theme overlay before
 * [super.onCreate] so that framework controls inherit the correct tint.
 */
class LibraryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(AccentManager.getThemeOverlay(this))
        super.onCreate(savedInstanceState)

        setContent {
            val accent = rememberSwtAccent()
            SwtTheme(accent = accent) {
                LibraryScreen(
                    onGameGrid = {
                        startActivity(Intent(this@LibraryActivity, GameGridActivity::class.java))
                    },
                    onApps = {
                        startActivity(Intent(this@LibraryActivity, AppsActivity::class.java))
                    },
                    onSettings = {
                        startActivity(Intent(this@LibraryActivity, SettingsActivity::class.java))
                    },
                )
            }
        }
    }
}
