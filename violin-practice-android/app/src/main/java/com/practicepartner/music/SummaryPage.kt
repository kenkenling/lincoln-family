package com.practicepartner.music

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import java.text.DateFormat
import java.time.format.DateTimeFormatter
import java.util.Date

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
    val sessions = state.summarySessions
    val weekly = state.weeklySummary(state.summaryDate)
    val completedCount = tasks.count { state.goalMet(it, state.summaryDate) }
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
                            color = Paper,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            formatDuration(displayElapsed),
                            color = Paper,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("active practice", color = Paper)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "$completedCount / ${tasks.size}",
                            color = Paper,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("assignments complete", color = Paper)
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription =
                        weeklyPracticeAccessibilityDescription(
                            practicedDays = weekly.practicedDays,
                            totalDays = weekly.days.size,
                        ) +
                        " ${formatDuration(weekly.totalSeconds)} total practice time. " +
                        WEEKLY_PATTERN_COPY
                },
                colors = CardDefaults.cardColors(containerColor = CloudWhite),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "This week",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        weekly.days.forEach { day ->
                            val date = day.date.toLocalDate()
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    date.format(DateTimeFormatter.ofPattern("E")).take(1),
                                    color = Muted,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                Surface(
                                    color = if (day.practiced) SunYellow else CloudBlue,
                                    shape = CircleShape,
                                    modifier = Modifier.padding(top = 4.dp),
                                ) {
                                    Box(
                                        Modifier.size(34.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            date.dayOfMonth.toString(),
                                            color = Ink,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        "${weekly.practicedDays} practice " +
                            (
                                if (weekly.practicedDays == 1) {
                                    "day"
                                } else {
                                    "days"
                                }
                                ) +
                            " · ${formatDuration(weekly.totalSeconds)} total",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "A gentle pattern, not a streak—rest days are okay.",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        item {
            Text(
                "Assignment progress",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.accessibilityHeading(),
            )
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
                Column(
                    modifier = Modifier.fillMaxWidth().padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clearAndSetSemantics {
                                contentDescription = taskCardAccessibilityDescription(
                                    title = task.title,
                                    progress = state.progressLabel(task, state.summaryDate),
                                    completed = state.goalMet(task, state.summaryDate),
                                    archived = task.archivedAtMillis != null,
                                )
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(task.title, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${task.assignmentType.label} · " +
                                    state.progressLabel(task, state.summaryDate),
                                color = Muted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (task.archivedAtMillis != null) {
                                Text(
                                    "Archived from Today",
                                    color = Muted,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        if (state.goalMet(task, state.summaryDate)) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Forest,
                            )
                        }
                    }
                    AssignmentVersionHistory(
                        history = state.assignmentHistoryFor(task.id),
                    )
                }
            }
        }

        item {
            Text(
                "Practice sessions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.accessibilityHeading(),
            )
        }
        if (sessions.isEmpty()) {
            item {
                EmptyCard(
                    "No sessions for this date",
                    "Each started mission will keep its own time, goal, outcome, and reflection.",
                )
            }
        }
        items(sessions.reversed(), key = { "session-${it.id}" }) { session ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Paper),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(session.assignmentTitle, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${session.assignmentType.label} · " +
                            formatDuration(session.elapsedSeconds),
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Steps explored: " +
                            session.orderedModesUsed().joinToString(" → ") {
                                it.kidLabel()
                            },
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    SessionCompletionAndMastery(session)
                    Text(
                        "Assignment version ${session.assignmentRevision}",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    session.reflection?.let { reflection ->
                        Text(
                            "Reflection: ${reflection.summaryLabel}",
                            color = DeepSky,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        item {
            Text(
                "Practice log",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.accessibilityHeading(),
            )
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
                        "${event.event} · " +
                            DateFormat.getTimeInstance(DateFormat.SHORT)
                                .format(Date(event.atMillis)),
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
            title = {
                Text(
                    "Delete this recording?",
                    modifier = Modifier.accessibilityHeading(),
                )
            },
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
