package com.example.comfyui_remote.service

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
import androidx.core.app.NotificationCompat
import com.example.comfyui_remote.ComfyApplication
import com.example.comfyui_remote.MainActivity
import com.example.comfyui_remote.R
import com.example.comfyui_remote.network.WebSocketState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ExecutionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val CHANNEL_ID = "comfy_connection_channel"
    private val NOTIFICATION_ID = 1

    // Phase 99: model downloads on the server (comfyui_remote_helper)
    private val DOWNLOADS_CHANNEL_ID = "model_downloads"
    private val DOWNLOAD_PROGRESS_ID = 2
    private val DOWNLOADS_FINISHED_ID = 3
    private var downloadsJob: kotlinx.coroutines.Job? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as ComfyApplication
        
        // Observe connection state
        scope.launch {
            app.connectionRepository.connectionState.collect { state ->
                updateNotification(state)
                
                // Optional: Stop service if disconnected? 
                // For now, we likely want to keep it running if the USER requested connection, 
                // even if it temporarily drops (Connecting/Error).
                // But if explicitly DISCONNECTED, we might stop. 
                // However, the ViewModel usually triggers start/stop of service.
            }
        }
        
        // onStartCommand runs again on every connect; watch the download queue once
        if (downloadsJob?.isActive != true) {
            downloadsJob = scope.launch { watchModelDownloads(app) }
        }

        // Start immediately with current state
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification(WebSocketState.CONNECTING), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification(WebSocketState.CONNECTING))
        }
        
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    /**
     * Progress while the server downloads models, then one summary when the queue goes idle. A batch is every
     * job seen since the queue was last idle.
     */
    private suspend fun watchModelDownloads(app: ComfyApplication) {
        val manager = androidx.core.app.NotificationManagerCompat.from(this)
        val batch = LinkedHashMap<String, com.example.comfyui_remote.domain.ModelDownload>()
        var wasActive = false
        app.modelDownloadRepository.downloads.collect { list ->
            val active = list.filter { it.active }
            if (active.isNotEmpty()) {
                active.forEach { d -> d.id?.let { batch[it] = d } }
                list.filter { it.finished && it.id in batch }.forEach { batch[it.id!!] = it }
                val finishedInBatch = batch.values.count { it.finished }
                val summary = com.example.comfyui_remote.domain.ModelDownloads.summary(list, finishedInBatch)
                if (summary != null && manager.areNotificationsEnabled()) {
                    notifySafely(manager, DOWNLOAD_PROGRESS_ID, NotificationCompat.Builder(this, DOWNLOADS_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentTitle(summary.title)
                        .setContentText(summary.text)
                        .setProgress(100, summary.percent ?: 0, summary.percent == null)
                        .setContentIntent(openAppIntent())
                        .setOngoing(true)
                        .setOnlyAlertOnce(true)
                        .build())
                }
                wasActive = true
            } else if (wasActive) {
                list.filter { it.finished && it.id in batch }.forEach { batch[it.id!!] = it }
                manager.cancel(DOWNLOAD_PROGRESS_ID)
                if (batch.isNotEmpty() && manager.areNotificationsEnabled()) {
                    notifySafely(manager, DOWNLOADS_FINISHED_ID, NotificationCompat.Builder(this, DOWNLOADS_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentTitle("Model downloads finished")
                        .setContentText(com.example.comfyui_remote.domain.ModelDownloads.finishedSummary(batch.values.toList()))
                        .setContentIntent(openAppIntent())
                        .setAutoCancel(true)
                        .build())
                }
                batch.clear()
                wasActive = false
            }
        }
    }

    private fun notifySafely(manager: androidx.core.app.NotificationManagerCompat, id: Int, notification: Notification) {
        try {
            manager.notify(id, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted (Android 13+)
        }
    }

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    private fun updateNotification(state: WebSocketState) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun buildNotification(state: WebSocketState): Notification {
        val contentText = when (state) {
            WebSocketState.CONNECTED -> "Connected to ComfyUI"
            WebSocketState.CONNECTING -> "Connecting..."
            WebSocketState.DISCONNECTED -> "Disconnected"
            WebSocketState.ERROR -> "Connection Error"
            WebSocketState.RECONNECTING -> "Reconnecting..."
        }

        val pendingIntent: PendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ComfyUI Remote")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Ensure this resource exists or use default
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        // Android 12+ requires stating foreground service type in manifest, but for general compatibility:
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
             // Foreground service type is defined in manifest
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Connection Status"
            val descriptionText = "Shows ComfyUI connection status"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            notificationManager.createNotificationChannel(
                NotificationChannel(DOWNLOADS_CHANNEL_ID, "Model downloads", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Progress of model downloads on the ComfyUI server"
                }
            )
        }
    }
}
