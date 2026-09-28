/*
 * SwtFrontend - shared capture storage for every game and companion module.
 * Copyright (C) 2026 RedClaw — GPLv3, see COPYING.
 */

package br.com.redclaw.swt.capture

import android.content.Context
import java.io.File

enum class CaptureMediaType { IMAGE, VIDEO }

data class CaptureItem(
    val type: CaptureMediaType,
    val file: File,
    val sourceId: String?,
    val timestamp: Long,
)

/**
 * Single owner for screenshots and recordings produced by SwtFrontend.
 *
 * Keeping capture files in the host makes the gallery independent from the emulator/module that
 * happens to be running. [sourceId] is a global module/game id when one is available.
 */
class CaptureStore(context: Context) {
    val directory: File = File(context.filesDir, DIRECTORY_NAME).apply { mkdirs() }

    fun screenshotFile(sourceId: String?, timestamp: Long = System.currentTimeMillis()): File =
        File(directory, "screenshot_${safeSourceId(sourceId)}_${timestamp}.png")

    fun recordingFile(sourceId: String?, timestamp: Long = System.currentTimeMillis()): File =
        File(directory, "recording_${safeSourceId(sourceId)}_${timestamp}.mp4")

    fun list(): List<CaptureItem> =
        directory.listFiles()
            .orEmpty()
            .asSequence()
            .filter(File::isFile)
            .mapNotNull(::parse)
            .sortedByDescending(CaptureItem::timestamp)
            .toList()

    fun delete(item: CaptureItem): Boolean = !item.file.exists() || item.file.delete()

    internal fun parse(file: File): CaptureItem? {
        val (prefix, suffix, type) = when {
            file.name.startsWith("screenshot_") && file.name.endsWith(".png", true) ->
                Triple("screenshot_", ".png", CaptureMediaType.IMAGE)
            file.name.startsWith("recording_") && file.name.endsWith(".mp4", true) ->
                Triple("recording_", ".mp4", CaptureMediaType.VIDEO)
            else -> return null
        }
        val body = file.name.removePrefix(prefix).removeSuffix(suffix)
        val separator = body.lastIndexOf('_')
        if (separator <= 0) return null
        val timestamp = body.substring(separator + 1).toLongOrNull() ?: return null
        val source = body.substring(0, separator).takeUnless { it == UNKNOWN_SOURCE }
        return CaptureItem(type, file, source, timestamp)
    }

    companion object {
        const val DIRECTORY_NAME = "captures"
        private const val UNKNOWN_SOURCE = "unknown"

        fun safeSourceId(sourceId: String?): String =
            sourceId
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.replace(Regex("[^A-Za-z0-9._:-]+"), "-")
                ?.take(120)
                ?: UNKNOWN_SOURCE
    }
}
