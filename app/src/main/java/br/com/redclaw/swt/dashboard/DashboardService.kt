/*
 *     Copyright (C) 2026 RedClaw
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package br.com.redclaw.swt.dashboard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import br.com.redclaw.swt.R
import br.com.redclaw.swt.views.MainActivity

class DashboardService : Service() {

    private var httpServer: DashboardServer? = null
    private var currentUrl: String = ""

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopServer()
                return START_NOT_STICKY
            }
            else -> {
                startServer()
                return START_STICKY
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopServer()
        activeInstance = null
        super.onDestroy()
    }

    private fun startServer() {
        if (httpServer != null) return

        val settings = SelfHostedPrefs.load(this)
        val port = settings.port

        val ip = getLocalIpAddress() ?: "127.0.0.1"
        SelfHostedPrefs.saveEnabled(this, true)
        enterForeground(buildServerUrl(port))

        var boundPort = port
        for (offset in 0..100) {
            val candidate = (port + offset).coerceIn(1024, 65535)
            try {
                val server = DashboardServer(this)
                server.start(candidate)
                httpServer = server
                boundPort = candidate
                if (candidate != port) {
                    Log.w(TAG, "Port $port busy, started on $candidate")
                    SelfHostedPrefs.save(this, settings.copy(port = candidate))
                }
                break
            } catch (e: Exception) {
                Log.w(TAG, "Port $candidate unavailable, trying next", e)
            }
        }

        val url = buildServerUrl(boundPort)
        currentUrl = url
        updateNotification(url)
        Log.i(TAG, "Dashboard server started on $url")
    }

    private fun stopServer() {
        httpServer?.stop()
        httpServer = null
        SelfHostedPrefs.saveEnabled(this, false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "Dashboard server stopped")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.dashboard_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.dashboard_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun enterForeground(url: String) {
        currentUrl = url
        val notification = buildNotification(url)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification)
            return
        }
        try {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } catch (e: Exception) {
            Log.w(TAG, "Foreground type rejected, falling back", e)
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(url: String): Notification {
        val stopIntent = Intent(this, DashboardService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.dashboard_notif_title))
            .setContentText(getString(R.string.dashboard_notif_active, url))
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.dashboard_notif_stop),
                stopPendingIntent
            )
            .build()
    }

    private fun updateNotification(url: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        try {
            manager.notify(NOTIFICATION_ID, buildNotification(url))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to refresh notification", e)
        }
    }

    companion object {
        private const val CHANNEL_ID = "swt_dashboard"
        private const val NOTIFICATION_ID = 2001
        private const val ACTION_STOP = "br.com.redclaw.swt.dashboard.STOP"
        private const val TAG = "DashboardSvc"

        var activeInstance: DashboardService? = null
            private set

        fun start(context: Context) {
            SelfHostedPrefs.saveEnabled(context, true)
            val intent = Intent(context, DashboardService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            SelfHostedPrefs.saveEnabled(context, false)
            val intent = Intent(context, DashboardService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun isRunning(): Boolean = activeInstance?.httpServer != null
    }
}
