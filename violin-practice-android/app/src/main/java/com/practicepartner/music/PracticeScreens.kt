package com.practicepartner.music

import android.app.DatePickerDialog
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import java.text.DateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date

data class AssignmentForm(
    val editingTaskId: String? = null,
    val assignedDate: String = currentDateKey(),
    val title: String = "",
    val source: String = "",
    val start: String = "",
    val end: String = "",
    val repetitions: String = "3",
    val coachNote: String = "",
    val whyImportant: String = "",
    val sheetMusicUri: String? = null,
) {
    companion object {
        fun fromTask(task: PracticeTask): AssignmentForm = AssignmentForm(
            editingTaskId = task.id,
            assignedDate = task.assignedDate,
            title = task.title,
            source = task.source,
            start = task.startSeconds.toString(),
            end = task.endSeconds?.toString().orEmpty(),
            repetitions = task.repetitions.toString(),
            coachNote = task.coachNote,
            whyImportant = task.whyImportant,
            sheetMusicUri = task.sheetMusicUri,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayPage(
    state: PracticeUiState,
    studentName: String,
    onSelectMode: (PracticeMode) -> Unit,
    onPractice: (PracticeTask) -> Unit,
    onToggleCompleted: (PracticeTask) -> Unit,
) {
    val progress = state.todayProgress
    val completedCount = state.todayTasks.count { it.id in progress.completedTaskIds }
    val allComplete = state.todayTasks.isNotEmpty() &&
        completedCount == state.todayTasks.size
    val nextTask = state.todayTasks.firstOrNull { it.id !in progress.completedTaskIds }
        ?: state.todayTasks.firstOrNull()

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TodaySkyHero(
                studentName = studentName,
                date = state.currentDate,
                nextTask = nextTask,
                selectedMode = state.mode,
                completedCount = completedCount,
                totalCount = state.todayTasks.size,
                allComplete = allComplete,
                onStart = { nextTask?.let(onPractice) },
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = CloudWhite.copy(alpha = 0.88f),
                ),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "Pick your practice style",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        "You can change this before starting any music mission.",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PracticeMode.entries.forEach { option ->
                            FilterChip(
                                selected = state.mode == option,
                                onClick = { onSelectMode(option) },
                                leadingIcon = {
                                    Text(option.kidEmoji(), fontSize = 16.sp)
                                },
                                label = { Text(option.kidLabel()) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = CloudBlue.copy(alpha = 0.7f),
                                    selectedContainerColor = SunYellow,
                                    selectedLabelColor = Ink,
                                ),
                            )
                        }
                    }
                }
            }
        }

        if (state.todayTasks.isNotEmpty()) {
            item {
                Text(
                    "Today’s music missions",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Ink,
                )
            }
        }

        if (state.todayTasks.isEmpty()) {
            item {
                EmptyCard(
                    "No music missions yet",
                    "Ask a grown-up to add today’s plan from the small settings button.",
                )
            }
        }

        itemsIndexed(state.todayTasks, key = { _, task -> task.id }) { index, task ->
            val completedRepetitions = progress.repetitionsFor(task.id)
            val completed = task.id in progress.completedTaskIds
            val needsReference = task.sourceType == SourceType.LOCAL && task.source.isBlank()
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (completed) MintCloud else CloudWhite,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                shape = RoundedCornerShape(26.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(17.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            color = if (completed) SunYellow else CloudBlue,
                            shape = CircleShape,
                        ) {
                            Box(
                                Modifier.size(44.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (completed) "✓" else "${index + 1}",
                                    color = Ink,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                task.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Text(
                                "${timeLabel(task.startSeconds)}–" +
                                    "${task.endSeconds?.let(::timeLabel) ?: "end"} · " +
                                    "$completedRepetitions / ${task.repetitions} repetitions",
                                color = Muted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    if (task.coachNote.isNotBlank()) {
                        Surface(
                            color = CloudBlue.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text(
                                "Coach tip: ${task.coachNote}",
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                color = Ink,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    if (needsReference) {
                        Text(
                            "Ask a grown-up to reattach this reference video before practice.",
                            color = Danger,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onPractice(task) },
                            enabled = !needsReference,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (completed) DeepSky else SunYellow,
                                contentColor = if (completed) Paper else Ink,
                            ),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text(if (completed) " Play again" else " Start")
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
private fun TodaySkyHero(
    studentName: String,
    date: String,
    nextTask: PracticeTask?,
    selectedMode: PracticeMode,
    completedCount: Int,
    totalCount: Int,
    allComplete: Boolean,
    onStart: () -> Unit,
) {
    val firstName = studentName.trim().substringBefore(' ').ifBlank { "Musician" }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(DeepSky, SkyBlue),
                ),
            )
            .padding(20.dp),
    ) {
        Text(
            "☁",
            modifier = Modifier.align(Alignment.TopEnd),
            color = Paper.copy(alpha = 0.22f),
            fontSize = 72.sp,
        )
        Text(
            "☀",
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 8.dp),
            color = SunYellow,
            fontSize = 34.sp,
        )
        Column(
            Modifier.fillMaxWidth().padding(end = 28.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                "Hi, $firstName!",
                color = Paper,
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Cursive,
                fontWeight = FontWeight.Bold,
            )
            Text(
                friendlyFullDate(date),
                color = Paper.copy(alpha = 0.82f),
                style = MaterialTheme.typography.bodySmall,
            )
            if (nextTask == null) {
                Text(
                    "Your music missions will appear here.",
                    color = Paper,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "A grown-up can add them from the settings cloud.",
                    color = Paper.copy(alpha = 0.84f),
                )
            } else {
                Text(
                    if (allComplete) {
                        "Amazing—today’s missions are complete!"
                    } else {
                        "Your next music mission"
                    },
                    color = Paper.copy(alpha = 0.84f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    nextTask.title,
                    color = Paper,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "${selectedMode.kidEmoji()} ${selectedMode.kidLabel()} · " +
                        "$completedCount of $totalCount finished",
                    color = Paper.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SunYellow,
                        contentColor = Ink,
                    ),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text(
                        if (allComplete) " Play it again" else " Start now",
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

private fun PracticeMode.kidLabel(): String = when (this) {
    PracticeMode.LISTEN -> "Listen first"
    PracticeMode.LISTEN_AND_PLAY -> "Play along"
    PracticeMode.PLAY_YOURSELF -> "Play solo"
}

private fun PracticeMode.kidEmoji(): String = when (this) {
    PracticeMode.LISTEN -> "👂"
    PracticeMode.LISTEN_AND_PLAY -> "🎻"
    PracticeMode.PLAY_YOURSELF -> "⭐"
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
    val context = LocalContext.current
    val selectedDate = runCatching { LocalDate.parse(form.assignedDate) }
        .getOrDefault(LocalDate.now())
    val assignmentsForDate = state.tasksForDate(form.assignedDate)
    var pendingRemoval by remember { mutableStateOf<PracticeTask?>(null) }

    fun showDatePicker() {
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val date = LocalDate.of(year, month + 1, day).toString()
                onFormChanged(form.copy(assignedDate = date))
            },
            selectedDate.year,
            selectedDate.monthValue - 1,
            selectedDate.dayOfMonth,
        ).show()
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
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
                            if (form.editingTaskId == null) {
                                "Build a practice plan"
                            } else {
                                "Edit assignment"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Assignment date: ${friendlyFullDate(form.assignedDate)}",
                            color = Muted,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = ::showDatePicker,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Choose date")
                            }
                            OutlinedButton(
                                onClick = {
                                    onFormChanged(
                                        form.copy(assignedDate = state.currentDate),
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Use today")
                            }
                        }
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
                        Text(
                            "Sheet music (optional)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Attach a PDF or image for Lincoln to open during practice, or leave this blank.",
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(
                            onClick = onPickSheetMusic,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (form.sheetMusicUri == null) {
                                    "Attach sheet music (optional)"
                                } else {
                                    "Replace attached sheet music"
                                },
                            )
                        }
                        if (form.sheetMusicUri != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Sheet music attached and ready.",
                                    color = Forest,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    onClick = {
                                        onFormChanged(form.copy(sheetMusicUri = null))
                                    },
                                ) {
                                    Text("Remove")
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                                Text("Import CSV")
                            }
                            OutlinedButton(
                                onClick = onExport,
                                enabled = assignmentsForDate.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Export date")
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
                    "${friendlyFullDate(form.assignedDate)} · ${assignmentsForDate.size} assignment" +
                        if (assignmentsForDate.size == 1) "" else "s",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(assignmentsForDate, key = { "assignment-${it.id}" }) { task ->
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
                                "${if (task.sourceType == SourceType.LOCAL) {
                                    if (task.source.isBlank()) "Device video needs reattachment" else "Device video"
                                } else {
                                    "YouTube"
                                }} · " +
                                    "${task.repetitions} repetitions",
                                color = Muted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            TextButton(
                                onClick = {
                                    onFormChanged(AssignmentForm.fromTask(task))
                                },
                            ) {
                                Text("Edit")
                            }
                            TextButton(onClick = { pendingRemoval = task }) {
                                Text("Remove", color = Danger)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        Surface(
            color = Paper,
            shadowElevation = 8.dp,
        ) {
            Column(
                Modifier.fillMaxWidth().imePadding().padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Button(
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Forest),
                ) {
                    Text(
                        if (form.editingTaskId == null) {
                            "Save assignment"
                        } else {
                            "Update assignment"
                        },
                    )
                }
                Text(
                    "Saves for ${friendlyFullDate(form.assignedDate)}. Sheet music is optional.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (form.editingTaskId != null) {
                    TextButton(
                        onClick = {
                            onFormChanged(AssignmentForm(assignedDate = form.assignedDate))
                        },
                    ) {
                        Text("Cancel editing")
                    }
                }
            }
        }
    }

    pendingRemoval?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Remove assignment?") },
            text = {
                Text(
                    "This removes “${task.title}” and its recorded completion state. " +
                        "Saved practice videos remain in the practice log.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRemove(task)
                        pendingRemoval = null
                        if (form.editingTaskId == task.id) {
                            onFormChanged(AssignmentForm(assignedDate = form.assignedDate))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Danger),
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
fun PracticeSessionPage(
    state: PracticeUiState,
    task: PracticeTask,
    studentName: String,
    onRecordingSaved: (RecordingFile) -> Unit,
    onRecordingChanged: (Boolean) -> Unit,
    onCompleteRepetition: () -> Unit,
    onResumeTimer: () -> Unit,
    onOpenUri: (Uri, String) -> Unit,
    onShare: (Uri, String?) -> Unit,
    onDeleteRecording: (RecordingFile) -> Boolean,
    onFinish: () -> Unit,
) {
    var savedTake by remember(task.id) { mutableStateOf<RecordingFile?>(null) }
    var pendingDelete by remember(task.id) { mutableStateOf<RecordingFile?>(null) }
    val completedRepetitions = state.todayProgress.repetitionsFor(task.id)
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
                            "The timer pauses when Practice Partner leaves the screen.",
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
                    recordingFileName = recordingFileName,
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
                        Text(
                            recording.fileName,
                            color = Forest,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onOpenUri(recording.uri.toUri(), "video/mp4") },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Watch")
                            }
                            Button(
                                onClick = {
                                    onShare(recording.uri.toUri(), recording.fileName)
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Share…")
                            }
                        }
                        TextButton(
                            onClick = {
                                pendingDelete = recording
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

    pendingDelete?.let { recording ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this take?") },
            text = { Text("The video and its practice-log entry will be permanently removed.") },
            confirmButton = {
                Button(
                    onClick = {
                        if (onDeleteRecording(recording)) {
                            savedTake = null
                            pendingDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Danger),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
fun SummaryPage(
    state: PracticeUiState,
    onPreviousDate: () -> Unit,
    onNextDate: () -> Unit,
    onToday: () -> Unit,
    onOpenUri: (Uri, String) -> Unit,
    onShare: (Uri, String?) -> Unit,
    onDeleteRecording: (PracticeEvent) -> Boolean,
) {
    val progress = state.summaryProgress
    val tasks = state.summaryTasks
    val events = state.summaryEvents
    val taskIds = tasks.mapTo(mutableSetOf()) { it.id }
    val completedCount = progress.completedTaskIds.count { it in taskIds }
    val canGoPrevious = state.historyDates.any { it < state.summaryDate }
    val canGoNext = state.historyDates.any { it > state.summaryDate }
    val displayElapsed = if (state.summaryDate == state.currentDate) {
        state.elapsedSeconds
    } else {
        progress.elapsedSeconds
    }
    var pendingDelete by remember { mutableStateOf<PracticeEvent?>(null) }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onPreviousDate,
                    enabled = canGoPrevious,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Previous")
                }
                OutlinedButton(
                    onClick = onToday,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Today")
                }
                OutlinedButton(
                    onClick = onNextDate,
                    enabled = canGoNext,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Next")
                }
            }
        }
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
                            friendlyFullDate(state.summaryDate).uppercase(),
                            color = Paper.copy(alpha = 0.78f),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            formatDuration(displayElapsed),
                            color = Paper,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("active practice", color = Paper.copy(alpha = 0.8f))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "$completedCount / ${tasks.size}",
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
        if (tasks.isEmpty()) {
            item {
                EmptyCard(
                    "No assignments for this date",
                    "Choose another date or create a plan in Assign.",
                )
            }
        }
        items(tasks, key = { "summary-${it.id}" }) { task ->
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
            Text("Practice log", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (events.isEmpty()) {
            item {
                EmptyCard(
                    "No practice logged yet",
                    "Starting a task, completing repetitions, and saving takes will appear here.",
                )
            }
        }
        items(events.reversed(), key = { it.id }) { event ->
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
                    event.videoFileName?.let { fileName ->
                        Text(
                            fileName,
                            color = Forest,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (event.videoUri != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onOpenUri(event.videoUri.toUri(), "video/mp4") },
                            ) {
                                Text("Watch")
                            }
                            Button(
                                onClick = {
                                    onShare(event.videoUri.toUri(), event.videoFileName)
                                },
                            ) {
                                Text("Share…")
                            }
                            if (event.videoPath != null) {
                                TextButton(onClick = { pendingDelete = event }) {
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

    pendingDelete?.let { event ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this recording?") },
            text = { Text("The video and its practice-log entry will be permanently removed.") },
            confirmButton = {
                Button(
                    onClick = {
                        if (onDeleteRecording(event)) {
                            pendingDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Danger),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            },
        )
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
    LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"))
}.getOrDefault(date)
