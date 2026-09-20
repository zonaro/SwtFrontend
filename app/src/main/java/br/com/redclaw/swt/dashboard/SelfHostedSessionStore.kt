/*
 * SwtFrontend — Self-hosted dashboard (ported from Kwiq, GPLv3).
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 */
package br.com.redclaw.swt.dashboard

import android.content.Context
import java.security.SecureRandom
import org.json.JSONObject

/**
 * Persistent browser sessions for the SwtFrontend Dashboard.
 *
 * Tokens are stored in SharedPreferences so a login survives server/service restarts.
 */
internal object SelfHostedSessionStore {

    private const val PREFS = "swt_self_hosted_sessions"
    private const val KEY_SESSIONS = "sessions_json"
    const val SESSION_TTL_SECONDS = 60L * 60L * 24L * 30L // 30 days
    private const val MAX_SESSIONS = 64

    private val random = SecureRandom()

    data class Session(
        val token: String,
        val username: String,
        val role: UserRole,
        val expiresAt: Long
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun readAll(context: Context): MutableMap<String, Session> {
        val raw = prefs(context).getString(KEY_SESSIONS, null) ?: return mutableMapOf()
        val now = System.currentTimeMillis()
        return runCatching {
            val root = JSONObject(raw)
            val sessions = mutableMapOf<String, Session>()
            root.keys().forEach { token ->
                val entry = root.getJSONObject(token)
                val expiresAt = entry.optLong("e")
                if (expiresAt <= now) return@forEach
                val role = runCatching {
                    UserRole.valueOf(entry.optString("r"))
                }.getOrNull() ?: return@forEach
                sessions[token] = Session(token, entry.optString("u"), role, expiresAt)
            }
            sessions
        }.getOrDefault(mutableMapOf())
    }

    private fun writeAll(context: Context, sessions: Map<String, Session>) {
        val root = JSONObject()
        sessions.values
            .sortedByDescending { it.expiresAt }
            .take(MAX_SESSIONS)
            .forEach { session ->
                root.put(
                    session.token,
                    JSONObject()
                        .put("u", session.username)
                        .put("r", session.role.name)
                        .put("e", session.expiresAt)
                )
            }
        prefs(context).edit().putString(KEY_SESSIONS, root.toString()).apply()
    }

    fun create(context: Context, username: String, role: UserRole): Session {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        val token = bytes.joinToString("") { "%02x".format(it) }
        val session = Session(
            token = token,
            username = username,
            role = role,
            expiresAt = System.currentTimeMillis() + SESSION_TTL_SECONDS * 1000L
        )
        val sessions = readAll(context)
        sessions[token] = session
        writeAll(context, sessions)
        return session
    }

    fun resolve(context: Context, token: String): Session? {
        val sessions = readAll(context)
        val session = sessions[token] ?: return null
        val refreshed = session.copy(
            expiresAt = System.currentTimeMillis() + SESSION_TTL_SECONDS * 1000L
        )
        if (refreshed.expiresAt - session.expiresAt > 24L * 60L * 60L * 1000L) {
            sessions[token] = refreshed
            writeAll(context, sessions)
        }
        return session
    }

    fun revoke(context: Context, token: String) {
        val sessions = readAll(context)
        if (sessions.remove(token) != null) writeAll(context, sessions)
    }

    fun revokeAll(context: Context) {
        prefs(context).edit().remove(KEY_SESSIONS).apply()
    }
}
