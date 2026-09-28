/*
 * SwtFrontend - MediaProjection consent entry point.
 * Copyright (C) 2026 RedClaw — GPLv3, see COPYING.
 */

package br.com.redclaw.swt.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import br.com.redclaw.swt.R

/**
 * Transparent trampoline that requests Android's screen-capture consent. Companion modules never
 * receive the projection token: the SwtFrontend host owns it and the resulting media.
 */
class CaptureProjectionActivity : ComponentActivity() {
    private val projectionManager by lazy {
        getSystemService(MediaProjectionManager::class.java)
    }

    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            Toast.makeText(this, R.string.capture_permission_denied, Toast.LENGTH_SHORT).show()
            finish()
            return@registerForActivityResult
        }
        val serviceIntent = CaptureProjectionService.startIntent(
            context = this,
            mode = intent.captureMode(),
            sourceId = intent.getStringExtra(EXTRA_SOURCE_ID),
            resultCode = result.resultCode,
            resultData = result.data!!,
        )
        ContextCompat.startForegroundService(this, serviceIntent)
        Toast.makeText(
            this,
            if (intent.captureMode() == CaptureMode.RECORDING) {
                R.string.capture_recording_started
            } else {
                R.string.capture_screenshot_requested
            },
            Toast.LENGTH_SHORT,
        ).show()
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) consent.launch(projectionManager.createScreenCaptureIntent())
    }

    companion object {
        private const val EXTRA_MODE = "capture_mode"
        const val EXTRA_SOURCE_ID = "capture_source_id"

        fun intent(context: Context, mode: CaptureMode, sourceId: String? = null): Intent =
            Intent(context, CaptureProjectionActivity::class.java).apply {
                putExtra(EXTRA_MODE, mode.name)
                putExtra(EXTRA_SOURCE_ID, sourceId)
            }

        private fun Intent.captureMode(): CaptureMode =
            when (action) {
                ACTION_RECORDING -> CaptureMode.RECORDING
                ACTION_SCREENSHOT -> CaptureMode.SCREENSHOT
                else -> getStringExtra(EXTRA_MODE)
                    ?.let { runCatching { CaptureMode.valueOf(it) }.getOrNull() }
                    ?: CaptureMode.SCREENSHOT
            }

        const val ACTION_SCREENSHOT = "br.com.redclaw.swt.capture.SCREENSHOT"
        const val ACTION_RECORDING = "br.com.redclaw.swt.capture.RECORDING"
    }
}

enum class CaptureMode { SCREENSHOT, RECORDING }
