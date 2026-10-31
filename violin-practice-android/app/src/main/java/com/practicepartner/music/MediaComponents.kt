package com.practicepartner.music

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Environment
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.AudioConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ReferenceVideoPanel(task: PracticeTask, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Paper),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().height(210.dp).background(Color.Black, RoundedCornerShape(12.dp)),
            ) {
                if (task.sourceType == SourceType.LOCAL) {
                    LocalVideoPlayer(task)
                } else {
                    YouTubePlayer(task)
                }
            }
            Text(
                "Play only the assigned clip. Complete a repetition after the assigned work is finished.",
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
// The YouTube embed requires JavaScript. Only a validated 11-character video ID is inserted,
// and local file/content access remains disabled below.
@SuppressLint("SetJavaScriptEnabled")
private fun YouTubePlayer(task: PracticeTask) {
    val videoId = remember(task.source) { youtubeVideoId(task.source) }
    if (videoId == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("This YouTube link could not be opened.", color = Color.White)
        }
        return
    }
    val endParam = task.endSeconds?.let { "&end=$it" }.orEmpty()
    val html = remember(videoId, task.startSeconds, endParam) {
        """
        <!doctype html>
        <html>
          <head>
            <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
          </head>
          <body style="margin:0;background:#000">
            <iframe
              width="100%"
              height="100%"
              src="https://www.youtube.com/embed/$videoId?start=${task.startSeconds}$endParam&playsinline=1&controls=1&rel=0"
              title="Teacher reference"
              frameborder="0"
              allow="encrypted-media; picture-in-picture; fullscreen"
              allowfullscreen>
            </iframe>
          </body>
        </html>
        """.trimIndent()
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                tag = html
                loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            if (webView.tag != html) {
                webView.tag = html
                webView.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null)
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        },
    )
}

@Composable
private fun LocalVideoPlayer(task: PracticeTask) {
    var videoView by remember { mutableStateOf<VideoView?>(null) }
    val clipKey = "${task.source}|${task.startSeconds}|${task.endSeconds}"

    LaunchedEffect(videoView, clipKey) {
        while (isActive) {
            val view = videoView
            val endMillis = task.endSeconds?.times(1_000)
            if (view != null && endMillis != null && view.isPlaying && view.currentPosition >= endMillis) {
                view.pause()
                view.seekTo(task.startSeconds * 1_000)
            }
            delay(100)
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            VideoView(context).apply {
                tag = clipKey
                setMediaController(MediaController(context))
                setVideoURI(task.source.toUri())
                setOnPreparedListener {
                    seekTo(task.startSeconds * 1_000)
                }
                videoView = this
            }
        },
        update = { view ->
            if (view.tag != clipKey) {
                view.tag = clipKey
                view.setVideoURI(task.source.toUri())
                view.setOnPreparedListener { view.seekTo(task.startSeconds * 1_000) }
            }
            videoView = view
        },
        onRelease = { view ->
            if (videoView === view) videoView = null
            view.stopPlayback()
        },
    )
}

@Composable
fun CameraRecorderPanel(
    onSaved: (RecordingFile) -> Unit,
    onRecordingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var cameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var audioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var isRecording by remember { mutableStateOf(false) }
    var isStarting by remember { mutableStateOf(false) }
    var recordingMessage by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var countdown by remember { mutableIntStateOf(0) }
    var countdownJob by remember { mutableStateOf<Job?>(null) }

    val permissionRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        cameraPermission = grants[android.Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        audioPermission = grants[android.Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        recordingMessage = when {
            !cameraPermission -> "Camera access is required to record a practice take."
            !audioPermission -> "Camera is ready. Microphone access is off, so recordings will be silent."
            else -> "Camera and microphone are ready."
        }
    }

    val controllerResult: Result<LifecycleCameraController?> = remember(
        cameraPermission,
        lifecycleOwner,
    ) {
        if (!cameraPermission) {
            Result.success<LifecycleCameraController?>(null)
        } else {
            fun create(selector: CameraSelector): LifecycleCameraController =
                LifecycleCameraController(context).apply {
                    cameraSelector = selector
                    setEnabledUseCases(CameraController.VIDEO_CAPTURE)
                    bindToLifecycle(lifecycleOwner)
                }
            runCatching<LifecycleCameraController?> {
                create(CameraSelector.DEFAULT_FRONT_CAMERA)
            }
                .recoverCatching { create(CameraSelector.DEFAULT_BACK_CAMERA) }
        }
    }
    val controller = controllerResult.getOrNull()
    val controllerError = controllerResult.exceptionOrNull()?.message

    fun startRecording() {
        val camera = controller ?: return
        isStarting = true
        onRecordingChanged(true)
        recordingMessage = "Starting the camera…"
        val externalDirectory = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        val directory = if (externalDirectory != null) {
            File(externalDirectory, "Practice Partner")
        } else {
            File(context.filesDir, "Practice Partner")
        }.apply { mkdirs() }
        val file = File(directory, "PracticePartner-${System.currentTimeMillis()}.mp4")
        val options = FileOutputOptions.Builder(file).build()
        val audioConfig = if (audioPermission) AudioConfig.create(true) else AudioConfig.AUDIO_DISABLED
        try {
            recording = camera.startRecording(
                options,
                audioConfig,
                ContextCompat.getMainExecutor(context),
            ) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        isStarting = false
                        isRecording = true
                        onRecordingChanged(true)
                        recordingMessage = "Recording. Tap Stop take when this repetition is finished."
                    }
                    is VideoRecordEvent.Finalize -> {
                        isStarting = false
                        isRecording = false
                        recording = null
                        onRecordingChanged(false)
                        if (!event.hasError()) {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.files",
                                file,
                            )
                            recordingMessage = "Take saved privately."
                            onSaved(RecordingFile(uri.toString(), file.absolutePath))
                        } else {
                            file.delete()
                            recordingMessage = "The recording could not be saved (error ${event.error})."
                        }
                    }
                }
            }
        } catch (error: Exception) {
            file.delete()
            isStarting = false
            isRecording = false
            recording = null
            onRecordingChanged(false)
            recordingMessage = error.message ?: "Could not start recording."
        }
    }

    fun beginCountdown() {
        if (controller == null || isRecording || isStarting || countdown > 0) return
        countdownJob?.cancel()
        countdownJob = scope.launch {
            for (value in 3 downTo 1) {
                countdown = value
                delay(1_000)
            }
            countdown = 0
            startRecording()
        }
    }

    DisposableEffect(controller, lifecycleOwner) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                countdownJob?.cancel()
                countdown = 0
                recording?.let {
                    recordingMessage = "The take was stopped because Practice Partner left the screen."
                    it.stop()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            countdownJob?.cancel()
            recording?.stop()
            controller?.unbind()
            onRecordingChanged(false)
        }
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Paper),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().height(230.dp)
                    .background(Color(0xFF172227), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    !cameraPermission -> Text(
                        "Camera access has not been granted.",
                        color = Color.White,
                    )
                    controller == null -> Text(
                        controllerError ?: "No usable camera was found.",
                        color = Color.White,
                    )
                    else -> AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { viewContext ->
                            PreviewView(viewContext).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                this.controller = controller
                            }
                        },
                        update = { it.controller = controller },
                    )
                }
                if (countdown > 0) {
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.58f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            countdown.toString(),
                            color = Color.White,
                            style = MaterialTheme.typography.displayLarge,
                        )
                    }
                }
            }

            when {
                !cameraPermission -> Button(
                    onClick = {
                        permissionRequest.launch(
                            arrayOf(
                                android.Manifest.permission.CAMERA,
                                android.Manifest.permission.RECORD_AUDIO,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Allow camera and microphone")
                }
                controller == null -> Text(
                    controllerError ?: "Camera unavailable.",
                    color = Color(0xFF9A3D32),
                    style = MaterialTheme.typography.bodySmall,
                )
                isRecording -> Button(
                    onClick = { recording?.stop() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9A3D32)),
                ) {
                    Text("Stop take")
                }
                isStarting -> Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Starting camera…")
                }
                else -> Button(
                    onClick = ::beginCountdown,
                    enabled = countdown == 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (countdown > 0) "Get ready…" else "Record take")
                }
            }

            if (recordingMessage.isNotBlank()) {
                Text(recordingMessage, color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            if (cameraPermission && !audioPermission) {
                Text(
                    "Microphone access is off; this take will be silent.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    onClick = {
                        permissionRequest.launch(
                            arrayOf(android.Manifest.permission.RECORD_AUDIO),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Allow microphone")
                }
            }
        }
    }
}
