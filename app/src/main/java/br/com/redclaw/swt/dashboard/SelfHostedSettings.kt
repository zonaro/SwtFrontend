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
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Configuration for the self-hosted HTTP dashboard server. */
data class SelfHostedSettings(
    val enabled: Boolean = false,
    val port: Int = 7120,
    val users: List<DashboardUser> = defaultUsers(),
) {
    fun authenticate(user: String, pass: String): UserRole? {
        return users.firstOrNull { it.username == user && it.password == pass }?.role
    }

    fun hasAdmin(): Boolean = users.any { it.role == UserRole.ADMIN }
}

data class DashboardUser(
    val username: String,
    val password: String,
    val role: UserRole
)

fun defaultUsers(): List<DashboardUser> =
    listOf(
        DashboardUser("Swt", "", UserRole.ADMIN),
        DashboardUser("Visitante", "", UserRole.VISITOR)
    )

/** SharedPreferences persistence for dashboard settings. */
object SelfHostedPrefs {
    private const val PREFS = "swt_self_hosted"
    private const val KEY_ENABLED = "sh_enabled"
    private const val KEY_PORT = "sh_port"
    private const val KEY_USERS_JSON = "sh_users_json"

    fun load(context: Context): SelfHostedSettings {
        val prefs = prefs(context)
        val usersJson = prefs.getString(KEY_USERS_JSON, null)
        val users = if (usersJson != null) {
            runCatching { usersFromJson(usersJson) }.getOrDefault(defaultUsers())
        } else {
            defaultUsers()
        }
        return SelfHostedSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            port = prefs.getInt(KEY_PORT, 7120).coerceIn(1024, 65535),
            users = users
        )
    }

    fun save(context: Context, settings: SelfHostedSettings) {
        prefs(context)
            .edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putInt(KEY_PORT, settings.port.coerceIn(1024, 65535))
            .putString(KEY_USERS_JSON, usersToJson(settings.users))
            .apply()
    }

    fun saveEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun usersToJson(users: List<DashboardUser>): String {
        val arr = JSONArray()
        users.forEach { user ->
            arr.put(
                JSONObject()
                    .put("u", user.username)
                    .put("p", user.password)
                    .put("r", user.role.name)
            )
        }
        return arr.toString()
    }

    private fun usersFromJson(json: String): List<DashboardUser> {
        val arr = JSONArray(json)
        val list = mutableListOf<DashboardUser>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                DashboardUser(
                    username = obj.getString("u"),
                    password = obj.getString("p"),
                    role = UserRole.valueOf(obj.getString("r"))
                )
            )
        }
        return list
    }
}
