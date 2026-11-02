package com.lkeducation.lkpractice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayPage(
    state: PracticeUiState,
    studentName: String,
    onSelectMode: (PracticeMode) -> Unit,
    onPractice: (PracticeTask) -> Unit,
    onSelfDirectedPractice: (PracticeTask) -> Unit,
    onSurprisePractice: () -> Unit,
    onToggleCompleted: (PracticeTask) -> Unit,
) {
    val completedCount = state.todayTasks.count { state.goalMet(it, state.currentDate) }
    val allComplete = state.allTodayTasksComplete
    val nextTask = state.todayTasks.firstOrNull { !state.goalMet(it, state.currentDate) }
        ?: state.todayTasks.firstOrNull()
    val availableModes = nextTask?.enabledModes.orEmpty()
        .ifEmpty { PracticeMode.entries.toSet() }
    val selectedMode = state.mode.takeIf(availableModes::contains)
        ?: availableModes.first()
    val weekly = state.weeklySummary(state.currentDate)
    val showSelfDirectedPractice =
        state.selfDirectedPracticeOptions.isNotEmpty() &&
            (state.todayTasks.isEmpty() || allComplete)

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TodaySkyHero(
                studentName = studentName,
                daysSinceLastPractice = state.daysSinceLastPractice,
                date = state.currentDate,
                nextTask = nextTask,
                selectedMode = selectedMode,
                completedCount = completedCount,
                totalCount = state.todayTasks.size,
                allComplete = allComplete,
                hasSelfDirectedOptions = state.selfDirectedPracticeOptions.isNotEmpty(),
                onStart = { nextTask?.let(onPractice) },
            )
        }

        item {
            WeeklyDotsRow(weekly)
        }

        if (nextTask != null) {
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
                            "Choose the next practice step",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            "These steps were selected for ${nextTask.title}.",
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PracticeMode.entries.filter(availableModes::contains).forEach { option ->
                                FilterChip(
                                    selected = selectedMode == option,
                                    onClick = { onSelectMode(option) },
                                    leadingIcon = {
                                        Text(
                                            option.kidEmoji(),
                                            modifier = Modifier.hideFromAccessibility(),
                                            fontSize = 16.sp,
                                        )
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
        }

        if (state.todayTasks.isNotEmpty()) {
            item {
                Text(
                    "Today’s music missions",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Ink,
                    modifier = Modifier.accessibilityHeading(),
                )
            }
        }

        if (state.todayTasks.isEmpty() && !showSelfDirectedPractice) {
            item {
                EmptyCard(
                    "No music missions yet",
                    "Ask a grown-up to add today’s plan from the small settings button.",
                )
            }
        }

        itemsIndexed(state.todayTasks, key = { _, task -> task.id }) { index, task ->
            val completed = state.goalMet(task, state.currentDate)
            val needsReference = task.sourceType == SourceType.LOCAL && task.source.isBlank()
            Card(
                modifier = Modifier.mergeForAccessibility(),
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .clearAndSetSemantics {
                                contentDescription = taskCardAccessibilityDescription(
                                    title = task.title,
                                    progress = state.progressLabel(task, state.currentDate),
                                    completed = completed,
                                )
                            },
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
                                "${task.assignmentType.label} · " +
                                    state.progressLabel(task, state.currentDate),
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
                    Text(
                        "Steps: " + PracticeMode.entries
                            .filter(task.enabledModes::contains)
                            .joinToString(" → ") { it.kidLabel() },
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
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
        if (showSelfDirectedPractice) {
            item {
                SelfDirectedPracticeCard(
                    state = state,
                    title = if (state.todayTasks.isEmpty()) {
                        "Practice on your own"
                    } else {
                        "Keep practicing"
                    },
                    onPractice = onSelfDirectedPractice,
                    onSurprisePractice = onSurprisePractice,
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
internal fun WeeklyDotsRow(weekly: WeeklyPracticeSummary) {
    val dots = weeklyDotItems(weekly)
    Card(
        modifier = Modifier.clearAndSetSemantics {
            contentDescription =
                weeklyPracticeAccessibilityDescription(
                    practicedDays = weekly.practicedDays,
                    totalDays = weekly.days.size,
                ) + " $WEEKLY_PATTERN_COPY"
        },
        colors = CardDefaults.cardColors(
            containerColor = CloudWhite.copy(alpha = 0.88f),
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(
                "Your practice week",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                dots.forEach { dot ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            dot.dayLabel,
                            color = Muted,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Surface(
                            color = if (dot.practiced) SunYellow else CloudBlue,
                            shape = CircleShape,
                        ) {
                            Box(
                                Modifier.size(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (dot.practiced) {
                                    Text(
                                        "✓",
                                        modifier = Modifier.hideFromAccessibility(),
                                        color = Ink,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Text(
                WEEKLY_PATTERN_COPY,
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

internal data class WeeklyDotItem(
    val dayLabel: String,
    val practiced: Boolean,
)

internal fun weeklyDotItems(weekly: WeeklyPracticeSummary): List<WeeklyDotItem> =
    weekly.days.map { day ->
        WeeklyDotItem(
            dayLabel = compactDayLabel(day.date),
            practiced = day.practiced,
        )
    }

private fun compactDayLabel(date: DateKey): String =
    when (date.toLocalDate().dayOfWeek) {
        DayOfWeek.MONDAY -> "M"
        DayOfWeek.TUESDAY -> "T"
        DayOfWeek.WEDNESDAY -> "W"
        DayOfWeek.THURSDAY -> "T"
        DayOfWeek.FRIDAY -> "F"
        DayOfWeek.SATURDAY -> "S"
        DayOfWeek.SUNDAY -> "S"
    }

internal const val WEEKLY_PATTERN_COPY =
    "A gentle pattern, not a streak—rest days are okay."
