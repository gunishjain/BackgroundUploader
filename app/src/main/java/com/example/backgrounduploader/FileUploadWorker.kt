package com.example.backgrounduploader

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class FileUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context,params) {


    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()


    override suspend fun doWork(): Result {

        val fileUri = inputData.getString("file_uri") ?: return Result.failure()
        val serverUrl = inputData.getString("server_url") ?: return Result.failure()

        val success = uploadFile(applicationContext, fileUri, serverUrl)
        return if (success) {
            Result.success()
        } else {
            Result.retry()  // Option to retry if it fails
        }

    }

    private suspend fun uploadFile(context: Context, fileUri: String, serverUrl: String): Boolean {

        val uri = Uri.parse(fileUri)
        val file = getFileFromUri(context, uri)

        if (file == null || !file.exists()) {
            return false
        }

        // Prepare the request body (Multipart)
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",  // Form field name
                file.name,  // File name
                file.asRequestBody("application/octet-stream".toMediaTypeOrNull())  // File body
            )
            .build()

        // Create the request
        val request = Request.Builder()
            .url(serverUrl)
            .post(requestBody)
            .build()

        // Execute the request and handle the response
        return try {
            val response = client.newCall(request).execute()
            response.isSuccessful // Return true if the upload was successful
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    private fun getFileFromUri(context: Context, uri: Uri): File? {
        var file: File? = null

        // Try to get the file path from the Uri
        if (uri.scheme == "file") {
            file = File(uri.path!!)
        } else if (uri.scheme == "content") {
            // Handle content URIs (e.g., when the user selects a file from gallery or file manager)
            try {
                val fileName = getFileName(context, uri)
                val inputStream = context.contentResolver.openInputStream(uri)
                val tempFile = File.createTempFile(fileName, null, context.cacheDir)
                tempFile.outputStream().use { output ->
                    inputStream?.copyTo(output)
                }
                file = tempFile
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return file
    }

    /**
     * Helper function to get file name from a content Uri.
     */
    @SuppressLint("Range")
    private fun getFileName(context: Context, uri: Uri): String {
        var fileName = "temp_file"
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                fileName = it.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME))
            }
        }
        return fileName
    }




}