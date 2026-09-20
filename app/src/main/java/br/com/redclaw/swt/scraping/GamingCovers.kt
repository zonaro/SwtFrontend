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
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gaming covers helper — resolves cover image URLs and manages local cover storage.
 *
 * Pattern: localCoverUri ?: remoteUrl — prefer the local copy if available.
 * Covers are stored in `filesDir/covers/`.
 *
 * Ported from Kwiq's KwiqGamingCovers, simplified for SwtFrontend without Compose/Hilt.
 */
object GamingCovers {
    private const val TAG = "SwtGamingCovers"
    private const val COVERS_DIR = "covers"
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    /**
     * Returns the best cover source for a game: local file if present, otherwise remote URL.
     */
    fun coverSource(context: Context, metadata: GameMetadata?): String? {
        metadata?.localCoverUri?.let { localUri ->
            val localFile = resolveLocalFile(context, localUri)
            if (localFile != null && localFile.exists()) return localUri
        }
        return metadata?.coverUrl
    }

    /**
     * Ensures `filesDir/covers/` exists, creating it if needed.
     */
    fun ensureCoversDir(context: Context): File {
        val dir = File(context.filesDir, COVERS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Returns the cover file for a given item key (hash-based filename).
     * Supports multiple image extensions, returns the most recent match.
     */
    fun coverFileFor(context: Context, itemKey: String): File? {
        val safeKey = Integer.toHexString(itemKey.hashCode())
        val dir = ensureCoversDir(context)
        return dir.listFiles { file ->
            file.isFile && file.name.startsWith("$safeKey.")
        }?.maxByOrNull { it.lastModified() }
    }

    /**
     * Downloads a remote image URL into `filesDir/covers/` and returns the local URI string.
     * Must be called from a non-main thread (e.g. via Dispatchers.IO).
     *
     * @return Local file URI string, or null on failure.
     */
    suspend fun downloadCoverToLocal(
        context: Context,
        itemKey: String,
        imageUrl: String
    ): String? = withContext(Dispatchers.IO) {
        if (!imageUrl.startsWith("http")) return@withContext null

        val extension = imageUrl.substringBefore('?')
            .substringAfterLast('.', "jpg")
            .lowercase(Locale.US)
            .takeIf { it in IMAGE_EXTENSIONS }
            ?: "jpg"

        val outFile = coverFile(context, itemKey, extension)
        runCatching {
            outFile.parentFile?.mkdirs()
            val connection = URL(imageUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            try {
                connection.inputStream.use { input ->
                    FileOutputStream(outFile).use { output -> input.copyTo(output) }
                }
            } finally {
                connection.disconnect()
            }
            Uri.fromFile(outFile).toString()
        }.getOrNull()
    }

    /**
     * Saves raw image bytes into the covers directory.
     *
     * @return Local file URI string, or null on failure.
     */
    fun saveCoverBytes(
        context: Context,
        itemKey: String,
        bytes: ByteArray,
        extension: String
    ): String? {
        val ext = extension.lowercase(Locale.US).takeIf { it in IMAGE_EXTENSIONS } ?: "jpg"
        val outFile = coverFile(context, itemKey, ext)
        return runCatching {
            outFile.parentFile?.mkdirs()
            FileOutputStream(outFile).use { it.write(bytes) }
            Uri.fromFile(outFile).toString()
        }.getOrNull()
    }

    /**
     * Deletes the local cover for a given item key.
     */
    fun deleteCover(context: Context, itemKey: String): Boolean {
        val file = coverFileFor(context, itemKey) ?: return false
        return file.delete()
    }

    /**
     * Returns the best background source: local if present, otherwise remote.
     */
    fun backgroundSource(context: Context, metadata: GameMetadata?): String? {
        metadata?.localBackgroundUri?.let { localUri ->
            val localFile = resolveLocalFile(context, localUri)
            if (localFile != null && localFile.exists()) return localUri
        }
        return null
    }

    // ── Browser search URL builders ─────────────────────────────────────────

    /**
     * Builds an image-search URL for game cover art.
     */
    fun browserSearchUrl(
        title: String,
        platform: String?,
        source: CoverSearchSource = CoverSearchSource.GoogleImages
    ): String {
        val query = buildString {
            append(cleanGameTitleForSearch(title))
            if (!platform.isNullOrBlank()) append(' ').append(platform)
            append(" box art cover")
        }.trim()
        val encoded = URLEncoder.encode(query, "UTF-8")
        return when (source) {
            CoverSearchSource.GoogleImages ->
                "https://www.google.com/search?tbm=isch&q=$encoded"
            CoverSearchSource.TheGamesDb ->
                "https://thegamesdb.net/search.php?name=$encoded"
            CoverSearchSource.SteamGridDb ->
                "https://www.steamgriddb.com/search/grids?term=$encoded"
        }
    }

    /**
     * Builds a background/wallpaper search URL.
     */
    fun backgroundBrowserSearchUrl(title: String, platform: String?): String {
        val query = buildString {
            append(cleanGameTitleForSearch(title))
            if (!platform.isNullOrBlank()) append(' ').append(platform)
            append(" game wallpaper screenshot 16:9")
        }.trim()
        return "https://www.google.com/search?tbm=isch&q=${URLEncoder.encode(query, "UTF-8")}"
    }

    // ── Internals ───────────────────────────────────────────────────────────

    private fun coverFile(context: Context, itemKey: String, extension: String): File {
        val safeKey = Integer.toHexString(itemKey.hashCode())
        return File(File(context.filesDir, COVERS_DIR), "$safeKey.$extension")
    }

    private fun resolveLocalFile(context: Context, uri: String): File? = runCatching {
        when {
            uri.startsWith("file://") -> File(Uri.parse(uri).path ?: return null)
            uri.startsWith("/") -> File(uri)
            else -> null
        }
    }.getOrNull()
}

/**
 * Cleans a game title for search queries — removes parentheticals, version numbers,
 * and moves "The" to the front.
 *
 * Ported from Kwiq's cleanGameTitleForSearch.
 */
internal fun cleanGameTitleForSearch(title: String): String {
    if (title.isBlank()) return title
    var result = title
    result = result.replace(Regex("\\([^)]*\\)"), "")
    val theMatch = Regex(",\\s*the\\b", RegexOption.IGNORE_CASE).find(result)
    if (theMatch != null) {
        result = "The " + result.replaceFirst(theMatch.value, "").trimStart(',', '-', ' ')
    }
    result = result.replace(Regex("\\b[vV]\\d+(?:\\.\\d+)*\\b"), "")
    result = result.replace(Regex("\\s+"), " ").trim().trim(',', '-', ' ').trim()
    return result.ifBlank { title }
}
