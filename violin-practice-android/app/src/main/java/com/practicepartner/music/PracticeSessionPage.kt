package com.practicepartner.music

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import java.io.File

@Composable
fun PracticeSessionPage(
    state: PracticeUiState,
    task: PracticeTask,
    studentName: String,
    createRecordingFile: (String) -> File,
    onRecordingSaved: (RecordingFile) -> Unit,
    onRecordingChanged: (Boolean) -> Unit,
    onCompleteRepetition: () -> Unit,
    onResumeTimer: () -> Unit,
    onOpenUri: (Uri, String) -> Unit,
    onFinish: () -> Unit,
) {
    var savedTake by remember(task.id) { mutableStateOf<RecordingFile?>(null) }
    val completedRepetitions = state.todayProgress.repetitionsFor(task.id)
    val goalMet = state.goalMet(task, state.currentDate)
    val sheetMusicUri = task.sheetMusicUri
    val nextTakeNumber = nextRecordingTakeNumber(
        events = state.todayEvents,
        practiceDate = state.currentDate,
        taskId = task.id,
    )
    val recordingFileName = practiceRecordingFileName(
        practiceDate = state.currentDate,
        studentName = studentName,
        assignmentTitle = task.title,
        takeNumber = nextTakeNumber,
    )

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Forest),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        task.title,
                        color = Paper,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${task.assignmentType.label} · ${state.mode.label}",
                        color = Paper.copy(alpha = 0.82f),
                    )
                    Text(
                        state.progressLabel(task, state.currentDate) + " · " +
                            "${formatDuration(state.activeSessionElapsedSeconds)} this session",
                        color = Paper.copy(alpha = 0.82f),
                    )
                    if (task.sourceType != SourceType.NONE) {
                        Text(
                            "Reference clip ${timeLabel(task.startSeconds)}–" +
                                "${task.endSeconds?.let(::timeLabel) ?: "end"}",
                            color = Paper.copy(alpha = 0.76f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        if (!state.running) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Amber.copy(alpha = 0.18f)),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Practice timer paused", fontWeight = FontWeight.Bold)
                        Text(
                            "The timer pauses when LK Practice leaves the screen.",
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(
                            onClick = onResumeTimer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Resume practice timer")
                        }
                    }
                }
            }
        }

        if (task.coachNote.isNotBlank() || task.whyImportant.isNotBlank() || sheetMusicUri != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Sage),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (task.coachNote.isNotBlank()) {
                            Text("Coach focus", fontWeight = FontWeight.Bold, color = Forest)
                            Text(task.coachNote)
                        }
                        if (task.whyImportant.isNotBlank()) {
                            Text("Why this matters", fontWeight = FontWeight.Bold, color = Forest)
                            Text(task.whyImportant)
                        }
                        if (sheetMusicUri != null) {
                            OutlinedButton(
                                onClick = { onOpenUri(sheetMusicUri.toUri(), "*/*") },
                                enabled = !state.recordingActive,
                            ) {
                                Text("Open sheet music")
                            }
                        }
                    }
                }
            }
        }

        if (state.mode != PracticeMode.PLAY_YOURSELF && task.sourceType != SourceType.NONE) {
            item {
                Text(
                    "TEACHER REFERENCE",
                    color = Forest,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                ReferenceVideoPanel(task)
            }
        }

        if (state.mode != PracticeMode.LISTEN) {
            item {
                Text(
                    "YOUR CAMERA",
                    color = Forest,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                CameraRecorderPanel(
                    recordingFileName = recordingFileName,
                    createRecordingFile = createRecordingFile,
                    onSaved = { recording ->
                        savedTake = recording
                        onRecordingSaved(recording)
                    },
                    onRecordingChanged = onRecordingChanged,
                )
            }
        }

        if (task.goalType == PracticeGoalType.REPETITIONS) {
            item {
                Button(
                    onClick = onCompleteRepetition,
                    enabled = completedRepetitions < task.repetitions && !state.recordingActive,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Forest),
                ) {
                    Text(
                        if (completedRepetitions >= task.repetitions) {
                            "All repetitions complete"
                        } else {
                            "Complete repetition ${completedRepetitions + 1}"
                        },
                    )
                }
            }
        } else {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (goalMet) MintCloud else CloudBlue,
                    ),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(
                        if (goalMet) {
                            "Time goal reached. Nice focused work!"
                        } else {
                            "Keep practicing—the timer is tracking this mission."
                        },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        savedTake?.let { recording ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Paper),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Take saved privately",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "It stays private. A grown-up can watch, share, or delete it from Practice Summary.",
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            recording.fileName,
                            color = Forest,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        OutlinedButton(
                            onClick = { onOpenUri(recording.uri.toUri(), "video/mp4") },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Watch this take")
                        }
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onFinish,
                enabled = !state.recordingActive,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        state.recordingActive -> "Stop recording before finishing"
                        goalMet -> "Finish and reflect"
                        else -> "Finish for now"
                    },
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
fun PracticeReflectionDialog(
    session: PracticeSession,
    onSubmit: (PracticeReflection?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onSubmit(null) },
        title = { Text("How did that practice feel?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    session.assignmentTitle,
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                PracticeReflection.entries.forEach { reflection ->
                    OutlinedButton(
                        onClick = { onSubmit(reflection) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            when (reflection) {
                                PracticeReflection.PROUD -> "😊  ${reflection.kidLabel}"
                                PracticeReflection.MAKING_PROGRESS ->
                                    "🙂  ${reflection.kidLabel}"
                                PracticeReflection.NEED_HELP -> "🤔  ${reflection.kidLabel}"
                            },
                        )
                    }
                }
                Text(
                    "There is no score. This just helps a grown-up know what support would be useful.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { onSubmit(null) }) {
                Text("Skip")
            }
        },
    )
}
