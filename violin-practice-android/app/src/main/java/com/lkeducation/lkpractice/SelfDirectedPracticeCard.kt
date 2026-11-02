package com.lkeducation.lkpractice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun SelfDirectedPracticeCard(
    state: PracticeUiState,
    title: String,
    onPractice: (PracticeTask) -> Unit,
    onSurprisePractice: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = LavenderCloud),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.accessibilityHeading(),
            )
            Text(
                "Pick a recent assignment. Today gets its own progress, so earlier practice stays unchanged.",
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
            )
            state.selfDirectedPracticeOptions.forEach { task ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .mergeForAccessibility(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(task.title, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${assignmentDateRangeLabel(task.startDate, task.endDate)} · " +
                                state.progressLabel(task, state.currentDate),
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(
                        onClick = { onPractice(task) },
                        enabled = task.sourceType != SourceType.LOCAL || task.source.isNotBlank(),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text(" Start")
                    }
                }
            }
            OutlinedButton(
                onClick = onSurprisePractice,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Surprise me!")
            }
        }
    }
}
