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

package br.com.redclaw.swt.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Material 3 color schemes for the Switch UI.
 *
 * Dark scheme: charcoal backgrounds (#1A1D21 / #2D2D2D) matching the Switch OS palette.
 * Light scheme: light gray backgrounds (#F0F0F0 / #FFFFFF) matching the Switch OS light mode.
 *
 * The [Color.primary] slot is overridden at runtime by the user-selected accent color,
 * so these defaults (cyan) are only visible before the accent is resolved.
 */
private val SwtDarkColorScheme = darkColorScheme(
    primary = Color(0xFF00BCD4),            // default cyan, replaced by accent at runtime
    onPrimary = Color.White,
    primaryContainer = Color(0xFF006064),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF00BCD4),
    onSecondary = Color.White,
    background = Color(0xFF1A1D21),         // switch_bg_dark
    onBackground = Color.White,
    surface = Color(0xFF2D2D2D),            // switch_panel_dark (card surface)
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1E1E1E),     // switch_panel_dark
    onSurfaceVariant = Color(0xFF9E9E9E),   // switch_text_secondary_dark
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val SwtLightColorScheme = lightColorScheme(
    primary = Color(0xFF00BCD4),            // default cyan, replaced by accent at runtime
    onPrimary = Color.White,
    primaryContainer = Color(0xFF00BCD4),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF00BCD4),
    onSecondary = Color.White,
    background = Color(0xFFF0F0F0),         // switch_bg_light
    onBackground = Color(0xFF333333),       // switch_text_primary_light
    surface = Color(0xFFFFFFFF),            // switch_panel_light
    onSurface = Color(0xFF333333),
    surfaceVariant = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF666666),   // switch_text_secondary_light
    error = Color(0xFFB3261E),
    onError = Color.White,
)

/**
 * The main Compose theme for the Switch UI.
 *
 * Wraps [MaterialTheme] with the accent-derived color scheme and propagates the accent color
 * via [LocalSwtAccent]. The accent [Color] should be resolved from [AccentManager] in the
 * calling Activity/Composable and passed in here.
 *
 * @param accent   The user-selected accent color (from [AccentManager.getAccentColor]).
 * @param darkTheme Whether to use the dark color scheme. Defaults to system setting.
 * @param content  The Composable content tree.
 */
@Composable
fun SwtTheme(
    accent: Color,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) {
        SwtDarkColorScheme.copy(primary = accent, secondary = accent)
    } else {
        SwtLightColorScheme.copy(primary = accent, secondary = accent)
    }

    // Update the system status bar color to match the current background.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalSwtAccent provides accent) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
