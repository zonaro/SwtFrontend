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
import org.json.JSONObject

/**
 * TheGamesDB API client — lightweight REST client for game metadata.
 *
 * Ported from Kwiq's KwiqTheGamesDbClient, adapted for SwtFrontend:
 * - Uses OkHttp instead of HttpURLConnection.
 * - Reads API key from [SwtPrefs].
 * - Rate-limited to ~4 req/s via throttling.
 * - No Hilt/Dagger injection — standalone object.
 */
object KwiqTheGamesDbClient {
    private const val TAG = "SwtTheGamesDb"
    private const val BASE_URL = "https://api.thegamesdb.net"
    private const val RATE_LIMIT_DELAY_MS = 250L

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun hasCredentials(context: Context): Boolean = SwtPrefs.hasTheGamesDbCredentials(context)

    /**
     * Searches games on TheGamesDB. Must be called from a coroutine context.
     */
    suspend fun searchGames(
        context: Context,
        query: String
    ): List<GameDatabaseSearchResult> {
        if (!hasCredentials(context) || query.isBlank()) return emptyList()
        val apiKey = SwtPrefs.theGamesDbApiKey(context) ?: return emptyList()

        delay(RATE_LIMIT_DELAY_MS)
        val response = httpGet(
            path = "/v1.1/Games/ByGameName",
            apiKey = apiKey,
            params = mapOf("name" to query, "include" to "boxart,platform")
        ) ?: return emptyList()

        return runCatching {
            val root = JSONObject(response)
            val baseUrl = root.optJSONObject("include")
                ?.optJSONObject("boxart")
                ?.optJSONObject("base_url")
                ?.optStringOrNull("original")
                .orEmpty()
            val games = root.optJSONObject("data")?.optJSONArray("games")
                ?: return@runCatching emptyList()

            List(games.length()) { games.optJSONObject(it) }.mapNotNull { game ->
                val id = game?.optLongOrNull("id") ?: return@mapNotNull null
                val name = game.optStringOrNull("game_title") ?: return@mapNotNull null
                val boxart = game.optJSONArray("boxart")?.let { art ->
                    List(art.length()) { art.optJSONObject(it) }
                        .firstOrNull { it?.optString("type") == "boxart" }
                        ?.optStringOrNull("filename")
                }
                GameDatabaseSearchResult(
                    id = id,
                    name = name,
                    coverUrl = boxart?.let { "$baseUrl$it" },
                    releaseYear = game.optStringOrNull("release_date")
                        ?.take(4)
                        ?.toIntOrNull(),
                    platforms = emptyList()
                )
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Fetches full game details from TheGamesDB. Must be called from a coroutine context.
     */
    suspend fun getGameDetails(context: Context, id: Long): GameMetadata? {
        if (!hasCredentials(context)) return null
        val apiKey = SwtPrefs.theGamesDbApiKey(context) ?: return null

        delay(RATE_LIMIT_DELAY_MS)
        val response = httpGet(
            path = "/v1/Games/ByGameID",
            apiKey = apiKey,
            params = mapOf(
                "id" to id.toString(),
                "include" to "boxart,platform,players,publishers,developers,genres"
            )
        ) ?: return null

        return runCatching {
            val root = JSONObject(response)
            val baseUrl = root.optJSONObject("include")
                ?.optJSONObject("boxart")
                ?.optJSONObject("base_url")
                ?.optStringOrNull("original")
                .orEmpty()
            val game = root.optJSONObject("data")?.optJSONArray("games")?.optJSONObject(0)
                ?: return@runCatching null
            val cover = game.optJSONArray("boxart")?.let { art ->
                List(art.length()) { art.optJSONObject(it) }
                    .firstOrNull { it?.optString("type") == "boxart" }
                    ?.optStringOrNull("filename")
                    ?.let { "$baseUrl$it" }
            }
            GameMetadata(
                theGamesDbId = id,
                name = game.optStringOrNull("game_title"),
                summary = game.optStringOrNull("overview"),
                releaseDateMillis = game.optStringOrNull("release_date")?.let {
                    runCatching { java.sql.Date.valueOf(it).time }.getOrNull()
                },
                genres = game.optJSONArray("genres")
                    ?.let { a -> List(a.length()) { a.optString(it) }.filter { it.isNotBlank() } }
                    .orEmpty(),
                platforms = game.optJSONArray("platform")
                    ?.let { a -> List(a.length()) { a.optString(it) }.filter { it.isNotBlank() } }
                    .orEmpty(),
                companies = game.optJSONArray("developers")
                    ?.let { a -> List(a.length()) { a.optString(it) }.filter { it.isNotBlank() } }
                    .orEmpty() +
                    game.optJSONArray("publishers")
                        ?.let { a -> List(a.length()) { a.optString(it) }.filter { it.isNotBlank() } }
                        .orEmpty(),
                coverUrl = cover
            )
        }.getOrNull()
    }

    // ── Internals ───────────────────────────────────────────────────────────

    private fun httpGet(path: String, apiKey: String, params: Map<String, String>): String? {
        val allParams = params + ("apikey" to apiKey)
        val query = allParams.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }
        val url = "$BASE_URL$path?$query"

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return runCatching {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "TheGamesDB request failed: ${response.code}")
                return@runCatching null
            }
            response.body?.string()
        }.getOrNull()
    }
}
