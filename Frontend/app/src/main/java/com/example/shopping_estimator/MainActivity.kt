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
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.PutObjectRequest
import java.io.ByteArrayInputStream
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.model.CannedAccessControlList
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder

class MainActivity : ComponentActivity() {

    private var isRecording by mutableStateOf(false)
    private var audioFilePath by mutableStateOf("")
    private var mediaRecorder: MediaRecorder? = null
    private lateinit var s3Client: AmazonS3Client
    private val AWS_S3_BUCKET_NAME = "s3-bucket-name"
    private val lambdaClient = OkHttpClient()
    private val YOUR_API_ENDPOINT = "YOUR_API_ENDPOINT"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val awsCredentials = BasicAWSCredentials(
            "YOUR_AWS_ACCESS_KEY_ID",
            "YOUR_AWS_SECRET_ACCESS_KEY"
        )
        s3Client = AmazonS3Client(awsCredentials)
        s3Client.setRegion(Region.getRegion(Regions.US_EAST_1))
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
                                text = "File path temporarily is: $audioFilePath",
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

        val fileName = "audio_record_${System.currentTimeMillis()}.mp3"
        // Cache the audiofile
        val audioFile = File(cacheDir, fileName)
        audioFilePath = audioFile.absolutePath

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(audioFile.absolutePath)
            setAudioEncoder(MediaRecorder.AudioEncoder.HE_AAC)
            try {
                prepare()
                start()
                isRecording = true
                Log.d("startRecording", "Recording started")
            } catch (e: IOException) {
                Log.e("startRecording", "Prepare failed: ${e.message}")
                isRecording = false
                audioFilePath = ""
                releaseMediaRecorder()
            } catch (e: IllegalStateException) {
                Log.e("startRecording", "IllegalStateException: ${e.message}")
                isRecording = false
                audioFilePath = ""
                releaseMediaRecorder()
            }
        }
    }

    private fun uploadToS3(file: File, key: String) {
        Thread {
            try {
                val request = PutObjectRequest(AWS_S3_BUCKET_NAME, key, file)
                    .withCannedAcl(CannedAccessControlList.PublicRead)
                s3Client.putObject(request)
                Log.d("uploadToS3", "Uploaded to S3: $key")
                // Delete temp file
                file.delete()
            } catch (e: Exception) {
                Log.e("uploadToS3", "Error uploading to S3: ${e.message}")
            }
        }.start()
    }

    private fun invokeLambdaFunction(fileName: String) {
        val lambdaEndpoint =
            "${YOUR_API_ENDPOINT}?filename=${fileName}"
        val request = Request.Builder()
            .url(lambdaEndpoint)
            .build()

        lambdaClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Log.e("Lambda", "Failed to invoke Lambda: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful) {
                        Log.d("Lambda", "Lambda response: $responseBody")
                    } else {
                        Log.e("Lambda", "Lambda error: ${response.code} - $responseBody")
                    }
                    response.close()
                }
            }
        })
    }

    private fun stopRecording() {
        mediaRecorder?.apply {
            try {
                stop()
                release()
                isRecording = false
                Log.d("stopRecording", "Recording stopped")

                val audioFile = File(audioFilePath)
                if (audioFile.exists()) {
                    val s3Key = "audio_record_${System.currentTimeMillis()}.mp3"
                    uploadToS3(audioFile, s3Key)
                    invokeLambdaFunction(s3Key)
                } else {
                    Log.e("stopRecording", "Audio file does not exist: $audioFilePath")
                }
                audioFilePath = ""

            } catch (e: IllegalStateException) {
                Log.e("stopRecording", "IllegalStateException: ${e.message}")
                isRecording = false
                audioFilePath = ""
                releaseMediaRecorder()
            } finally {
                releaseMediaRecorder()
            }
        }
    }

    //Function to ask for permissions. Doesn't pop up once granted
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
    MaterialTheme {
        Greeting("Shivani")
    }
}
