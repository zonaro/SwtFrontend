/*
 * SwtFrontend - host-owned system screen capture service.
 * Copyright (C) 2026 RedClaw — GPLv3, see COPYING.
 */

package br.com.redclaw.swt.capture

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat
import br.com.redclaw.swt.R

/** Records any game/module window after explicit Android MediaProjection consent. */
class CaptureProjectionService : Service() {
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var recorder: MediaRecorder? = null
    private var outputFile: java.io.File? = null
    private var captureThread: HandlerThread? = null
    private var stopping = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() = stopCapture(deleteIncomplete = recorder != null)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopCapture(deleteIncomplete = false)
            ACTION_START -> startCapture(intent)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopCapture(deleteIncomplete = false)
        super.onDestroy()
    }

    private fun startCapture(intent: Intent) {
        if (projection != null) return
        val resultData = intent.intentExtra(EXTRA_RESULT_DATA) ?: run {
            stopSelf()
            return
        }
        val mode = intent.getStringExtra(EXTRA_MODE)
            ?.let { runCatching { CaptureMode.valueOf(it) }.getOrNull() }
            ?: CaptureMode.SCREENSHOT
        val sourceId = intent.getStringExtra(EXTRA_SOURCE_ID)

        startProjectionForeground(mode)
        val manager = getSystemService(MediaProjectionManager::class.java)
        projection = manager.getMediaProjection(
            intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED),
            resultData,
        ).also { it.registerCallback(projectionCallback, Handler(mainLooper)) }

        runCatching {
            if (mode == CaptureMode.RECORDING) startRecording(sourceId) else captureScreenshot(sourceId)
        }.onFailure {
            outputFile?.delete()
            stopCapture(deleteIncomplete = true)
        }
    }

    private fun startProjectionForeground(mode: CaptureMode) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_capture)
            .setContentTitle(getString(R.string.capture_notification_title))
            .setContentText(
                getString(
                    if (mode == CaptureMode.RECORDING) R.string.capture_notification_recording
                    else R.string.capture_notification_screenshot,
                ),
            )
            .setOngoing(mode == CaptureMode.RECORDING)
            .setOnlyAlertOnce(true)
            .apply {
                if (mode == CaptureMode.RECORDING) {
                    addAction(0, getString(R.string.capture_stop), stopPendingIntent())
                }
            }
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startRecording(sourceId: String?) {
        val (width, height, density) = displayMetrics()
        val targetWidth = width.coerceAtLeast(2) and -2
        val targetHeight = height.coerceAtLeast(2) and -2
        outputFile = CaptureStore(this).recordingFile(sourceId)
        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()
        recorder!!.apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoEncodingBitRate(8_000_000)
            setVideoFrameRate(30)
            setVideoSize(targetWidth, targetHeight)
            setOutputFile(outputFile!!.absolutePath)
            prepare()
        }
        virtualDisplay = projection!!.createVirtualDisplay(
            "SwtFrontendRecording",
            targetWidth,
            targetHeight,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            recorder!!.surface,
            null,
            null,
        )
        recorder!!.start()
    }

    private fun captureScreenshot(sourceId: String?) {
        val (width, height, density) = displayMetrics()
        outputFile = CaptureStore(this).screenshotFile(sourceId)
        captureThread = HandlerThread("SwtCaptureScreenshot").also { it.start() }
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).also { reader ->
            reader.setOnImageAvailableListener({ available ->
                val image = available.acquireLatestImage() ?: return@setOnImageAvailableListener
                available.setOnImageAvailableListener(null, null)
                runCatching {
                    val plane = image.planes[0]
                    val rowPadding = plane.rowStride - plane.pixelStride * width
                    val paddedWidth = width + rowPadding / plane.pixelStride
                    val padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
                    padded.copyPixelsFromBuffer(plane.buffer)
                    val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)
                    outputFile!!.outputStream().use {
                        check(cropped.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                    if (cropped !== padded) padded.recycle()
                    cropped.recycle()
                }.onFailure { outputFile?.delete() }
                image.close()
                stopCapture(deleteIncomplete = false)
            }, Handler(captureThread!!.looper))
        }
        virtualDisplay = projection!!.createVirtualDisplay(
            "SwtFrontendScreenshot",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null,
            Handler(captureThread!!.looper),
        )
    }

    @Synchronized
    private fun stopCapture(deleteIncomplete: Boolean) {
        if (stopping) return
        stopping = true
        val activeRecorder = recorder
        recorder = null
        if (activeRecorder != null) {
            val stopped = runCatching { activeRecorder.stop() }.isSuccess
            runCatching { activeRecorder.reset() }
            runCatching { activeRecorder.release() }
            if (!stopped || deleteIncomplete) outputFile?.delete()
        } else if (deleteIncomplete) {
            outputFile?.delete()
        }
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        runCatching { projection?.unregisterCallback(projectionCallback) }
        runCatching { projection?.stop() }
        projection = null
        captureThread?.quitSafely()
        captureThread = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun displayMetrics(): Triple<Int, Int, Int> {
        val metrics = resources.displayMetrics
        return Triple(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
    }

    private fun stopPendingIntent(): PendingIntent = PendingIntent.getService(
        this,
        0,
        Intent(this, CaptureProjectionService::class.java).setAction(ACTION_STOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.capture_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    @Suppress("DEPRECATION")
    private fun Intent.intentExtra(key: String): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, Intent::class.java)
        } else {
            getParcelableExtra(key)
        }

    companion object {
        private const val ACTION_START = "br.com.redclaw.swt.capture.START"
        private const val ACTION_STOP = "br.com.redclaw.swt.capture.STOP"
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_SOURCE_ID = "source_id"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "swt_capture"
        private const val NOTIFICATION_ID = 0x535754

        fun startIntent(
            context: Context,
            mode: CaptureMode,
            sourceId: String?,
            resultCode: Int,
            resultData: Intent,
        ): Intent = Intent(context, CaptureProjectionService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_MODE, mode.name)
            putExtra(EXTRA_SOURCE_ID, sourceId)
            putExtra(EXTRA_RESULT_CODE, resultCode)
            putExtra(EXTRA_RESULT_DATA, resultData)
        }

        fun stopIntent(context: Context): Intent =
            Intent(context, CaptureProjectionService::class.java).setAction(ACTION_STOP)
    }
}
