/*
 * CoreUpdaterImpl.kt
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

package br.com.redclaw.swt.cores

import android.content.Context
import android.os.Build
import android.util.Log
import com.swordfish.lemuroid.lib.core.CoreUpdater
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

class CoreUpdaterImpl(
    private val context: Context,
    private val directoriesManager: DirectoriesManager,
) : CoreUpdater {

    companion object {
        private const val TAG = "SwtCoreUpdater"
        private const val CORES_VERSION = "1.17.0"
        private const val BASE_URL = "https://github.com/Swordfish90/LemuroidCores/raw/$CORES_VERSION"
        private const val CONNECT_TIMEOUT_SECONDS = 30L
        private const val READ_TIMEOUT_SECONDS = 60L
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    interface ProgressCallback {
        fun onCoreDownloadStart(coreID: CoreID, index: Int, total: Int)
        fun onCoreDownloadProgress(coreID: CoreID, bytesDownloaded: Long, totalBytes: Long)
        fun onCoreDownloadComplete(coreID: CoreID, file: File)
        fun onCoreDownloadError(coreID: CoreID, error: Throwable)
        fun onAllCoresComplete(totalDownloaded: Int, totalFailed: Int)
    }

    data class DownloadResult(
        val coreID: CoreID,
        val file: File?,
        val error: Throwable? = null,
    )

    override suspend fun downloadCores(
        context: Context,
        coreIDs: List<CoreID>,
    ) {
        downloadCores(context, coreIDs, null)
    }

    suspend fun downloadCores(
        context: Context,
        coreIDs: List<CoreID>,
        callback: ProgressCallback?,
    ): List<DownloadResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DownloadResult>()
        var downloadedCount = 0
        var failedCount = 0

        coreIDs.forEachIndexed { index, coreID ->
            callback?.onCoreDownloadStart(coreID, index, coreIDs.size)

            try {
                val file = downloadCore(coreID, callback)
                results.add(DownloadResult(coreID, file))
                downloadedCount++
                callback?.onCoreDownloadComplete(coreID, file)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to download core: ${coreID.coreName}", e)
                results.add(DownloadResult(coreID, null, e))
                failedCount++
                callback?.onCoreDownloadError(coreID, e)
            }
        }

        callback?.onAllCoresComplete(downloadedCount, failedCount)
        results
    }

    fun getCoreFile(coreID: CoreID): File? {
        val coresDir = directoriesManager.getCoresDirectory()
        val versionDir = File(coresDir, CORES_VERSION)
        val destFile = File(versionDir, coreID.libretroFileName)
        return if (destFile.exists()) destFile else null
    }

    fun getCoresVersion(): String = CORES_VERSION

    fun isCoreDownloaded(coreID: CoreID): Boolean = getCoreFile(coreID) != null

    fun getDownloadedCores(): List<CoreID> {
        return CoreID.values().filter { isCoreDownloaded(it) }
    }

    private suspend fun downloadCore(
        coreID: CoreID,
        callback: ProgressCallback?,
    ): File {
        Log.i(TAG, "Downloading core: ${coreID.coreName}")

        val coresDir = directoriesManager.getCoresDirectory()
        val versionDir = File(coresDir, CORES_VERSION).apply { mkdirs() }
        val destFile = File(versionDir, coreID.libretroFileName)

        if (destFile.exists()) {
            Log.d(TAG, "Core already downloaded: ${coreID.coreName}")
            return destFile
        }

        runCatching { deleteOutdatedCores(coresDir, CORES_VERSION) }

        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        val url = "$BASE_URL/lemuroid_core_${coreID.coreName}/src/main/jniLibs/$abi/${coreID.libretroFileName}"

        Log.d(TAG, "Download URL: $url")

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            response.close()
            throw Exception("Download failed: HTTP ${response.code} — $errorBody")
        }

        val body = response.body ?: throw Exception("Empty response body")
        val contentLength = body.contentLength()
        val inputStream: InputStream = body.byteStream()

        try {
            var bytesDownloaded = 0L
            val buffer = ByteArray(8 * 1024)

            destFile.outputStream().use { outputStream ->
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    bytesDownloaded += bytesRead
                    callback?.onCoreDownloadProgress(coreID, bytesDownloaded, contentLength)
                }
            }

            Log.i(TAG, "Core downloaded successfully: ${coreID.coreName} ($bytesDownloaded bytes)")
            return destFile
        } finally {
            inputStream.close()
            response.close()
        }
    }

    private fun deleteOutdatedCores(
        mainCoresDirectory: File,
        currentVersion: String,
    ) {
        mainCoresDirectory.listFiles()
            ?.filter { it.name != currentVersion }
            ?.forEach {
                Log.d(TAG, "Deleting outdated cores version: ${it.name}")
                it.deleteRecursively()
            }
    }
}
