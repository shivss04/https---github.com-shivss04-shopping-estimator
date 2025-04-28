package com.example.shopping_estimator

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException
import androidx.compose.material3.MaterialTheme


class MainActivity : ComponentActivity() {

    private var isRecording by mutableStateOf(false)
    private var audioFilePath by mutableStateOf("")
    private var mediaRecorder: MediaRecorder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("main", "onCreate")
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Button(onClick = {
                            if (isRecording) {
                                stopRecording()
                            } else {
                                startRecording()
                            }
                        }) {
                            Text(if (isRecording) "Stop Recording" else "Start Recording")
                        }
                        if (audioFilePath.isNotEmpty()) {
                            Text(
                                text = "File path: $audioFilePath",
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO_PERMISSION_CODE)
            return
        }

        // Use a subdirectory "Recordings" in internal storage
        val recordingsDir = File(filesDir, "Recordings")
        if (!recordingsDir.exists()) {
            if (!recordingsDir.mkdirs()) {
                Log.e("startRecording", "Failed to create Recordings directory")
                return
            }
        }

        val fileName = "audio_record_${System.currentTimeMillis()}.3gp"
        val audioFile = File(recordingsDir, fileName)
        audioFilePath = audioFile.absolutePath

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setOutputFile(audioFile)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            try {
                prepare()
                start()
                isRecording = true
                Log.d("startRecording", "Recording started to: $audioFilePath")
            } catch (e: IOException) {
                Log.e("startRecording", "Prepare failed: ${e.message}")
                isRecording = false
                audioFilePath = ""
                releaseMediaRecorder() //use the function
            } catch (e: IllegalStateException) {
                Log.e("startRecording", "IllegalStateException: ${e.message}")
                isRecording = false
                audioFilePath = ""
                releaseMediaRecorder()
            }
        }
    }

    private fun stopRecording() {
        mediaRecorder?.apply {
            try {
                stop()
                release()
                isRecording = false
                Log.d("stopRecording", "Recording stopped. File saved at: $audioFilePath")
            } catch (e: IllegalStateException) {
                Log.e("stopRecording", "IllegalStateException: ${e.message}")
                isRecording = false
                audioFilePath = ""
                releaseMediaRecorder()
            } finally{
                releaseMediaRecorder()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("onRequestPermissionsResult", "Audio permission granted")
                startRecording()
            } else {
                Log.e("onRequestPermissionsResult", "Audio permission denied")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseMediaRecorder()
    }

    private fun releaseMediaRecorder() {
        mediaRecorder?.release()
        mediaRecorder = null
    }

    companion object {
        private const val REQUEST_AUDIO_PERMISSION_CODE = 200
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MaterialTheme{
        Greeting("Shivani")
    }
}
