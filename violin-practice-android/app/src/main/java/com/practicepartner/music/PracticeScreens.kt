package com.practicepartner.music

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import java.text.DateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date

data class AssignmentForm(
    val title: String = "",
    val source: String = "",
    val start: String = "",
    val end: String = "",
    val repetitions: String = "3",
    val coachNote: String = "",
    val whyImportant: String = "",
    val sheetMusicUri: String? = null,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayPage(
    state: PracticeUiState,
    onStartTimer: () -> Unit,
    onPauseTimer: () -> Unit,
    onSelectMode: (PracticeMode) -> Unit,
    onPractice: (PracticeTask) -> Unit,
    onToggleCompleted: (PracticeTask) -> Unit,
) {
    val progress = state.todayProgress
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Forest),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(
                        friendlyFullDate(state.currentDate),
                        color = Paper.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "A little practice,\na little progress.",
                        color = Paper,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                formatDuration(state.elapsedSeconds),
                                color = Paper,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (state.running) "Today’s timer is running" else "Today’s active practice",
                                color = Paper.copy(alpha = 0.8f),
                            )
                        }
                        Button(
                            onClick = if (state.running) onPauseTimer else onStartTimer,
                            colors = ButtonDefaults.buttonColors(containerColor = Paper, contentColor = Forest),
                        ) {
                            Icon(
                                if (state.running) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                            )
                            Text(if (state.running) " Pause" else " Start")
                        }
                    }
                }
            }
        }

        item {
            Column {
                Text(
                    "Choose a practice mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(9.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PracticeMode.entries.forEach { option ->
                        FilterChip(
                            selected = state.mode == option,
                            onClick = { onSelectMode(option) },
                            label = { Text(option.label) },
                        )
                    }
                }
            }
        }

        if (state.todayTasks.isEmpty()) {
            item {
                EmptyCard(
                    "No assignments for today",
                    "A parent can create today’s plan in the Assign tab.",
                )
            }
        }

        items(state.todayTasks, key = { it.id }) { task ->
            val completedRepetitions = progress.repetitionsFor(task.id)
            val completed = task.id in progress.completedTaskIds
            Card(
                colors = CardDefaults.cardColors(containerColor = Paper),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                task.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "${timeLabel(task.startSeconds)}–" +
                                    "${task.endSeconds?.let(::timeLabel) ?: "end"} · " +
                                    "$completedRepetitions / ${task.repetitions} repetitions",
                                color = Muted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (completed) {
                            Icon(Icons.Default.CheckCircle, "Completed", tint = Forest)
                        }
                    }
                    if (task.coachNote.isNotBlank()) {
                        Text("Coach focus: ${task.coachNote}", color = Ink)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onPractice(task) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Forest),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text(" Practice")
                        }
                        TextButton(onClick = { onToggleCompleted(task) }) {
                            Text(if (completed) "Reopen" else "Mark done")
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
fun AssignmentPage(
    state: PracticeUiState,
    form: AssignmentForm,
    onFormChanged: (AssignmentForm) -> Unit,
    onPickVideo: () -> Unit,
    onPickSheetMusic: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onAdd: () -> Unit,
    onRemove: (PracticeTask) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Paper),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Build today’s plan",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Assignments are stored for ${friendlyFullDate(state.currentDate)} and reset naturally on the next day.",
                        color = Muted,
                    )
                    OutlinedTextField(
                        value = form.title,
                        onValueChange = { onFormChanged(form.copy(title = it)) },
                        label = { Text("Technique, piece, or passage") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = form.source,
                        onValueChange = { onFormChanged(form.copy(source = it)) },
                        label = { Text("YouTube link or selected device video") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(onClick = onPickVideo, modifier = Modifier.fillMaxWidth()) {
                        Text("Choose reference video from device")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = form.start,
                            onValueChange = { onFormChanged(form.copy(start = it)) },
                            label = { Text("Start seconds") },
                            placeholder = { Text("0") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = form.end,
                            onValueChange = { onFormChanged(form.copy(end = it)) },
                            label = { Text("End seconds") },
                            placeholder = { Text("Video end") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        value = form.repetitions,
                        onValueChange = { onFormChanged(form.copy(repetitions = it)) },
                        label = { Text("Target repetitions (1–20)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = form.coachNote,
                        onValueChange = { onFormChanged(form.copy(coachNote = it)) },
                        label = { Text("Coach focus") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = form.whyImportant,
                        onValueChange = { onFormChanged(form.copy(whyImportant = it)) },
                        label = { Text("Why this matters") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(onClick = onPickSheetMusic, modifier = Modifier.fillMaxWidth()) {
                        Text(if (form.sheetMusicUri == null) "Attach sheet music" else "Replace attached sheet music")
                    }
                    if (form.sheetMusicUri != null) {
                        Text("Sheet music attached.", color = Forest, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                        Text("Add assignment")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                            Text("Import CSV")
                        }
                        OutlinedButton(
                            onClick = onExport,
                            enabled = state.todayTasks.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Export today")
                        }
                    }
                    Text(
                        "CSV now preserves commas, quotation marks, dates, coaching notes, and optional sheet-music URIs.",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        item {
            Text(
                "Today’s assignments · ${state.todayTasks.size}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        items(state.todayTasks, key = { "assignment-${it.id}" }) { task ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Paper),
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(task.title, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${if (task.sourceType == SourceType.LOCAL) "Device video" else "YouTube"} · " +
                                "${task.repetitions} repetitions",
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(onClick = { onRemove(task) }) {
                        Text("Remove", color = Danger)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
fun PracticeSessionPage(
    state: PracticeUiState,
    task: PracticeTask,
    onRecordingSaved: (RecordingFile) -> Unit,
    onRecordingChanged: (Boolean) -> Unit,
    onCompleteRepetition: () -> Unit,
    onOpenUri: (Uri, String) -> Unit,
    onShare: (Uri) -> Unit,
    onDeleteRecording: (RecordingFile) -> Unit,
    onFinish: () -> Unit,
) {
    var savedTake by remember(task.id) { mutableStateOf<RecordingFile?>(null) }
    val completedRepetitions = state.todayProgress.repetitionsFor(task.id)
    val sheetMusicUri = task.sheetMusicUri

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
                        "${state.mode.label} · ${timeLabel(task.startSeconds)}–" +
                            "${task.endSeconds?.let(::timeLabel) ?: "end"}",
                        color = Paper.copy(alpha = 0.82f),
                    )
                    Text(
                        "$completedRepetitions of ${task.repetitions} repetitions · " +
                            "${formatDuration(state.elapsedSeconds)} today",
                        color = Paper.copy(alpha = 0.82f),
                    )
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
                            ) {
                                Text("Open sheet music")
                            }
                        }
                    }
                }
            }
        }

        if (state.mode != PracticeMode.PLAY_YOURSELF) {
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
                    onSaved = { recording ->
                        savedTake = recording
                        onRecordingSaved(recording)
                    },
                    onRecordingChanged = onRecordingChanged,
                )
            }
        }

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
                            "It stays inside Practice Partner until you explicitly share it.",
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onOpenUri(recording.uri.toUri(), "video/mp4") },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Watch")
                            }
                            Button(
                                onClick = { onShare(recording.uri.toUri()) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Share…")
                            }
                        }
                        TextButton(
                            onClick = {
                                onDeleteRecording(recording)
                                savedTake = null
                            },
                        ) {
                            Text("Delete take", color = Danger)
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
                Text(if (state.recordingActive) "Stop recording before finishing" else "Finish practice")
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
fun SummaryPage(
    state: PracticeUiState,
    onOpenUri: (Uri, String) -> Unit,
    onShare: (Uri) -> Unit,
    onDeleteRecording: (PracticeEvent) -> Unit,
) {
    val progress = state.todayProgress
    val taskIds = state.todayTasks.mapTo(mutableSetOf()) { it.id }
    val completedCount = progress.completedTaskIds.count { it in taskIds }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Forest),
                shape = RoundedCornerShape(22.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            friendlyFullDate(state.currentDate).uppercase(),
                            color = Paper.copy(alpha = 0.78f),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            formatDuration(state.elapsedSeconds),
                            color = Paper,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("active practice", color = Paper.copy(alpha = 0.8f))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "$completedCount / ${state.todayTasks.size}",
                            color = Paper,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("assignments complete", color = Paper.copy(alpha = 0.8f))
                    }
                }
            }
        }

        item {
            Text("Assignment progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (state.todayTasks.isEmpty()) {
            item { EmptyCard("No assignments today", "Create a plan to start tracking repetitions.") }
        }
        items(state.todayTasks, key = { "summary-${it.id}" }) { task ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Paper),
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(task.title, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${progress.repetitionsFor(task.id)} / ${task.repetitions} repetitions",
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (task.id in progress.completedTaskIds) {
                        Icon(Icons.Default.CheckCircle, "Completed", tint = Forest)
                    }
                }
            }
        }

        item {
            Text("Today’s practice log", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (state.todayEvents.isEmpty()) {
            item {
                EmptyCard(
                    "No practice logged yet",
                    "Starting a task, completing repetitions, and saving takes will appear here.",
                )
            }
        }
        items(state.todayEvents.reversed(), key = { it.id }) { event ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Paper),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(event.taskTitle, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${event.event} · ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(event.atMillis))}",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (event.videoUri != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onOpenUri(event.videoUri.toUri(), "video/mp4") },
                            ) {
                                Text("Watch")
                            }
                            Button(onClick = { onShare(event.videoUri.toUri()) }) {
                                Text("Share…")
                            }
                            if (event.videoPath != null) {
                                TextButton(onClick = { onDeleteRecording(event) }) {
                                    Text("Delete", color = Danger)
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun EmptyCard(title: String, detail: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Paper),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(detail, color = Muted)
        }
    }
}

fun formatDuration(seconds: Long): String {
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    val remainingSeconds = seconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, remainingSeconds)
    } else {
        "%d:%02d".format(minutes, remainingSeconds)
    }
}

private fun timeLabel(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

private fun friendlyFullDate(date: String): String = runCatching {
    LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
}.getOrDefault(date)
