package com.smartmanager.ai.camera

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.smartmanager.ai.actions.AppVisibility
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CaptureResult(val uri: String?, val error: String? = null)

/** Hands the saved file from the capture screen back to the action handler so it can verify it. */
object CameraResultBus {
    private val _result = MutableStateFlow<CaptureResult?>(null)
    val result: StateFlow<CaptureResult?> = _result
    fun reset() { _result.value = null }
    fun post(r: CaptureResult) { _result.value = r }
}

/** CameraX capture screen with visible preview (privacy indicators stay on). Voice-triggered captures use a 3-2-1 countdown. */
class CameraCaptureActivity : ComponentActivity() {
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var posted = false

    override fun onStart() { super.onStart(); AppVisibility.started++ }
    override fun onStop() { super.onStop(); AppVisibility.started-- }

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { if (!it) { CameraResultBus.post(CaptureResult(null, "camera permission denied")); finish() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val front = intent.getBooleanExtra(EXTRA_FRONT, false); val video = intent.getBooleanExtra(EXTRA_VIDEO, false); val auto = intent.getBooleanExtra(EXTRA_AUTO, false)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) permLauncher.launch(Manifest.permission.CAMERA)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var countdown by remember { mutableIntStateOf(if (auto) 3 else 0) }
                var isRecording by remember { mutableStateOf(false) }
                var ready by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxSize()) {
                    AndroidView(factory = { ctx -> PreviewView(ctx).also { pv -> bind(pv, front, video) { ready = true } } }, modifier = Modifier.fillMaxSize())
                    if (countdown > 0) Text("$countdown", style = MaterialTheme.typography.displayLarge, modifier = Modifier.align(Alignment.Center))
                    Row(Modifier.align(Alignment.BottomCenter).padding(32.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(onClick = { if (video) { if (isRecording) { recording?.stop() } else { isRecording = true; startVideo() } } else takePhoto() }, enabled = ready) { Text(if (video) (if (isRecording) "■ Stop" else "● Record") else "📷") }
                        OutlinedButton(onClick = { CameraResultBus.post(CaptureResult(null, "cancelled")); finish() }) { Text("✕") }
                    }
                }
                LaunchedEffect(ready) {
                    if (ready && auto) { while (countdown > 0) { delay(1000); countdown-- }; if (video) { isRecording = true; startVideo(); delay(30_000); recording?.stop() } else takePhoto() }
                }
            }
        }
    }

    private fun bind(pv: PreviewView, front: Boolean, video: Boolean, onReady: () -> Unit) {
        val f = ProcessCameraProvider.getInstance(this)
        f.addListener({
            val provider = f.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
            val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            provider.unbindAll()
            try {
                if (video) {
                    videoCapture = VideoCapture.withOutput(Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HD)).build())
                    provider.bindToLifecycle(this, selector, preview, videoCapture)
                } else {
                    imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                    provider.bindToLifecycle(this, selector, preview, imageCapture)
                }
                onReady()
            } catch (e: Exception) { post(CaptureResult(null, "camera unavailable: ${e.message}")); finish() }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    private fun takePhoto() {
        val ic = imageCapture ?: return
        val cv = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, "SM_${stamp()}.jpg"); put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg"); put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/SmartManager") }
        ic.takePicture(ImageCapture.OutputFileOptions.Builder(contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv).build(), ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(o: ImageCapture.OutputFileResults) { post(CaptureResult(o.savedUri?.toString())); finish() }
            override fun onError(e: ImageCaptureException) { post(CaptureResult(null, e.message)); finish() }
        })
    }

    private fun startVideo() {
        val vc = videoCapture ?: return
        val cv = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, "SM_${stamp()}.mp4"); put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4"); put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/SmartManager") }
        val opts = MediaStoreOutputOptions.Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(cv).build()
        val audioOk = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        var pending = vc.output.prepareRecording(this, opts)
        if (audioOk) pending = pending.withAudioEnabled()
        recording = pending.start(ContextCompat.getMainExecutor(this)) { ev ->
            if (ev is VideoRecordEvent.Finalize) { post(if (ev.hasError()) CaptureResult(null, "video error ${ev.error}") else CaptureResult(ev.outputResults.outputUri.toString())); finish() }
        }
    }

    private fun post(r: CaptureResult) { if (!posted) { posted = true; CameraResultBus.post(r) } }
    override fun onDestroy() { super.onDestroy(); post(CaptureResult(null, "closed")) }

    companion object { const val EXTRA_FRONT = "front"; const val EXTRA_VIDEO = "video"; const val EXTRA_AUTO = "auto" }
}
