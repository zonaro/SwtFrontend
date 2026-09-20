/*
 * GameLoaderHelper.kt
 *
 * Copyright (C) 2026 RedClaw Studio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package br.com.redclaw.swt.game

import android.content.Context
import android.net.Uri
import android.util.Log
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import com.swordfish.libretrodroid.Variable
import br.com.redclaw.swt.cores.CoreUpdaterImpl
import java.io.File

/**
 * Bridges [CoreID] to the resolved .so file path via [CoreUpdaterImpl].
 *
 * Resolves the core library file at `filesDir/cores/<version>/` and
 * provides convenience methods for game/system directory lookup.
 */
class GameLoaderHelper(
    private val context: Context,
    private val directoriesManager: DirectoriesManager,
    private val coreUpdater: CoreUpdaterImpl,
) {

    companion object {
        private const val TAG = "GameLoaderHelper"
    }

    /**
     * Resolve the absolute path to a core .so library file.
     *
     * @return File pointing to the .so, or null if not yet downloaded.
     */
    fun resolveCorePath(coreID: CoreID): File? {
        return coreUpdater.getCoreFile(coreID)
    }

    /**
     * Get the system directory for BIOS files and system data.
     */
    fun getSystemDirectory(): File = directoriesManager.getSystemDirectory()

    /**
     * Get the saves directory for battery saves and save states.
     */
    fun getSavesDirectory(): File = directoriesManager.getSavesDirectory()

    /**
     * Resolve a game file path from a Game entity's fileUri.
     *
     * @return File if the URI is a file:// scheme, or the content URI string.
     */
    fun resolveGameFile(game: Game): File? {
        return try {
            val uri = Uri.parse(game.fileUri)
            if (uri.scheme == "file") {
                File(uri.path!!)
            } else {
                // For content:// URIs, copy to a temp file for the core
                val tempFile = File(context.cacheDir, "temp_${game.id}_${game.fileName}")
                if (!tempFile.exists()) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                tempFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve game file: ${game.fileUri}", e)
            null
        }
    }

    /**
     * Convert Lemuroid [CoreVariable] list to libretrodroid [Variable] array.
     */
    fun toLibretrodroidVariables(
        coreVariables: List<com.swordfish.lemuroid.lib.core.CoreVariable>,
    ): Array<Variable> {
        return coreVariables.map { Variable(it.key, it.value) }.toTypedArray()
    }

    /**
     * Find the [SystemCoreConfig] for a given game's system.
     */
    fun findSystemCoreConfig(game: Game): SystemCoreConfig? {
        return try {
            val system = GameSystem.findById(game.systemId)
            system.systemCoreConfigs.firstOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to find system for: ${game.systemId}", e)
            null
        }
    }

    /**
     * Check if the system is a dual-screen system (DS, 3DS).
     */
    fun isDualScreenSystem(game: Game): Boolean {
        return try {
            val system = GameSystem.findById(game.systemId)
            system.hasTouchScreen
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get the DS screen layout core variable for a given core.
     */
    fun getDsScreenLayoutVariable(
        coreID: CoreID,
        layout: DsScreenLayout,
    ): Variable {
        return when (coreID) {
            CoreID.MELONDS -> Variable(
                "melonds_screen_layout1",
                layout.melondsValue,
            )
            CoreID.DESMUME -> Variable(
                "desmume_screens_layout",
                layout.desmumeValue,
            )
            else -> Variable("melonds_screen_layout1", layout.melondsValue)
        }
    }

    /**
     * DS dual-screen layout options.
     */
    enum class DsScreenLayout(
        val melondsValue: String,
        val desmumeValue: String,
        val displayName: String,
    ) {
        TOP_BOTTOM("top-bottom", "top/bottom", "Top/Bottom"),
        LEFT_RIGHT("left-right", "left/right", "Left/Right"),
    }
}
