package com.lkeducation.lkpractice

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File

internal enum class PlayAlongLayout {
    PORTRAIT,
    LANDSCAPE,
}

internal const val PLAY_ALONG_PORTRAIT_MEDIA_HEIGHT_DP = 200

internal fun playAlongLayoutForOrientation(orientation: Int): PlayAlongLayout =
    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
        PlayAlongLayout.LANDSCAPE
    } else {
        PlayAlongLayout.PORTRAIT
    }

internal fun playAlongAccessibilityDescription(layout: PlayAlongLayout): String =
    when (layout) {
        PlayAlongLayout.PORTRAIT ->
            "Play along: teacher reference on top, your camera below. " +
                "Tap Play and Record to start both."
        PlayAlongLayout.LANDSCAPE ->
            "Play along: teacher reference on the left, your camera on the right. " +
                "Tap Play and Record to start both."
    }

@Composable
fun PlayAlongPanel(
    task: PracticeTask,
    recordingFileName: String,
    createRecordingFile: (String) -> File,
    onSaved: (RecordingFile) -> Unit,
    onRecordingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = playAlongLayoutForOrientation(LocalConfiguration.current.orientation)
    val playbackController = remember(
        task.id,
        task.source,
        task.startSeconds,
        task.endSeconds,
    ) {
        ReferencePlaybackController()
    }
    val recorder = rememberCameraRecorderState(
        recordingFileName = recordingFileName,
        createRecordingFile = createRecordingFile,
        onSaved = onSaved,
        onRecordingChanged = onRecordingChanged,
        onRecordingStarted = playbackController::playFromStart,
        onRecordingStopped = playbackController::pause,
    )

    Card(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = playAlongAccessibilityDescription(layout)
        },
        colors = CardDefaults.cardColors(containerColor = Paper),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when (layout) {
                PlayAlongLayout.PORTRAIT -> {
                    LabeledMediaSurface(
                        label = "TEACHER REFERENCE",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(PLAY_ALONG_PORTRAIT_MEDIA_HEIGHT_DP.dp),
                    ) {
                        ReferenceVideoSurface(
                            task = task,
                            playbackController = playbackController,
                            interactive = !recorder.isBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(PLAY_ALONG_PORTRAIT_MEDIA_HEIGHT_DP.dp),
                        )
                    }
                    LabeledMediaSurface(
                        label = "YOUR CAMERA",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(PLAY_ALONG_PORTRAIT_MEDIA_HEIGHT_DP.dp),
                    ) {
                        CameraPreviewSurface(
                            state = recorder,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(PLAY_ALONG_PORTRAIT_MEDIA_HEIGHT_DP.dp),
                        )
                    }
                }
                PlayAlongLayout.LANDSCAPE -> {
                    Row(
                        Modifier.fillMaxWidth().height(220.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LabeledMediaSurface(
                            label = "TEACHER REFERENCE",
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        ) {
                            ReferenceVideoSurface(
                                task = task,
                                playbackController = playbackController,
                                interactive = !recorder.isBusy,
                                modifier = Modifier.fillMaxHeight().fillMaxWidth(),
                            )
                        }
                        LabeledMediaSurface(
                            label = "YOUR CAMERA",
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        ) {
                            CameraPreviewSurface(
                                state = recorder,
                                modifier = Modifier.fillMaxHeight().fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            CameraRecorderActions(
                state = recorder,
                startLabel = "Play & Record",
                stopLabel = "Stop",
                showFileName = false,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun LabeledMediaSurface(
    label: String,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier) {
        content()
        Text(
            label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(
                    Color.Black.copy(alpha = 0.66f),
                    RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 7.dp, vertical = 4.dp)
                .hideFromAccessibility(),
        )
    }
}
