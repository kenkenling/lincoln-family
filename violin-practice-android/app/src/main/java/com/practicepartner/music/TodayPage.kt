package com.practicepartner.music

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayPage(
    state: PracticeUiState,
    studentName: String,
    onSelectMode: (PracticeMode) -> Unit,
    onPractice: (PracticeTask) -> Unit,
    onToggleCompleted: (PracticeTask) -> Unit,
) {
    val completedCount = state.todayTasks.count { state.goalMet(it, state.currentDate) }
    val allComplete = state.todayTasks.isNotEmpty() &&
        completedCount == state.todayTasks.size
    val nextTask = state.todayTasks.firstOrNull { !state.goalMet(it, state.currentDate) }
        ?: state.todayTasks.firstOrNull()
    val availableModes = nextTask?.enabledModes.orEmpty()
        .ifEmpty { PracticeMode.entries.toSet() }
    val selectedMode = state.mode.takeIf(availableModes::contains)
        ?: availableModes.first()

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TodaySkyHero(
                studentName = studentName,
                date = state.currentDate,
                nextTask = nextTask,
                selectedMode = selectedMode,
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
                        "Choose the next practice step",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        if (nextTask == null) {
                            "Practice steps appear with the next music mission."
                        } else {
                            "These steps were selected for ${nextTask.title}."
                        },
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
            val completed = state.goalMet(task, state.currentDate)
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

internal fun PracticeMode.kidLabel(): String = when (this) {
    PracticeMode.LISTEN -> "Listen first"
    PracticeMode.LISTEN_AND_PLAY -> "Play along"
    PracticeMode.PLAY_YOURSELF -> "Play solo"
}

internal fun PracticeMode.kidEmoji(): String = when (this) {
    PracticeMode.LISTEN -> "👂"
    PracticeMode.LISTEN_AND_PLAY -> "🎶"
    PracticeMode.PLAY_YOURSELF -> "⭐"
}
