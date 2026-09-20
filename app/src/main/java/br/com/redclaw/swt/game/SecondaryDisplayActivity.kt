/*
 * SecondaryDisplayActivity.kt
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

import android.app.Presentation
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import br.com.redclaw.swt.theme.AccentManager

/**
 * Hosts an Android [Presentation] on an external display for DS dual-screen output.
 * The Presentation now uses [ComposeView] with [SecondaryScreen] composable.
 */
class SecondaryDisplayActivity : ComponentActivity() {

    companion object {
        private const val TAG = "SwtSecondaryDisplay"
        const val EXTRA_DISPLAY_ID = "display_id"

        fun launch(context: Context, displayId: Int) {
            val intent = Intent(context, SecondaryDisplayActivity::class.java).apply {
                putExtra(EXTRA_DISPLAY_ID, displayId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private var presentation: Presentation? = null
    private var attachedDisplayId: Int = Display.DEFAULT_DISPLAY

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) { /* no-op */ }
        override fun onDisplayRemoved(displayId: Int) {
            if (displayId == attachedDisplayId) {
                Log.i(TAG, "Attached display removed: $displayId")
                finish()
            }
        }
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == attachedDisplayId) {
                val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                if (dm.getDisplay(displayId) == null) {
                    Log.i(TAG, "Attached display became unavailable: $displayId")
                    finish()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(AccentManager.getThemeOverlay(this))
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )

        attachedDisplayId = intent.getIntExtra(EXTRA_DISPLAY_ID, Display.DEFAULT_DISPLAY)

        if (attachedDisplayId == Display.DEFAULT_DISPLAY) {
            Log.e(TAG, "Cannot attach to default display, finishing")
            finish()
            return
        }

        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        dm.registerDisplayListener(displayListener, null)

        val display = dm.getDisplay(attachedDisplayId)
        if (display == null) {
            Log.e(TAG, "Display $attachedDisplayId not found")
            finish()
            return
        }

        try {
            presentation = SecondaryGamePresentation(this, display).apply { show() }
            Log.i(TAG, "Presentation started on display $attachedDisplayId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start presentation", e)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        dm.unregisterDisplayListener(displayListener)
        try {
            presentation?.dismiss()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to dismiss presentation", e)
        }
        presentation = null
    }

    /**
     * Presentation that renders DS bottom-screen info on an external display using ComposeView.
     */
    inner class SecondaryGamePresentation(
        hostActivity: Context,
        display: Display,
    ) : Presentation(hostActivity, display) {

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            val ctx = context ?: return

            val metrics = DisplayMetrics()
            display.getMetrics(metrics)

            val composeView = ComposeView(ctx).apply {
                setContent {
                    SecondaryScreen(
                        coreDisplayName = null,
                        screenWidthPx = metrics.widthPixels,
                        screenHeightPx = metrics.heightPixels,
                    )
                }
            }

            @Suppress("DEPRECATION")
            window?.decorView?.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )

            setContentView(composeView)
        }
    }
}
