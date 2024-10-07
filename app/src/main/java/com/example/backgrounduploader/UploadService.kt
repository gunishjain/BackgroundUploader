package com.example.backgrounduploader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.ArrayList
import java.util.UUID

class UploadService : Service() {

    private companion object {
        const val CHANNEL_ID = "UploadServiceChannel"
        const val CHANNEL_NAME = "File Upload Service"
        const val SUMMARY_NOTIFICATION_ID = 1
    }


    private val workManager by lazy { WorkManager.getInstance(applicationContext) }
    private lateinit var notificationManager: NotificationManager
    private val activeNotifications = mutableMapOf<UUID, Int>()
    private var nextNotificationId = SUMMARY_NOTIFICATION_ID + 1


    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createSummaryNotification(message: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("File Upload Progress")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setGroup("upload_group")
            .setGroupSummary(true)
            .build()
    }

    private fun createNotification(
        message: String,
        progress: Int,
        currentFile: Int = 0,
        totalFiles: Int = 0
    ): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("File Upload")
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setOnlyAlertOnce(true)

        if (currentFile > 0 && totalFiles > 0) {
            builder.setContentText("$message ($currentFile of $totalFiles)")
        } else {
            builder.setContentText(message)
        }

        if (progress > 0) {
            builder.setProgress(100, progress, false)
        }

        return builder.build()
    }

    private fun updateNotification(notificationId: Int, message: String, progress: Int) {
        val notification = createNotification(message, progress)
        notificationManager.notify(notificationId, notification)
    }

    private fun updateSummaryNotification(totalFiles: Int, completed: Boolean = false) {
        val completedFiles = totalFiles - activeNotifications.size
        val summaryText = when {
            completed -> "All $totalFiles files uploaded successfully"
            activeNotifications.isEmpty() -> "Upload failed"
            else -> "Uploading files ($completedFiles of $totalFiles completed)"
        }
        val summaryNotification = createSummaryNotification(summaryText)
        notificationManager.notify(SUMMARY_NOTIFICATION_ID, summaryNotification)
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

        val summaryNotification = createSummaryNotification("Preparing uploads...")
        startForeground(SUMMARY_NOTIFICATION_ID,summaryNotification)


        val fileUris: ArrayList<String>? = intent.getStringArrayListExtra("file_uris")
        val serverUrl = intent.getStringExtra("server_url") ?: return


        if (fileUris.isNullOrEmpty()) {
            stopSelf()
            return
        }

        enqueueUploads(fileUris, serverUrl)

    }

    private fun enqueueUploads(fileUris: ArrayList<String>, serverUrl: String) {
        val uploadWorkRequests = fileUris.mapIndexed { index, fileUri ->
            OneTimeWorkRequestBuilder<FileUploadWorker>()
                .setInputData(workDataOf(
                    "file_uri" to fileUri,
                    "server_url" to serverUrl,
                    "file_index" to index,
                    "total_files" to fileUris.size
                ))
                .setConstraints(
                    Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build())
                .build()
        }

        uploadWorkRequests.forEach { workRequest ->
            val notificationId = nextNotificationId++
            activeNotifications[workRequest.id] = notificationId
            workManager.getWorkInfoByIdLiveData(workRequest.id)
                .observeForever { workInfo ->
                    handleWorkInfoUpdate(workInfo,notificationId)
                }
        }

        workManager.beginWith(uploadWorkRequests).enqueue()
    }

    private fun handleWorkInfoUpdate(workInfo: WorkInfo?, notificationId: Int) {
        workInfo?.let {
            when (it.state) {
                WorkInfo.State.RUNNING -> {
                    val fileName = it.progress.getString("file_name") ?: "Unknown file"
                    val progress = it.progress.getInt("progress", 0)
                    val fileIndex = it.progress.getInt("file_index", 0)
                    val totalFiles = it.progress.getInt("total_files", 1)

                    updateNotification(notificationId, fileName, progress)
                    updateSummaryNotification(totalFiles)
                }
                WorkInfo.State.SUCCEEDED -> {
                    val fileName = it.outputData.getString("file_name") ?: "Unknown file"
                    updateNotification(notificationId, "$fileName - Completed", 100)
                    activeNotifications.remove(it.id)

                    if (activeNotifications.isEmpty()) {
                        val totalFiles = it.outputData.getInt("total_files", 1)
                        updateSummaryNotification(totalFiles, true)
                        stopSelf()
                    } else {
                        val totalFiles = it.outputData.getInt("total_files", 1)
                        updateSummaryNotification(totalFiles)
                    }
                }
                WorkInfo.State.FAILED -> {
                    val fileName = it.outputData.getString("file_name") ?: "Unknown file"
                    updateNotification(notificationId, "$fileName - Failed", -1)
                    activeNotifications.remove(it.id)

                    if (activeNotifications.isEmpty()) {
                        stopSelf()
                    }
                }
                else -> {}
            }
        }
    }





    override fun onBind(intent: Intent?): IBinder? {
        return null
    }


}