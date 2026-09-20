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

package br.com.redclaw.swt.utils

import android.content.Context

/**
 * Centralized SharedPreferences access for Swt Frontend.
 *
 * Manages Switch UI preferences (theme, accent) and will grow to cover
 * other app-wide settings. Pattern ported from HylianBox CorePrefs.
 */
object SwtPrefs {
    private const val PREFS_NAME = "swt_prefs"

    // ---- Nintendo Switch UI preferences ----
    private const val PREF_SWITCH_THEME = "pref_switch_theme"
    private const val PREF_SWITCH_ACCENT = "pref_switch_accent"

    const val THEME_DARK = "dark"
    const val THEME_LIGHT = "light"
    const val ACCENT_DEFAULT = "cyan"

    // ---- Theme (DayNight) ----

    /** Returns [THEME_DARK] or [THEME_LIGHT]. Default is dark. */
    fun getSwitchTheme(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_SWITCH_THEME, THEME_DARK)
            ?: THEME_DARK

    fun setSwitchTheme(context: Context, theme: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_SWITCH_THEME, theme)
            .apply()
    }

    // ---- Accent color ----

    /** Returns the accent key (e.g., "cyan", "green_light"). */
    fun getSwitchAccent(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_SWITCH_ACCENT, ACCENT_DEFAULT)
            ?: ACCENT_DEFAULT

    fun setSwitchAccent(context: Context, accentKey: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_SWITCH_ACCENT, accentKey)
            .apply()
    }

    private const val PREF_CORES_INSTALLED_VERSION = "pref_cores_installed_version"
    private const val PREF_CORES_UPDATE_PENDING = "pref_cores_update_pending"

    fun getCoresInstalledVersion(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_CORES_INSTALLED_VERSION, null)

    fun setCoresInstalledVersion(context: Context, version: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_CORES_INSTALLED_VERSION, version)
            .apply()
    }

    fun isCoresUpdatePending(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_CORES_UPDATE_PENDING, false)

    fun setCoresUpdatePending(context: Context, pending: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_CORES_UPDATE_PENDING, pending)
            .apply()
    }
}
