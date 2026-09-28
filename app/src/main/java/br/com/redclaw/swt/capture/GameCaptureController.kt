/*
 * SwtFrontend - shared in-process gameplay capture.
 * Copyright (C) 2026 RedClaw — GPLv3, see COPYING.
 */

package br.com.redclaw.swt.capture

import android.graphics.Bitmap
import com.swordfish.libretrodroid.GLRetroView

/** Host-owned capture facade used by every in-process libretro game. */
class GameCaptureController(private val store: CaptureStore) {
    var isRecording: Boolean = false
        private set

    fun screenshot(
        view: GLRetroView,
        sourceId: String?,
        onResult: (Boolean) -> Unit,
    ) {
        view.captureScreenshot { bitmap ->
            if (bitmap == null) {
                onResult(false)
                return@captureScreenshot
            }
            Thread {
                val saved = runCatching {
                    store.screenshotFile(sourceId).outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                }.getOrDefault(false)
                bitmap.recycle()
                view.post { onResult(saved) }
            }.start()
        }
    }

    fun startRecording(
        view: GLRetroView,
        sourceId: String?,
        includeMicrophone: Boolean = false,
        onResult: (Boolean) -> Unit,
    ) {
        if (isRecording) {
            onResult(true)
            return
        }
        view.startVideoRecording(store.recordingFile(sourceId), includeMicrophone) { started ->
            isRecording = started
            onResult(started)
        }
    }

    fun stopRecording(view: GLRetroView?, onStopped: (() -> Unit)? = null) {
        if (!isRecording || view == null) {
            isRecording = false
            onStopped?.invoke()
            return
        }
        view.stopVideoRecording {
            isRecording = false
            onStopped?.invoke()
        }
    }
}
