package com.example.backgrounduploader

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import android.Manifest
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import com.example.backgrounduploader.ui.theme.BackgroundUploaderTheme

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU){
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                0
            )
        }

        setContent {
            BackgroundUploaderTheme {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Button(onClick = {
                        Intent(applicationContext,UploadService::class.java).also {
                            it.action = UploadService.Actions.START.toString()
                            startForegroundService(it)
                        }
                    }) {
                        Text(text = "Start Service")
                    }

                    Button(onClick = {
                        Intent(applicationContext,UploadService::class.java).also {
                            it.action = UploadService.Actions.STOP.toString()
                            startForegroundService(it)
                        }
                    }) {
                        Text(text = "Stop Service")
                    }
                }
            }
        }
    }
}

