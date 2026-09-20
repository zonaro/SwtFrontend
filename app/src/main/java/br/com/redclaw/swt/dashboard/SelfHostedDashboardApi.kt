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
import android.os.Environment
import android.os.StatFs
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import org.json.JSONObject

/**
 * Data layer behind the SwtFrontend Dashboard pages.
 *
 * Everything here is plain data in / plain data out so [SelfHostedHttpServer] only has to worry
 * about routing, auth and serialization.
 *
 * Ported from Kwiq's SelfHostedDashboardApi (GPLv3), adapted for SwtFrontend.
 * Uses mock/placeholder data where the retrograde-app-shared library is not yet populated.
 */
internal object SelfHostedDashboardApi {

    private const val MAX_IMAGE_DOWNLOAD_BYTES = 32L * 1024L * 1024L

    // ─── Dashboard summary ────────────────────────────────────────────────────

    fun dashboard(
        context: Context,
        settings: SelfHostedSettings,
        isAdmin: Boolean
    ): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>(
            "server" to serverInfo(settings),
            "storage" to storageInfo()
        )
        if (isAdmin) {
            result["library"] = librarySummary(context)
        }
        return result
    }

    private fun serverInfo(settings: SelfHostedSettings): Map<String, Any?> =
        mapOf(
            "port" to settings.port,
            "rootPath" to contextFilesDir()
        )

    private fun storageInfo(): Map<String, Any?> = runCatching {
        val stat = StatFs(Environment.getExternalStorageDirectory().absolutePath)
        val free = stat.availableBytes
        val total = stat.totalBytes
        mapOf(
            "freeBytes" to free,
            "totalBytes" to total,
            "freeText" to formatSize(free),
            "totalText" to formatSize(total)
        )
    }.getOrDefault(mapOf("freeText" to "\u2014", "totalText" to "\u2014"))

    private fun librarySummary(context: Context): Map<String, Any?> {
        val roms = scanRoms(context)
        val platforms = roms.groupBy { it.platform }.mapValues { it.value.size }
        return mapOf(
            "totalRoms" to roms.size,
            "platformCount" to platforms.size,
            "platforms" to platforms.map { (name, count) ->
                mapOf("name" to name, "romCount" to count)
            }
        )
    }

    // ─── Gaming: platforms ─────────────────────────────────────────────────────

    fun gamingPlatforms(): Map<String, Any?> =
        mapOf("platforms" to PLATFORMS.map { platformToMap(it) })

    // ─── Gaming: ROMs ─────────────────────────────────────────────────────────

    fun gamingRoms(context: Context): Map<String, Any?> {
        val roms = scanRoms(context)
        return mapOf(
            "count" to roms.size,
            "roms" to roms.map { rom ->
                val coverFile = coverFileFor(context, rom.documentUri)
                val bgFile = backgroundFileFor(context, rom.documentUri)
                mapOf(
                    "documentUri" to rom.documentUri,
                    "name" to rom.name,
                    "platform" to rom.platform,
                    "extension" to rom.extension,
                    "size" to rom.sizeBytes,
                    "hasCover" to (coverFile?.exists() == true),
                    "hasBackground" to (bgFile?.exists() == true)
                )
            }
        )
    }

    fun gamingRomDetail(context: Context, key: String): Map<String, Any?> {
        val rom = findGamingRom(context, key) ?: error("ROM not found")
        val coverFile = coverFileFor(context, key)
        val bgFile = backgroundFileFor(context, key)
        return mapOf(
            "rom" to mapOf(
                "documentUri" to rom.documentUri,
                "name" to rom.name,
                "platform" to rom.platform,
                "extension" to rom.extension,
                "size" to rom.sizeBytes,
                "hasCover" to (coverFile?.exists() == true),
                "hasBackground" to (bgFile?.exists() == true)
            )
        )
    }

    fun gamingDeleteRom(context: Context, key: String): Map<String, Any?> {
        val file = File(key)
        if (file.exists() && file.isFile) {
            require(file.delete()) { "Could not delete ROM" }
        }
        // Clean up cover/background
        coverFileFor(context, key)?.delete()
        backgroundFileFor(context, key)?.delete()
        return mapOf("success" to true)
    }

    fun saveGamingRomMetadata(
        context: Context,
        key: String,
        body: JSONObject
    ): Map<String, Any?> {
        // Store metadata as a JSON sidecar file next to the ROM
        val metaFile = File(key + ".meta.json")
        metaFile.writeText(body.toString(2))
        return mapOf("success" to true)
    }

    fun gamingUploadRom(
        context: Context,
        platformId: String?,
        originalName: String,
        source: InputStream
    ): Map<String, Any?> {
        val ext = originalName.substringAfterLast('.', "").lowercase(Locale.US)
        val resolvedPlatform = platformId
            ?: PLATFORMS.firstOrNull { it.supportedExtensions.contains(ext) }?.uniqueId
            ?: error("Unsupported extension: .$ext")

        val romDir = File(context.filesDir, "roms/$resolvedPlatform").apply { mkdirs() }
        val target = File(romDir, originalName)
        target.outputStream().use { output -> source.copyTo(output) }
        val rom = GameRomEntry(
            documentUri = target.absolutePath,
            name = originalName.substringBeforeLast('.'),
            platform = resolvedPlatform,
            extension = ext,
            sizeBytes = target.length()
        )
        return mapOf("success" to true, "rom" to romToMap(rom))
    }

    fun gamingResolve(context: Context, filename: String): Map<String, Any?> {
        val ext = filename.substringAfterLast('.', "").lowercase(Locale.US)
        val matching = PLATFORMS.filter { it.supportedExtensions.contains(ext) }
        return when {
            matching.isEmpty() -> mapOf("status" to "unsupported")
            matching.size == 1 -> mapOf("status" to "unique", "platform" to platformToMap(matching[0]))
            else -> mapOf(
                "status" to "ambiguous",
                "platforms" to matching.map(::platformToMap)
            )
        }
    }

    fun gamingSearch(
        context: Context,
        source: String,
        query: String,
        platform: String?
    ): Map<String, Any?> {
        // Placeholder: real implementation will call IGDB/TGB APIs
        return mapOf(
            "results" to emptyList<Any>(),
            "note" to "Search providers not yet configured. Configure IGDB/TheGamesDb API keys in Settings."
        )
    }

    // ─── Gaming: covers & backgrounds ─────────────────────────────────────────

    fun gamingSaveCoverBytes(
        context: Context,
        key: String,
        bytes: ByteArray,
        filename: String
    ): Map<String, Any?> {
        require(bytes.isNotEmpty()) { "Empty file" }
        val dir = coversDir(context)
        val target = File(dir, coverFileName(key))
        target.writeBytes(bytes)
        return mapOf("success" to true)
    }

    fun gamingSaveBackgroundBytes(
        context: Context,
        key: String,
        bytes: ByteArray,
        filename: String
    ): Map<String, Any?> {
        require(bytes.isNotEmpty()) { "Empty file" }
        val dir = backgroundsDir(context)
        val target = File(dir, coverFileName(key))
        target.writeBytes(bytes)
        return mapOf("success" to true)
    }

    fun gamingClearCover(context: Context, key: String): Map<String, Any?> {
        coverFileFor(context, key)?.delete()
        return mapOf("success" to true)
    }

    fun gamingClearBackground(context: Context, key: String): Map<String, Any?> {
        backgroundFileFor(context, key)?.delete()
        return mapOf("success" to true)
    }

    fun coverFileFor(context: Context, key: String): File? {
        val file = File(coversDir(context), coverFileName(key))
        return file.takeIf { it.exists() }
    }

    fun backgroundFileFor(context: Context, key: String): File? {
        val file = File(backgroundsDir(context), coverFileName(key))
        return file.takeIf { it.exists() }
    }

    // ─── Settings ──────────────────────────────────────────────────────────────

    fun settingsInfo(context: Context): Map<String, Any?> =
        mapOf(
            "port" to SelfHostedPrefs.load(context).port,
            "coversDir" to coversDir(context).absolutePath,
            "backgroundsDir" to backgroundsDir(context).absolutePath,
            "romsDir" to File(context.filesDir, "roms").absolutePath
        )

    // ─── Files ─────────────────────────────────────────────────────────────────

    fun listFiles(context: Context, path: String): Map<String, Any?> {
        val dir = File(path)
        if (!dir.isDirectory) return mapOf("error" to "Not a directory")
        val entries = dir.listFiles()
            ?.filter { !it.name.startsWith(".") }
            ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            ?: emptyList()
        return mapOf(
            "files" to entries.map { file ->
                mapOf(
                    "name" to file.name,
                    "isDirectory" to file.isDirectory,
                    "size" to if (file.isFile) file.length() else 0L,
                    "path" to file.absolutePath
                )
            },
            "currentPath" to dir.absolutePath,
            "parent" to dir.parent
        )
    }

    // ─── ROM scanning ─────────────────────────────────────────────────────────

    fun findGamingRom(context: Context, key: String): GameRomEntry? =
        scanRoms(context).firstOrNull { it.documentUri == key }

    private fun scanRoms(context: Context): List<GameRomEntry> {
        val romsDir = File(context.filesDir, "roms")
        if (!romsDir.exists()) return emptyList()
        val result = mutableListOf<GameRomEntry>()
        romsDir.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                val ext = file.extension.lowercase(Locale.ROOT)
                val platform = PLATFORMS.firstOrNull { it.supportedExtensions.contains(ext) }
                if (platform != null) {
                    result.add(
                        GameRomEntry(
                            documentUri = file.absolutePath,
                            name = file.nameWithoutExtension,
                            platform = platform.uniqueId,
                            extension = ext,
                            sizeBytes = file.length()
                        )
                    )
                }
            }
        return result
    }

    // ─── File system helpers ───────────────────────────────────────────────────

    private fun coversDir(context: Context): File =
        File(context.filesDir, "covers").apply { mkdirs() }

    private fun backgroundsDir(context: Context): File =
        File(context.filesDir, "backgrounds").apply { mkdirs() }

    private fun coverFileName(key: String): String =
        key.replace(Regex("[^a-zA-Z0-9._-]"), "_").takeLast(200) + ".jpg"

    private fun contextFilesDir(): String = "/data/br.com.redclaw.swt/files"

    // ─── Formatting ────────────────────────────────────────────────────────────

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024L * 1024 * 1024 ->
            "%.1f MB".format(Locale.US, bytes.toDouble() / (1024 * 1024))
        else ->
            "%.2f GB".format(Locale.US, bytes.toDouble() / (1024.0 * 1024 * 1024))
    }

    // ─── Serialization helpers ─────────────────────────────────────────────────

    private fun romToMap(rom: GameRomEntry): Map<String, Any?> = mapOf(
        "documentUri" to rom.documentUri,
        "name" to rom.name,
        "platform" to rom.platform,
        "extension" to rom.extension,
        "size" to rom.sizeBytes
    )

    private fun platformToMap(platform: PlatformEntry): Map<String, Any?> = mapOf(
        "uniqueId" to platform.uniqueId,
        "name" to platform.name,
        "shortname" to platform.shortname,
        "extensions" to platform.supportedExtensions.sorted()
    )

    // ─── Data classes ──────────────────────────────────────────────────────────

    data class GameRomEntry(
        val documentUri: String,
        val name: String,
        val platform: String,
        val extension: String,
        val sizeBytes: Long
    )

    data class PlatformEntry(
        val uniqueId: String,
        val name: String,
        val shortname: String,
        val supportedExtensions: List<String>
    )

    // ─── Platform definitions (common retro systems) ───────────────────────────

    val PLATFORMS = listOf(
        PlatformEntry("nes", "Nintendo Entertainment System", "nes", listOf("nes", "fds")),
        PlatformEntry("snes", "Super Nintendo", "snes", listOf("sfc", "smc", "fig", "swc")),
        PlatformEntry("gb", "Game Boy", "gb", listOf("gb")),
        PlatformEntry("gbc", "Game Boy Color", "gbc", listOf("gbc")),
        PlatformEntry("gba", "Game Boy Advance", "gba", listOf("gba", "agb")),
        PlatformEntry("n64", "Nintendo 64", "n64", listOf("n64", "z64", "v64", "pal")),
        PlatformEntry("nds", "Nintendo DS", "nds", listOf("nds", "dsi")),
        PlatformEntry("gen", "Sega Genesis / Mega Drive", "gen", listOf("gen", "md", "bin")),
        PlatformEntry("sms", "Sega Master System", "sms", listOf("sms", "sg")),
        PlatformEntry("gg", "Sega Game Gear", "gg", listOf("gg")),
        PlatformEntry("psx", "Sony PlayStation", "psx", listOf("iso", "bin", "cue", "pbp")),
        PlatformEntry("ps2", "Sony PlayStation 2", "ps2", listOf("iso", "bin", "cue")),
        PlatformEntry("pce", "PC Engine / TurboGrafx-16", "pce", listOf("pce", "sgx")),
        PlatformEntry("lynx", "Atari Lynx", "lynx", listOf("lnx")),
        PlatformEntry("wswan", "WonderSwan", "wswan", listOf("ws", "wsc")),
        PlatformEntry("ngp", "Neo Geo Pocket", "ngp", listOf("ngp", "ngc")),
        PlatformEntry("atari2600", "Atari 2600", "a26", listOf("a26", "bin")),
        PlatformEntry("atari7800", "Atari 7800", "a78", listOf("a78")),
        PlatformEntry("col", "ColecoVision", "col", listOf("col", "rom")),
        PlatformEntry("int", "Intellivision", "int", listOf("int", "rom")),
    )
}
