package com.example.backgrounduploader

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class UploadService : Service() {

    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "UploadServiceChannel"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        when(intent?.action) {
            Actions.START.toString() -> start()
            Actions.STOP.toString() -> stopSelf()
        }

        return START_STICKY
    }


    enum class Actions {
        START,STOP
    }

    private fun start() {

        val notification = createNotification("Upload starting...")
        startForeground(NOTIFICATION_ID,notification)

    }

    private fun createNotification(message: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("File Upload")
            .setContentText(message)
            .setSmallIcon(androidx.core.R.drawable.ic_call_answer)
            .build()
    }

}