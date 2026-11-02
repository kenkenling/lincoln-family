package com.practicepartner.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@Composable
internal fun AssignmentVersionHistory(history: List<AssignmentHistory>) {
    if (history.isEmpty()) return
    var expanded by remember(history) { mutableStateOf(false) }
    TextButton(
        onClick = { expanded = !expanded },
        modifier = Modifier.padding(top = 2.dp),
    ) {
        Text(
            if (expanded) {
                "Hide version history"
            } else {
                "Version history (${history.size})"
            },
        )
    }
    if (!expanded) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        history.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(color = CloudBlue)
            AssignmentHistoryEntry(item)
        }
    }
}

@Composable
private fun AssignmentHistoryEntry(history: AssignmentHistory) {
    val assignment = history.previousAssignment
    Card(
        colors = CardDefaults.cardColors(containerColor = CloudWhite),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Version ${history.revision}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Replaced ${history.changedAtMillis.historyTimeLabel()} · " +
                    "changed ${history.changedFields.changedFieldLabel()}",
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(assignment.title, fontWeight = FontWeight.SemiBold)
            Text(
                "${friendlyFullDate(assignment.assignedDate)} · " +
                    assignment.goalDescription(),
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Modes: ${assignment.enabledModes.joinToString { it.kidLabel() }}",
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
            )
            if (assignment.source.isNotBlank()) {
                Text(
                    "Reference: ${assignment.source}",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (assignment.coachNote.isNotBlank()) {
                Text(
                    "Coach focus: ${assignment.coachNote}",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (assignment.whyImportant.isNotBlank()) {
                Text(
                    "Why it matters: ${assignment.whyImportant}",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (assignment.sheetMusicUri != null) {
                Text(
                    "Sheet music was attached",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (assignment.archivedAtMillis != null) {
                Text(
                    "This version was archived",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                if (assignment.teacherId == null) {
                    "Created by parent"
                } else {
                    "Created by teacher"
                },
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
internal fun SessionCompletionAndMastery(session: PracticeSession) {
    val goalReached = session.status == PracticeSessionStatus.COMPLETED
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.mergeForAccessibility(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (goalReached) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Forest,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Spacer(Modifier.size(20.dp))
            }
            Text(
                "Completion: ${session.status.completionLabel()}",
                color = if (goalReached) Forest else Muted,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(
            modifier = Modifier.mergeForAccessibility(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (session.masteryStatus == MasteryStatus.TEACHER_APPROVED) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = Forest,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Spacer(Modifier.size(20.dp))
            }
            Text(
                "Mastery: ${session.masteryStatus.summaryLabel}",
                color = when (session.masteryStatus) {
                    MasteryStatus.NOT_ASSESSED -> Muted
                    MasteryStatus.STUDENT_CONFIDENT -> DeepSky
                    MasteryStatus.TEACHER_APPROVED -> Forest
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun Assignment.goalDescription(): String = when (goalType) {
    PracticeGoalType.REPETITIONS -> "$repetitions repetitions"
    PracticeGoalType.TIME -> formatDuration(targetSeconds.toLong())
}

private fun Long.historyTimeLabel(): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(this))

private fun Set<String>.changedFieldLabel(): String =
    sorted()
        .map { field ->
            when (field) {
                "assignedDate" -> "date"
                "source", "sourceType" -> "reference"
                "startSeconds", "endSeconds" -> "reference timing"
                "repetitions", "targetSeconds", "goalType" -> "goal"
                "coachNote" -> "coach focus"
                "whyImportant" -> "why it matters"
                "sheetMusicUri" -> "sheet music"
                "teacherId" -> "creator"
                "assignmentType" -> "assignment type"
                "enabledModes" -> "practice modes"
                "archivedAtMillis" -> "archive status"
                else -> field
            }
        }
        .distinct()
        .ifEmpty { listOf("assignment details") }
        .joinToString()
