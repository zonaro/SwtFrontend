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

import org.json.JSONObject

/**
 * Internal JSON extension helpers ported from Kwiq's KwiqCore.kt.
 *
 * These avoid Gson overhead for simple field extraction from org.json responses.
 */

internal fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

internal fun JSONObject.optLongOrNull(key: String): Long? =
    if (has(key) && !isNull(key)) runCatching { getLong(key) }.getOrNull() else null

internal fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (has(key) && !isNull(key)) runCatching { getDouble(key) }.getOrNull() else null

internal fun JSONObject.optNestedNames(key: String): List<String> =
    optNestedStrings(key, "name")

internal fun JSONObject.optNestedStrings(arrayKey: String, valueKey: String): List<String> {
    val arr = optJSONArray(arrayKey) ?: return emptyList()
    return List(arr.length()) { arr.optJSONObject(it) }.mapNotNull { it?.optStringOrNull(valueKey) }
}

internal fun JSONObject.optImageUrls(arrayKey: String, size: String): List<String> {
    val arr = optJSONArray(arrayKey) ?: return emptyList()
    return List(arr.length()) { arr.optJSONObject(it) }.mapNotNull {
        it?.optStringOrNull("image_id")?.igdbImageUrl(size)
    }
}

internal fun String.igdbImageUrl(size: String): String =
    "https://images.igdb.com/igdb/image/upload/$size/$this.jpg"

internal fun String.escapeIgdb(): String =
    replace("\\", "\\\\").replace("\"", "\\\"")
