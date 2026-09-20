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
import android.util.Log
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * SteamGridDB API client — Bearer token auth with autocomplete and grid endpoints.
 *
 * Implements the SGDB public API v2:
 * - `/search/autocomplete/{term}` — fuzzy game name search
 * - `/grids/game/{id}?dimensions=600x900` — cover grids for a game
 *
 * Rate-limited to ~1 req/s (1000 ms between requests).
 * No Hilt/Dagger injection — standalone object.
 */
object SteamGridDbClient {
    private const val TAG = "SwtSgdbClient"
    private const val BASE_URL = "https://www.steamgriddb.com/api/v2"
    private const val RATE_LIMIT_DELAY_MS = 1000L // ~1 req/s

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun hasCredentials(context: Context): Boolean = SwtPrefs.hasSgdbCredentials(context)

    /**
     * Autocomplete search for games on SteamGridDB. Must be called from a coroutine context.
     *
     * @return List of [SgdbSearchResult] with game ID, name, and release year.
     */
    suspend fun searchAutocomplete(
        context: Context,
        term: String
    ): List<SgdbSearchResult> {
        if (!hasCredentials(context) || term.isBlank()) return emptyList()
        val apiKey = SwtPrefs.sgdbApiKey(context) ?: return emptyList()

        delay(RATE_LIMIT_DELAY_MS)
        val encodedTerm = URLEncoder.encode(term.trim(), "UTF-8")
        val response = authorizedGet(
            path = "/search/autocomplete/$encodedTerm",
            apiKey = apiKey
        ) ?: return emptyList()

        return runCatching {
            val root = JSONObject(response)
            val data = root.optJSONArray("data") ?: return@runCatching emptyList()

            List(data.length()) { data.optJSONObject(it) }.mapNotNull { item ->
                val id = item?.optLongOrNull("id") ?: return@mapNotNull null
                val name = item.optStringOrNull("name") ?: return@mapNotNull null
                SgdbSearchResult(
                    id = id,
                    name = name,
                    releaseYear = item.optLongOrNull("release_date")?.let { ts ->
                        java.util.Calendar.getInstance().apply { timeInMillis = ts * 1000L }
                            .get(java.util.Calendar.YEAR)
                    }
                )
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Fetches grid images (covers) for a game by its SGDB ID.
     * Returns URLs for portrait grids (600x900) suitable for cover art.
     *
     * Must be called from a coroutine context.
     */
    suspend fun getGridsForGame(
        context: Context,
        gameId: Long
    ): List<SgdbGridResult> {
        if (!hasCredentials(context)) return emptyList()
        val apiKey = SwtPrefs.sgdbApiKey(context) ?: return emptyList()

        delay(RATE_LIMIT_DELAY_MS)
        val response = authorizedGet(
            path = "/grids/game/$gameId?dimensions=600x900&styles=alternate",
            apiKey = apiKey
        ) ?: return emptyList()

        return runCatching {
            val root = JSONObject(response)
            val data = root.optJSONArray("data") ?: return@runCatching emptyList()

            List(data.length()) { data.optJSONObject(it) }.mapNotNull { item ->
                val url = item?.optStringOrNull("url") ?: return@mapNotNull null
                val width = item.optLongOrNull("width")?.toInt() ?: 600
                val height = item.optLongOrNull("height")?.toInt() ?: 900
                SgdbGridResult(
                    url = url,
                    width = width,
                    height = height
                )
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Convenience: search + get best cover URL for the first result.
     *
     * @return Pair of (game name, cover URL) or null.
     */
    suspend fun findCoverUrl(
        context: Context,
        query: String
    ): Pair<String, String>? {
        val result = searchAutocomplete(context, query).firstOrNull() ?: return null
        val grids = getGridsForGame(context, result.id)
        val bestCover = grids.firstOrNull()?.url ?: return result.name to (result.name)
        return result.name to bestCover
    }

    // ── Internals ───────────────────────────────────────────────────────────

    private fun authorizedGet(path: String, apiKey: String): String? {
        val url = "$BASE_URL$path"
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        return runCatching {
            val response = httpClient.newCall(request).execute()
            val json = response.body?.string() ?: return@runCatching null
            if (!response.isSuccessful) {
                Log.w(TAG, "SGDB request failed: ${response.code} — $json")
                return@runCatching null
            }
            json
        }.getOrNull()
    }
}

/** Autocomplete search result from SteamGridDB. */
data class SgdbSearchResult(
    val id: Long,
    val name: String,
    val releaseYear: Int?
)

/** A single grid image from SteamGridDB. */
data class SgdbGridResult(
    val url: String,
    val width: Int,
    val height: Int
)
