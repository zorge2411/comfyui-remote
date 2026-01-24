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
        }
    }
}
