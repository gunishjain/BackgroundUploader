package com.example.backgrounduploader

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import okhttp3.OkHttpClient
import java.util.ArrayList
import java.util.concurrent.TimeUnit

class UploadService : Service() {

    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "UploadServiceChannel"


    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        when(intent?.action) {
            Actions.START.toString() -> start(intent)
            Actions.STOP.toString() -> stopSelf()
        }

        return START_NOT_STICKY
    }


    enum class Actions {
        START,STOP
    }

    private fun start(intent : Intent) {

        val notification = createNotification("Upload starting...")
        startForeground(NOTIFICATION_ID,notification)


        val fileUris: ArrayList<String>? = intent?.getStringArrayListExtra("file_uris")
        val serverUrl = "http://182.18.0.47:3000/upload"


        fileUris!!.forEach { fileUri ->
            val uploadWorkRequest = OneTimeWorkRequestBuilder<FileUploadWorker>()
                .setInputData(workDataOf(
                    "file_uri" to fileUri,
                    "server_url" to serverUrl
                ))
                .build()

            WorkManager.getInstance(this).enqueue(uploadWorkRequest)
        }



    }

    private fun createNotification(message: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("File Upload")
            .setContentText(message)
            .setSmallIcon(androidx.core.R.drawable.ic_call_answer)
            .build()
    }

}