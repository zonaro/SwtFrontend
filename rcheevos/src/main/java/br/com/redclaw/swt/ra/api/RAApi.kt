/*
 * SwtFrontend - RetroAchievements direct API client.
 *
 * Kotlin HTTP client for the RetroAchievements dorequest.php and Web API endpoints.
 * Pattern derived from rafaelvcaetano/melonDS-android RAApi.kt.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package br.com.redclaw.swt.ra.api

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Direct RetroAchievements API client using OkHttp.
 *
 * Provides two categories of endpoints:
 * 1. dorequest.php — server-authoritative requests (login2, startsession, patch, unlocks,
 *    awardachievement, submitlbentry, ping). These require MD5(username+token) signature
 *    for awardachievement.
 * 2. Web API — read-only profile/game queries (API_GetUserSummary, API_GetGameInfoAndUserProgress,
 *    API_GetUserRecentlyPlayedGames). These authenticate with a Web API key as the `y` param.
 *
 * @param userAgent Full User-Agent header value per rcheevos contract.
 * @param client Shared OkHttp instance; defaults to one tuned for the RA API.
 */
class RAApi(
    private val userAgent: String,
    private val client: OkHttpClient = defaultOkHttp()
) {

    // ------------------------------------------------------------------ //
    // dorequest.php endpoints                                             //
    // ------------------------------------------------------------------ //

    /**
     * Login with username and password. Returns the issued token on success.
     *
     * @return Result with token string on success, or failure with error message.
     */
    suspend fun login2(username: String, password: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/dorequest.php"
                val body = "r=login2&u=${enc(username)}&p=${enc(password)}"
                val response = postForm(url, body) ?: return@withContext Result.failure(IOException("request failed"))
                val json = JSONObject(response)
                if (json.optBoolean("Success", false)) {
                    val token = json.optString("Token", "")
                    if (token.isNotBlank()) {
                        Result.success(token)
                    } else {
                        Result.failure(IOException("no token in response"))
                    }
                } else {
                    Result.failure(IOException(json.optString("Error", "login failed")))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Start a game session. Returns session metadata on success.
     */
    suspend fun startSession(
        username: String,
        token: String,
        gameId: Long,
        richPresencePatch: String? = null
    ): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/dorequest.php"
                val sb = StringBuilder("r=startsession&u=${enc(username)}&t=${enc(token)}&g=$gameId")
                if (richPresencePatch != null) {
                    sb.append("&rp=${enc(richPresencePatch)}")
                }
                val response = postForm(url, sb.toString()) ?: return@withContext Result.failure(IOException("request failed"))
                val json = JSONObject(response)
                if (json.optBoolean("Success", false)) {
                    Result.success(json)
                } else {
                    Result.failure(IOException(json.optString("Error", "startsession failed")))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Fetch achievement/leaderboard patch data for a game.
     */
    suspend fun patch(
        username: String,
        token: String,
        gameId: Long
    ): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/dorequest.php"
                val body = "r=patch&u=${enc(username)}&t=${enc(token)}&g=$gameId"
                val response = postForm(url, body) ?: return@withContext Result.failure(IOException("request failed"))
                val json = JSONObject(response)
                if (json.optBoolean("Success", false)) {
                    Result.success(json)
                } else {
                    Result.failure(IOException(json.optString("Error", "patch failed")))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Fetch user unlocks for a game.
     */
    suspend fun unlocks(
        username: String,
        token: String,
        gameId: Long,
        hardcore: Boolean = false
    ): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/dorequest.php"
                val hc = if (hardcore) "1" else "0"
                val body = "r=unlocks&u=${enc(username)}&t=${enc(token)}&g=$gameId&h=$hc"
                val response = postForm(url, body) ?: return@withContext Result.failure(IOException("request failed"))
                val json = JSONObject(response)
                if (json.optBoolean("Success", false)) {
                    Result.success(json)
                } else {
                    Result.failure(IOException(json.optString("Error", "unlocks failed")))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Award an achievement. Requires MD5(username + token) signature.
     */
    suspend fun awardAchievement(
        username: String,
        token: String,
        achievementId: Long,
        hardcore: Boolean = false
    ): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/dorequest.php"
                val signature = AndroidRASignatureProvider.computeMd5(username + token)
                val hc = if (hardcore) "1" else "0"
                val body = "r=awardachievement&u=${enc(username)}&t=${enc(token)}" +
                    "&a=$achievementId&h=$hc&v=$signature"
                val response = postForm(url, body) ?: return@withContext Result.failure(IOException("request failed"))
                val json = JSONObject(response)
                if (json.optBoolean("Success", false)) {
                    Result.success(json)
                } else {
                    Result.failure(IOException(json.optString("Error", "awardachievement failed")))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Submit a leaderboard entry.
     */
    suspend fun submitLbEntry(
        username: String,
        token: String,
        leaderboardId: Long,
        score: Long,
        leaderboardFormat: Int = 0
    ): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/dorequest.php"
                val body = "r=submitlbentry&u=${enc(username)}&t=${enc(token)}" +
                    "&i=$leaderboardId&s=$score&f=$leaderboardFormat"
                val response = postForm(url, body) ?: return@withContext Result.failure(IOException("request failed"))
                val json = JSONObject(response)
                if (json.optBoolean("Success", false)) {
                    Result.success(json)
                } else {
                    Result.failure(IOException(json.optString("Error", "submitlbentry failed")))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Keep-alive ping.
     */
    suspend fun ping(
        username: String,
        token: String,
        gameId: Long
    ): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/dorequest.php"
                val body = "r=ping&u=${enc(username)}&t=${enc(token)}&g=$gameId"
                val response = postForm(url, body) ?: return@withContext Result.failure(IOException("request failed"))
                val json = JSONObject(response)
                Result.success(json.optBoolean("Success", false))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ------------------------------------------------------------------ //
    // Web API endpoints (read-only profile/game queries)                  //
    // ------------------------------------------------------------------ //

    /**
     * Get user summary (profile, recent games, recent achievements).
     * Endpoint: API_GetUserSummary.php?z=<user>&y=<key>&u=<user>&g=3&a=5
     */
    suspend fun getUserSummary(
        username: String,
        apiKey: String
    ): Result<JSONObject> =
        webApiGet("API_GetUserSummary.php", username, apiKey, "g=3&a=5")

    /**
     * Get game info and user progress for a specific game.
     * Endpoint: API_GetGameInfoAndUserProgress.php?z=<user>&y=<key>&u=<user>&g=<gameId>
     */
    suspend fun getGameInfoAndUserProgress(
        username: String,
        apiKey: String,
        gameId: Long
    ): Result<JSONObject> =
        webApiGet("API_GetGameInfoAndUserProgress.php", username, apiKey, "g=$gameId")

    /**
     * Get user's recently played games.
     * Endpoint: API_GetUserRecentlyPlayedGames.php?z=<user>&y=<key>&u=<user>&c=<count>
     */
    suspend fun getUserRecentlyPlayedGames(
        username: String,
        apiKey: String,
        count: Int = 10
    ): Result<JSONObject> =
        webApiGet("API_GetUserRecentlyPlayedGames.php", username, apiKey, "c=$count")

    // ------------------------------------------------------------------ //
    // Internals                                                           //
    // ------------------------------------------------------------------ //

    private suspend fun webApiGet(
        endpoint: String,
        username: String,
        apiKey: String,
        extraParams: String
    ): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            try {
                val sb = StringBuilder("$WEB_API_BASE/$endpoint?z=${enc(username)}&y=${enc(apiKey)}")
                if (extraParams.isNotBlank()) {
                    sb.append("&$extraParams")
                }
                val response = get(sb.toString()) ?: return@withContext Result.failure(IOException("request failed"))
                val json = JSONObject(response)
                if (json.has("Error")) {
                    Result.failure(IOException(json.optString("Error", "api error")))
                } else {
                    Result.success(json)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun postForm(url: String, body: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull()))
            .build()
        return executeRequest(request)
    }

    private fun get(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .build()
        return executeRequest(request)
    }

    private fun executeRequest(request: Request): String? {
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    Log.w(TAG, "HTTP ${response.code} for ${request.url}")
                    null
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Network error: ${e.message}")
            null
        }
    }

    /** URL-encodes a string for form data. */
    private fun enc(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val TAG = "RAApi"
        const val BASE_URL = "https://retroachievements.org"
        const val WEB_API_BASE = "https://retroachievements.org/API"

        fun defaultOkHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
