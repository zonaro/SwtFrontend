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
import androidx.appcompat.app.AppCompatActivity
import br.com.redclaw.swt.theme.AccentManager

/**
 * Base activity applying the global accent theme before view inflation.
 *
 * Every Activity in the Swt Frontend extends this class so that the
 * user-selected accent overlay is applied before AppCompat/Material
 * inflates or tints any view. This is the global fallback for
 * platform/Material controls; custom Switch views still use
 * [AccentManager] directly for their runtime-created drawables.
 *
 * Simplified from HylianBox ScaledAppCompatActivity: no UiScaleManager
 * or SwitchBackButton (those are HylianBox-specific).
 */
open class ScaledAppCompatActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply the persisted accent before AppCompat inflates or tints any view.
        setTheme(AccentManager.getThemeOverlay(this))
        super.onCreate(savedInstanceState)
    }
}
