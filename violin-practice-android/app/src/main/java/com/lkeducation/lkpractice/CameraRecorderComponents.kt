package com.lkeducation.lkpractice

import android.content.pm.PackageManager
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
internal data class CameraRecorderState(
    val cameraPermissionGranted: Boolean,
    val audioPermissionGranted: Boolean,
    val controller: LifecycleCameraController?,
    val controllerError: String?,
    val isRecording: Boolean,
    val isStarting: Boolean,
    val countdown: Int,
    val recordingMessage: String,
    val recordingFileName: String,
    val beginCountdown: () -> Unit,
    val stopRecording: () -> Unit,
    val requestCameraAndMicrophone: () -> Unit,
    val requestMicrophone: () -> Unit,
) {
    val isBusy: Boolean
        get() = countdown > 0 || isStarting || isRecording
}
@Composable
internal fun rememberCameraRecorderState(
    recordingFileName: String,
    createRecordingFile: (String) -> File,
    onSaved: (RecordingFile) -> Unit,
    onRecordingChanged: (Boolean) -> Unit,
    onRecordingStarted: () -> Unit = {},
    onRecordingStopped: () -> Unit = {},
): CameraRecorderState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val latestCreateRecordingFile = rememberUpdatedState(createRecordingFile)
    val latestOnSaved = rememberUpdatedState(onSaved)
    val latestOnRecordingChanged = rememberUpdatedState(onRecordingChanged)
    val latestOnRecordingStarted = rememberUpdatedState(onRecordingStarted)
    val latestOnRecordingStopped = rememberUpdatedState(onRecordingStopped)
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
    var coordinatedPlaybackActive by remember { mutableStateOf(false) }

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
            }.recoverCatching {
                create(CameraSelector.DEFAULT_BACK_CAMERA)
            }
        }
    }
    val controller = controllerResult.getOrNull()
    val controllerError = controllerResult.exceptionOrNull()?.message

    fun pauseCoordinatedPlayback() {
        if (coordinatedPlaybackActive) {
            coordinatedPlaybackActive = false
            latestOnRecordingStopped.value()
        }
    }

    fun startRecording() {
        val camera = controller
        if (camera == null) {
            latestOnRecordingChanged.value(false)
            return
        }
        isStarting = true
        recordingMessage = "Starting the camera…"
        val file = try {
            latestCreateRecordingFile.value(recordingFileName)
        } catch (error: Exception) {
            isStarting = false
            latestOnRecordingChanged.value(false)
            recordingMessage = error.message ?: "Could not create the recording file."
            return
        }
        val options = FileOutputOptions.Builder(file).build()
        val audioConfig = if (audioPermission) {
            AudioConfig.create(true)
        } else {
            AudioConfig.AUDIO_DISABLED
        }
        try {
            // Start coordinated reference playback at the same point that recording is
            // requested. Waiting for VideoRecordEvent.Start can leave the reference idle
            // when CameraX startup is delayed (or fails before emitting Start).
            coordinatedPlaybackActive = true
            latestOnRecordingStarted.value()
            recording = camera.startRecording(
                options,
                audioConfig,
                ContextCompat.getMainExecutor(context),
            ) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        isStarting = false
                        isRecording = true
                        latestOnRecordingChanged.value(true)
                        recordingMessage = "Recording. Tap Stop when this repetition is finished."
                    }
                    is VideoRecordEvent.Finalize -> {
                        isStarting = false
                        isRecording = false
                        recording = null
                        pauseCoordinatedPlayback()
                        latestOnRecordingChanged.value(false)
                        if (!event.hasError()) {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.files",
                                file,
                            )
                            recordingMessage = "Take saved privately."
                            latestOnSaved.value(
                                RecordingFile(
                                    uri = uri.toString(),
                                    path = file.absolutePath,
                                    fileName = file.name,
                                ),
                            )
                        } else {
                            file.delete()
                            recordingMessage =
                                "The recording could not be saved (error ${event.error})."
                        }
                    }
                }
            }
        } catch (error: Exception) {
            file.delete()
            isStarting = false
            isRecording = false
            recording = null
            pauseCoordinatedPlayback()
            latestOnRecordingChanged.value(false)
            recordingMessage = error.message ?: "Could not start recording."
        }
    }

    fun beginCountdown() {
        if (controller == null || isRecording || isStarting || countdown > 0) return
        countdownJob?.cancel()
        latestOnRecordingChanged.value(true)
        recordingMessage = "Get ready…"
        countdownJob = scope.launch {
            val skipCountdown = shouldSkipRecordingCountdown(
                coroutineContext[MotionDurationScale]?.scaleFactor,
            )
            if (!skipCountdown) {
                for (value in 3 downTo 1) {
                    countdown = value
                    delay(1_000)
                }
            }
            countdown = 0
            startRecording()
        }
    }

    fun stopRecording() {
        pauseCoordinatedPlayback()
        recording?.stop()
    }

    fun cancelCountdownAndRecording(message: String?) {
        val hadCountdown = countdown > 0
        countdownJob?.cancel()
        countdownJob = null
        countdown = 0
        if (hadCountdown) {
            latestOnRecordingChanged.value(false)
            if (message != null) recordingMessage = message
        }
        val activeRecording = recording
        if (activeRecording != null) {
            if (message != null) recordingMessage = message
            pauseCoordinatedPlayback()
            activeRecording.stop()
        }
    }

    DisposableEffect(controller, lifecycleOwner) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                cancelCountdownAndRecording(
                    "The take was stopped because LK Practice left the screen.",
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            cancelCountdownAndRecording(null)
            controller?.unbind()
            latestOnRecordingChanged.value(false)
        }
    }

    return CameraRecorderState(
        cameraPermissionGranted = cameraPermission,
        audioPermissionGranted = audioPermission,
        controller = controller,
        controllerError = controllerError,
        isRecording = isRecording,
        isStarting = isStarting,
        countdown = countdown,
        recordingMessage = recordingMessage,
        recordingFileName = recordingFileName,
        beginCountdown = ::beginCountdown,
        stopRecording = ::stopRecording,
        requestCameraAndMicrophone = {
            permissionRequest.launch(
                arrayOf(
                    android.Manifest.permission.CAMERA,
                    android.Manifest.permission.RECORD_AUDIO,
                ),
            )
        },
        requestMicrophone = {
            permissionRequest.launch(
                arrayOf(android.Manifest.permission.RECORD_AUDIO),
            )
        },
    )
}
@Composable
internal fun CameraPreviewSurface(
    state: CameraRecorderState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF172227)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            !state.cameraPermissionGranted -> Text(
                "Camera access has not been granted.",
                color = Color.White,
            )
            state.controller == null -> Text(
                state.controllerError ?: "No usable camera was found.",
                color = Color.White,
            )
            else -> AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .clearAndSetSemantics {
                        contentDescription = "Camera preview"
                    },
                factory = { viewContext ->
                    PreviewView(viewContext).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        controller = state.controller
                    }
                },
                update = { previewView ->
                    // Practice timer and countdown updates recompose this surface every
                    // second. Reassigning the same controller makes PreviewView detach
                    // and rebind CameraX, which interrupts an active recording with
                    // VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE (error 4).
                    if (previewView.controller !== state.controller) {
                        previewView.controller = state.controller
                    }
                },
            )
        }
        if (state.countdown > 0) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.58f))
                    .clearAndSetSemantics {
                        contentDescription = "Recording starts in ${state.countdown}"
                        liveRegion = LiveRegionMode.Assertive
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    state.countdown.toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.displayLarge,
                )
            }
        }
    }
}

@Composable
internal fun CameraRecorderActions(
    state: CameraRecorderState,
    startLabel: String,
    stopLabel: String,
    showFileName: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        when {
            !state.cameraPermissionGranted -> Button(
                onClick = state.requestCameraAndMicrophone,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Allow camera and microphone")
            }
            state.controller == null -> Text(
                state.controllerError ?: "Camera unavailable.",
                color = Color(0xFF9A3D32),
                style = MaterialTheme.typography.bodySmall,
            )
            state.isRecording -> Button(
                onClick = state.stopRecording,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9A3D32)),
            ) {
                Text(stopLabel)
            }
            state.isStarting -> Button(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Starting camera…")
            }
            state.countdown > 0 -> Button(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Get ready…")
            }
            else -> Button(
                onClick = state.beginCountdown,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(startLabel)
            }
        }

        if (state.recordingMessage.isNotBlank()) {
            Text(
                state.recordingMessage,
                modifier = Modifier.politeLiveRegion(),
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (showFileName) {
            Text(
                "Video name: ${state.recordingFileName}",
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (state.cameraPermissionGranted && !state.audioPermissionGranted) {
            Text(
                "Microphone access is off; this take will be silent.",
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                onClick = state.requestMicrophone,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Allow microphone")
            }
        }
    }
}

@Composable
fun CameraRecorderPanel(
    recordingFileName: String,
    createRecordingFile: (String) -> File,
    onSaved: (RecordingFile) -> Unit,
    onRecordingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val recorder = rememberCameraRecorderState(
        recordingFileName = recordingFileName,
        createRecordingFile = createRecordingFile,
        onSaved = onSaved,
        onRecordingChanged = onRecordingChanged,
    )
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Paper),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            CameraPreviewSurface(
                state = recorder,
                modifier = Modifier.fillMaxWidth().height(230.dp),
            )
            CameraRecorderActions(
                state = recorder,
                startLabel = "Record take",
                stopLabel = "Stop take",
                showFileName = true,
            )
        }
    }
}
