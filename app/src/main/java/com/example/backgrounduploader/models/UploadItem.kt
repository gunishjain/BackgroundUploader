package com.example.backgrounduploader.models

import android.net.Uri
import java.util.UUID

data class UploadItem(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val uri: Uri,
    val progress: Float = 0f,
    val status: UploadStatus = UploadStatus.QUEUED
)

enum class UploadStatus {
    QUEUED, UPLOADING, COMPLETED, FAILED
}