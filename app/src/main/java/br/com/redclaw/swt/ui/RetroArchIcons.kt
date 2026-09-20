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

package br.com.redclaw.swt.ui

import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import br.com.redclaw.swt.R
import br.com.redclaw.swt.theme.AccentManager

/**
 * Maps dock items and UI sections to RetroArch monochrome PNG icons,
 * tinted at runtime with the user-selected accent color via [ImageView.setColorFilter].
 *
 * All icons come from libretro/retroarch-assets xmb/monochrome/png/
 * (GPLv3-compatible) and live in res/drawable-nodpi/.
 */
object RetroArchIcons {

    @DrawableRes val dockGames: Int = R.drawable.ic_dock_games
    @DrawableRes val dockApps: Int = R.drawable.ic_dock_apps
    @DrawableRes val dockAchievements: Int = R.drawable.ic_dock_achievements
    @DrawableRes val dockProfile: Int = R.drawable.ic_dock_profile
    @DrawableRes val dockSettings: Int = R.drawable.ic_dock_settings
    @DrawableRes val dockAllGames: Int = R.drawable.ic_dock_all_games
    @DrawableRes val dockCollections: Int = R.drawable.ic_dock_collections
    @DrawableRes val dockQuickMenu: Int = R.drawable.ic_dock_quickmenu
    @DrawableRes val dockNetwork: Int = R.drawable.ic_dock_network
    @DrawableRes val dockExit: Int = R.drawable.ic_dock_exit
    @DrawableRes val dockHelp: Int = R.drawable.ic_dock_help
    @DrawableRes val dockInfo: Int = R.drawable.ic_dock_info

    /**
     * Applies the current accent tint to [imageView] by setting its drawable
     * from [resId] and coloring it with [AccentManager.getAccentColor].
     */
    fun tintToAccent(imageView: ImageView, @DrawableRes resId: Int) {
        val context = imageView.context
        val drawable = ContextCompat.getDrawable(context, resId)?.mutate() ?: return
        DrawableCompat.setTint(drawable, AccentManager.getAccentColor(context))
        imageView.setImageDrawable(drawable)
    }

    /**
     * Tints [imageView] with a fixed [colorRes] instead of the current accent.
     * Used for dock icons that keep their canonical color (e.g. store red, gallery teal).
     */
    fun tintFixed(imageView: ImageView, @DrawableRes iconRes: Int, colorRes: Int) {
        val context = imageView.context
        val drawable = ContextCompat.getDrawable(context, iconRes)?.mutate() ?: return
        DrawableCompat.setTint(drawable, ContextCompat.getColor(context, colorRes))
        imageView.setImageDrawable(drawable)
    }
}
