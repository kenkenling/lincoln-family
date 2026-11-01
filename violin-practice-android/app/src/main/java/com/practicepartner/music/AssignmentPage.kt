package com.practicepartner.music

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate

data class AssignmentForm(
    val editingTaskId: String? = null,
    val assignedDate: String = currentDateKey(),
    val title: String = "",
    val source: String = "",
    val start: String = "",
    val end: String = "",
    val repetitions: String = "3",
    val targetMinutes: String = "10",
    val coachNote: String = "",
    val whyImportant: String = "",
    val sheetMusicUri: String? = null,
    val assignmentType: AssignmentType = AssignmentType.TECHNIQUE,
    val goalType: PracticeGoalType = PracticeGoalType.REPETITIONS,
    val enabledModes: Set<PracticeMode> = setOf(PracticeMode.PLAY_YOURSELF),
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
            targetMinutes = (task.targetSeconds / 60).coerceAtLeast(1).toString(),
            coachNote = task.coachNote,
            whyImportant = task.whyImportant,
            sheetMusicUri = task.sheetMusicUri,
            assignmentType = task.assignmentType,
            goalType = task.goalType,
            enabledModes = task.enabledModes,
        )
    }

    fun applyTemplate(template: AssignmentTemplate): AssignmentForm = copy(
        editingTaskId = null,
        title = template.title,
        repetitions = template.repetitions.toString(),
        targetMinutes = (template.targetSeconds / 60).coerceAtLeast(1).toString(),
        coachNote = template.coachNote,
        assignmentType = template.assignmentType,
        goalType = template.goalType,
        enabledModes = template.enabledModes,
    )
}

@Composable
fun AssignmentPage(
    state: PracticeUiState,
    studentName: String,
    instrument: String,
    form: AssignmentForm,
    onFormChanged: (AssignmentForm) -> Unit,
    onPickVideo: () -> Unit,
    onPickSheetMusic: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onAdd: () -> Unit,
    onArchive: (PracticeTask) -> Unit,
    onRestore: (PracticeTask) -> Unit,
    onRemove: (PracticeTask) -> Unit,
) {
    val context = LocalContext.current
    val selectedDate = runCatching { LocalDate.parse(form.assignedDate) }
        .getOrDefault(LocalDate.now())
    val assignmentsForDate = state.tasksForDate(form.assignedDate)
    val archivedAssignmentsForDate = state.archivedTasksForDate(form.assignedDate)
    val templates = assignmentTemplatesFor(instrument)
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
                AssignmentEditorCard(
                    studentName = studentName,
                    instrument = instrument,
                    currentDate = state.currentDate,
                    form = form,
                    templates = templates,
                    hasAssignmentsForDate = assignmentsForDate.isNotEmpty(),
                    onFormChanged = onFormChanged,
                    onChooseDate = ::showDatePicker,
                    onPickVideo = onPickVideo,
                    onPickSheetMusic = onPickSheetMusic,
                    onImport = onImport,
                    onExport = onExport,
                )
            }

            item {
                Text(
                    "${friendlyFullDate(form.assignedDate)} · " +
                        "${assignmentsForDate.size} assignment" +
                        if (assignmentsForDate.size == 1) "" else "s",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(assignmentsForDate, key = { "assignment-${it.id}" }) { task ->
                AssignmentRow(
                    task = task,
                    onEdit = { onFormChanged(AssignmentForm.fromTask(task)) },
                    onArchive = { onArchive(task) },
                    onRemove = { pendingRemoval = task },
                )
            }
            if (archivedAssignmentsForDate.isNotEmpty()) {
                item {
                    Text(
                        "Archived · ${archivedAssignmentsForDate.size}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(
                    archivedAssignmentsForDate,
                    key = { "archived-assignment-${it.id}" },
                ) { task ->
                    ArchivedAssignmentRow(
                        task = task,
                        onRestore = { onRestore(task) },
                        onRemove = { pendingRemoval = task },
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        AssignmentSaveBar(
            form = form,
            onFormChanged = onFormChanged,
            onAdd = onAdd,
        )
    }

    pendingRemoval?.let { task ->
        AssignmentRemovalDialog(
            task = task,
            onConfirm = {
                onRemove(task)
                pendingRemoval = null
                if (form.editingTaskId == task.id) {
                    onFormChanged(AssignmentForm(assignedDate = form.assignedDate))
                }
            },
            onDismiss = { pendingRemoval = null },
        )
    }
}

@Composable
private fun AssignmentRow(
    task: PracticeTask,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onRemove: () -> Unit,
) {
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
                    "${when (task.sourceType) {
                        SourceType.NONE -> "No reference video"
                        SourceType.LOCAL ->
                            if (task.source.isBlank()) {
                                "Device video needs reattachment"
                            } else {
                                "Device video"
                            }
                        SourceType.YOUTUBE -> "YouTube"
                    }} · " +
                        "${task.assignmentType.label} · " +
                        when (task.goalType) {
                            PracticeGoalType.REPETITIONS ->
                                "${task.repetitions} repetitions"
                            PracticeGoalType.TIME ->
                                formatGoalDuration(task.targetSeconds.toLong())
                        },
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    PracticeMode.entries
                        .filter(task.enabledModes::contains)
                        .joinToString(" → ") { it.kidLabel() },
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onEdit) {
                    Text("Edit")
                }
                TextButton(onClick = onArchive) {
                    Text("Archive")
                }
                TextButton(onClick = onRemove) {
                    Text("Remove", color = Danger)
                }
            }
        }
    }
}

@Composable
private fun ArchivedAssignmentRow(
    task: PracticeTask,
    onRestore: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = CloudWhite.copy(alpha = 0.72f),
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(task.title, fontWeight = FontWeight.SemiBold)
                Text(
                    "Hidden from Today · history preserved · version ${task.revision}",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onRestore) {
                    Text("Restore")
                }
                TextButton(onClick = onRemove) {
                    Text("Remove", color = Danger)
                }
            }
        }
    }
}

@Composable
private fun AssignmentSaveBar(
    form: AssignmentForm,
    onFormChanged: (AssignmentForm) -> Unit,
    onAdd: () -> Unit,
) {
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
                "Saves for ${friendlyFullDate(form.assignedDate)}. " +
                    "Reference video and sheet music are optional.",
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

@Composable
private fun AssignmentRemovalDialog(
    task: PracticeTask,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove assignment?") },
        text = {
            Text(
                "This removes “${task.title}” and its recorded completion state. " +
                    "Saved practice videos remain in the practice log.",
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Danger),
            ) {
                Text("Remove")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
