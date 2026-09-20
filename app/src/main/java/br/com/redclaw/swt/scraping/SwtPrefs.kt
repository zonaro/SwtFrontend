/*
 * Copyright (C) 2025 RedClaw Studio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package br.com.redclaw.swt.scraping

import android.content.Context
import android.content.SharedPreferences

/**
 * Centralized SharedPreferences access for the Swt scraping module.
 *
 * API keys are stored via [SharedPreferences] — never hardcoded.
 * Uses the same pattern as KwiqPrefs but scoped to SwtFrontend.
 */
object SwtPrefs {
    private const val PREFS_NAME = "swt_scraping"

    // IGDB keys
    const val KEY_IGDB_ENABLED = "igdb_covers_enabled"
    const val KEY_IGDB_CLIENT_ID = "igdb_client_id"
    const val KEY_IGDB_CLIENT_SECRET = "igdb_client_secret"

    // TheGamesDB keys
    const val KEY_TGDB_ENABLED = "thegamesdb_enabled"
    const val KEY_TGDB_API_KEY = "thegamesdb_api_key"

    // SteamGridDB keys
    const val KEY_SGDB_ENABLED = "sgdb_enabled"
    const val KEY_SGDB_API_KEY = "sgdb_api_key"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun igdbClientId(context: Context): String? =
        prefs(context).getString(KEY_IGDB_CLIENT_ID, null)?.takeIf { it.isNotBlank() }

    fun igdbClientSecret(context: Context): String? =
        prefs(context).getString(KEY_IGDB_CLIENT_SECRET, null)?.takeIf { it.isNotBlank() }

    fun igdbEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_IGDB_ENABLED, false)

    fun theGamesDbApiKey(context: Context): String? =
        prefs(context).getString(KEY_TGDB_API_KEY, null)?.takeIf { it.isNotBlank() }

    fun theGamesDbEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TGDB_ENABLED, false)

    fun sgdbApiKey(context: Context): String? =
        prefs(context).getString(KEY_SGDB_API_KEY, null)?.takeIf { it.isNotBlank() }

    fun sgdbEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SGDB_ENABLED, false)

    fun hasIgdbCredentials(context: Context): Boolean =
        igdbEnabled(context) &&
                !igdbClientId(context).isNullOrBlank() &&
                !igdbClientSecret(context).isNullOrBlank()

    fun hasTheGamesDbCredentials(context: Context): Boolean =
        theGamesDbEnabled(context) && !theGamesDbApiKey(context).isNullOrBlank()

    fun hasSgdbCredentials(context: Context): Boolean =
        sgdbEnabled(context) && !sgdbApiKey(context).isNullOrBlank()

    fun saveIgdbCredentials(context: Context, clientId: String, clientSecret: String, enabled: Boolean = true) {
        prefs(context).edit()
            .putString(KEY_IGDB_CLIENT_ID, clientId)
            .putString(KEY_IGDB_CLIENT_SECRET, clientSecret)
            .putBoolean(KEY_IGDB_ENABLED, enabled)
            .apply()
    }

    fun saveTheGamesDbKey(context: Context, apiKey: String, enabled: Boolean = true) {
        prefs(context).edit()
            .putString(KEY_TGDB_API_KEY, apiKey)
            .putBoolean(KEY_TGDB_ENABLED, enabled)
            .apply()
    }

    fun saveSgdbKey(context: Context, apiKey: String, enabled: Boolean = true) {
        prefs(context).edit()
            .putString(KEY_SGDB_API_KEY, apiKey)
            .putBoolean(KEY_SGDB_ENABLED, enabled)
            .apply()
    }
}
