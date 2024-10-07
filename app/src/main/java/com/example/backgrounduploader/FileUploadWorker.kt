package com.example.backgrounduploader

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.Sink
import okio.buffer
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class FileUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context,params) {


    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()


    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {

        try {
            val fileUri = inputData.getString("file_uri") ?: return@withContext Result.failure()
            val serverUrl = inputData.getString("server_url") ?: return@withContext Result.failure()
            val fileIndex = inputData.getInt("file_index", 0)
            val totalFiles = inputData.getInt("total_files", 1)

            val uri = Uri.parse(fileUri)
            val fileName = getFileName(applicationContext, uri)
            val inputStream = applicationContext.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure()

            setProgress(workDataOf(
                "file_name" to fileName,
                "file_index" to fileIndex,
                "total_files" to totalFiles,
                "progress" to 0
            ))

            val success = uploadFile(inputStream, fileName, serverUrl, fileIndex, totalFiles)

            if (success) {
                Result.success(workDataOf(
                    "file_name" to fileName,
                    "file_index" to fileIndex,
                    "total_files" to totalFiles
                ))
            } else {
                Result.failure(workDataOf(
                    "file_name" to fileName
                ))
            }
        } catch (e: Exception) {
            Result.failure()
        }
    }


    private suspend fun uploadFile(
        inputStream: InputStream,
        fileName: String,
        serverUrl: String,
        fileIndex: Int,
        totalFiles: Int
    ): Boolean {
        return suspendCancellableCoroutine { continuation ->
            val progressListener = object : ProgressListener {
                override fun onProgress(progress: Int) {
                    runBlocking {
                        setProgress(workDataOf(
                            "progress" to progress,
                            "file_name" to fileName,
                            "file_index" to fileIndex,
                            "total_files" to totalFiles
                        ))
                    }
                }
            }

            // Create a temporary file to get the content length
            val tempFile = File.createTempFile("upload", null, applicationContext.cacheDir)
            tempFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    fileName,
                    object : RequestBody() {
                        override fun contentType(): MediaType? =
                            "application/octet-stream".toMediaTypeOrNull()

                        override fun contentLength(): Long = tempFile.length()

                        override fun writeTo(sink: BufferedSink) {
                            var bytesWritten = 0L
                            val fileLength = contentLength()

                            tempFile.inputStream().use { input ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                var bytes = input.read(buffer)

                                while (bytes >= 0) {
                                    sink.write(buffer, 0, bytes)
                                    bytesWritten += bytes

                                    val progress = ((bytesWritten * 100) / fileLength).toInt()
                                    progressListener.onProgress(progress)

                                    bytes = input.read(buffer)
                                }
                            }
                        }
                    }
                )
                .build()

            val request = Request.Builder()
                .url(serverUrl)
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    tempFile.delete()
                    continuation.resume(false)
                }

                override fun onResponse(call: Call, response: Response) {
                    tempFile.delete()
                    continuation.resume(response.isSuccessful)
                }
            })
        }
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 8 * 1024 // 8 KB
    }

    interface ProgressListener {
        fun onProgress(progress: Int)
    }


    @SuppressLint("Range")
    private fun getFileName(context: Context, uri: Uri): String {
        // Try to get the file name from the content resolver
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val displayName = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                if (!displayName.isNullOrBlank()) {
                    return displayName
                }
            }
        }

        // If content resolver doesn't provide a name, try to get it from the last segment of the path
        uri.lastPathSegment?.let { lastSegment ->
            if (lastSegment.isNotBlank()) {
                return lastSegment
            }
        }

        // If all else fails, generate a unique name
        return "file_${System.currentTimeMillis()}"
    }


}

