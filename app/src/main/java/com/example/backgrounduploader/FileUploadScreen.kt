package com.example.backgrounduploader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun FileUploadScreen() {
    val context = LocalContext.current
    val fileUris = remember { mutableStateListOf<Uri>() }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris?.let {
            fileUris.addAll(it)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Button to open the file picker
        Button(
            onClick = {
                // Allow picking multiple files of any type
                launcher.launch(arrayOf("*/*"))
            }
        ) {
            Text("Select Files")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Show the selected files
        LazyColumn {
            items(fileUris) { uri ->
                Text(text = "File: ${uri.path}", modifier = Modifier.padding(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Button to start upload
        Button(
            onClick = {
                startUploadService(context, fileUris)
            },
            enabled = fileUris.isNotEmpty() // Disable if no files are selected
        ) {
            Text("Start Upload")
        }
    }
}

private fun startUploadService(context: Context, uris: List<Uri>) {
    val fileUris = ArrayList<String>().apply {
        uris.forEach { add(it.toString()) }
    }

    val intent = Intent(context, UploadService::class.java).apply {
        action = UploadService.Actions.START.toString()
        putStringArrayListExtra("file_uris", fileUris)
        putExtra("server_url", "http://182.18.0.47:3000/upload")
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}