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
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray

/**
 * IGDB API client — OAuth2 Twitch client_credentials flow.
 *
 * Ported from Kwiq's KwiqIgdbClient, adapted for SwtFrontend:
 * - Uses OkHttp instead of HttpURLConnection.
 * - Reads credentials from [SwtPrefs] instead of Kwiq's LauncherSettings.
 * - Rate-limited to ~4 requests/second via throttling.
 * - No Hilt/Dagger injection — standalone object.
 */
object KwiqIgdbClient {
    private const val TAG = "SwtIgdbClient"
    private const val RATE_LIMIT_DELAY_MS = 250L // ~4 req/s

    private var accessToken: String? = null
    private var tokenExpiresAtMillis: Long = 0L

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun hasCredentials(context: Context): Boolean = SwtPrefs.hasIgdbCredentials(context)

    /**
     * Searches games on IGDB. Must be called from a coroutine-compatible context.
     */
    suspend fun searchGames(
        context: Context,
        query: String,
        platform: String? = null
    ): List<IgdbSearchResult> {
        if (!hasCredentials(context) || query.isBlank()) return emptyList()

        val clientId = SwtPrefs.igdbClientId(context) ?: return emptyList()
        val clientSecret = SwtPrefs.igdbClientSecret(context) ?: return emptyList()
        val token = obtainAccessToken(clientId, clientSecret) ?: return emptyList()

        delay(RATE_LIMIT_DELAY_MS)
        val response = postIgdb(clientId, token, buildSearchQuery(query, platform, 12))
            ?: return emptyList()

        return runCatching {
            val arr = JSONArray(response)
            List(arr.length()) { arr.getJSONObject(it) }.mapNotNull { obj ->
                val id = obj.optLongOrNull("id") ?: return@mapNotNull null
                val name = obj.optStringOrNull("name") ?: return@mapNotNull null
                IgdbSearchResult(
                    id = id,
                    name = name,
                    coverUrl = obj.optJSONObject("cover")
                        ?.optStringOrNull("image_id")
                        ?.igdbImageUrl("t_cover_big_2x"),
                    releaseYear = obj.optLongOrNull("first_release_date")?.let {
                        Calendar.getInstance().apply { timeInMillis = it * 1000L }
                            .get(Calendar.YEAR)
                    },
                    platforms = obj.optNestedNames("platforms")
                )
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Fetches full game details from IGDB. Must be called from a coroutine context.
     */
    suspend fun getGameDetails(context: Context, gameId: Long): GameMetadata? {
        if (!hasCredentials(context)) return null

        val clientId = SwtPrefs.igdbClientId(context) ?: return null
        val clientSecret = SwtPrefs.igdbClientSecret(context) ?: return null
        val token = obtainAccessToken(clientId, clientSecret) ?: return null

        delay(RATE_LIMIT_DELAY_MS)
        val response = postIgdb(clientId, token, igdbDetailsQuery(gameId)) ?: return null

        return runCatching {
            val obj = JSONArray(response).optJSONObject(0) ?: return@runCatching null
            val screenshots = obj.optImageUrls("screenshots", "t_screenshot_big_2x") +
                    obj.optImageUrls("artworks", "t_screenshot_big_2x")
            GameMetadata(
                igdbId = gameId,
                name = obj.optStringOrNull("name"),
                summary = obj.optStringOrNull("summary"),
                storyline = obj.optStringOrNull("storyline"),
                releaseDateMillis = obj.optLongOrNull("first_release_date")?.let { it * 1000L },
                genres = obj.optNestedNames("genres"),
                platforms = obj.optNestedNames("platforms"),
                rating = obj.optDoubleOrNull("rating"),
                aggregatedRating = obj.optDoubleOrNull("aggregated_rating"),
                screenshots = screenshots.distinct().take(8),
                coverUrl = obj.optJSONObject("cover")
                    ?.optStringOrNull("image_id")
                    ?.igdbImageUrl("t_cover_big_2x"),
                websites = obj.optNestedStrings("websites", "url"),
                companies = obj.optJSONArray("involved_companies")
                    ?.let { arr ->
                        List(arr.length()) { arr.optJSONObject(it) }
                            .mapNotNull {
                                it?.optJSONObject("company")?.optStringOrNull("name")
                            }
                    }.orEmpty(),
                gameModes = obj.optNestedNames("game_modes"),
                playerPerspectives = obj.optNestedNames("player_perspectives"),
                themes = obj.optNestedNames("themes")
            )
        }.getOrNull()
    }

    /**
     * Convenience: search + get details for the first result.
     */
    suspend fun findCoverMetadata(
        context: Context,
        title: String,
        platform: String?
    ): GameMetadata? {
        val result = searchGames(context, title, platform).firstOrNull() ?: return null
        return getGameDetails(context, result.id)
            ?: GameMetadata(
                igdbId = result.id,
                name = result.name,
                coverUrl = result.coverUrl,
                platforms = result.platforms
            )
    }

    // ── Internals ───────────────────────────────────────────────────────────

    private fun obtainAccessToken(clientId: String, clientSecret: String): String? {
        val now = System.currentTimeMillis()
        accessToken?.takeIf { now < tokenExpiresAtMillis }?.let { return it }

        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("grant_type", "client_credentials")
            .build()

        val request = Request.Builder()
            .url("https://id.twitch.tv/oauth2/token")
            .post(body)
            .build()

        return runCatching {
            val response = httpClient.newCall(request).execute()
            val json = response.body?.string() ?: return@runCatching null
            if (!response.isSuccessful) {
                Log.w(TAG, "Twitch OAuth failed: ${response.code}")
                return@runCatching null
            }
            val obj = org.json.JSONObject(json)
            val token = obj.optStringOrNull("access_token") ?: return@runCatching null
            val expiresIn = obj.optLongOrNull("expires_in") ?: 3600L
            accessToken = token
            tokenExpiresAtMillis = now + (expiresIn - 60L).coerceAtLeast(60L) * 1000L
            token
        }.getOrNull()
    }

    private fun buildSearchQuery(query: String, platform: String?, limit: Int): String {
        val platformField = if (!platform.isNullOrBlank()) ",platforms.name" else ""
        return """
            search "${query.escapeIgdb()}";
            fields name,cover.image_id,first_release_date$platformField;
            limit $limit;
        """.trimIndent()
    }

    private fun igdbDetailsQuery(gameId: Long): String =
        """
        where id = $gameId;
        fields name,cover.image_id,first_release_date,summary,storyline,
               rating,aggregated_rating,
               genres.name,platforms.name,
               screenshots.image_id,artworks.image_id,
               websites.url,involved_companies.company.name,
               game_modes.name,player_perspectives.name,themes.name;
        limit 1;
    """.trimIndent()

    private fun postIgdb(clientId: String, token: String, body: String): String? {
        val mediaType = "text/plain".toMediaType()
        val requestBody = body.toRequestBody(mediaType)
        val request = Request.Builder()
            .url("https://api.igdb.com/v4/games")
            .post(requestBody)
            .addHeader("Client-ID", clientId)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "text/plain")
            .build()

        return runCatching {
            val response = httpClient.newCall(request).execute()
            val json = response.body?.string() ?: return@runCatching null
            if (!response.isSuccessful) {
                Log.w(TAG, "IGDB request failed: ${response.code}")
                return@runCatching null
            }
            json
        }.getOrNull()
    }
}
