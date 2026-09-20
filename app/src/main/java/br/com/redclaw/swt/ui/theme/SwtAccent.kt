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

import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import br.com.redclaw.swt.theme.AccentManager

/**
 * CompositionLocal providing the current accent [Color] for Compose UI.
 *
 * This is a [staticCompositionLocalOf] because the accent changes infrequently (only when the user
 * picks a new one in Settings) and every recomposition in the tree should see the updated value.
 */
val LocalSwtAccent = staticCompositionLocalOf {
    Color.Unspecified
}

/**
 * Reads the current accent color from [AccentManager] and returns it as a Compose [Color].
 *
 * Use inside a Composable to obtain the accent without providing a default explicitly.
 */
@Composable
fun rememberSwtAccent(): Color {
    val context = LocalContext.current
    val argb = AccentManager.getAccentColor(context)
    return Color(AndroidColor.red(argb), AndroidColor.green(argb), AndroidColor.blue(argb), AndroidColor.alpha(argb))
}
