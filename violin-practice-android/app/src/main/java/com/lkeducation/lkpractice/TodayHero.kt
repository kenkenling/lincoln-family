package com.lkeducation.lkpractice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun TodaySkyHero(
    studentName: String,
    daysSinceLastPractice: Int,
    date: DateKey,
    nextTask: PracticeTask?,
    selectedMode: PracticeMode,
    completedCount: Int,
    totalCount: Int,
    allComplete: Boolean,
    hasSelfDirectedOptions: Boolean,
    onStart: () -> Unit,
) {
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
            modifier = Modifier
                .align(Alignment.TopEnd)
                .hideFromAccessibility(),
            color = Paper.copy(alpha = 0.22f),
            fontSize = 72.sp,
        )
        Text(
            "☀",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 3.dp, end = 8.dp)
                .hideFromAccessibility(),
            color = SunYellow,
            fontSize = 34.sp,
        )
        Column(
            Modifier.fillMaxWidth().padding(end = 28.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                todayWelcomeGreeting(studentName, daysSinceLastPractice),
                color = Paper,
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Cursive,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.accessibilityHeading(),
            )
            Text(
                friendlyFullDate(date),
                color = Paper,
                style = MaterialTheme.typography.bodySmall,
            )
            if (nextTask == null) {
                Text(
                    if (hasSelfDirectedOptions) {
                        "No music mission is scheduled today."
                    } else {
                        "Your music missions will appear here."
                    },
                    color = Paper,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (hasSelfDirectedOptions) {
                        "Choose something familiar to practice below."
                    } else {
                        "A grown-up can add them from the settings cloud."
                    },
                    color = Paper,
                )
            } else {
                Text(
                    if (allComplete) {
                        "Amazing—today’s missions are complete!"
                    } else {
                        "Your next music mission"
                    },
                    color = Paper,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    nextTask.title,
                    color = Paper,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        selectedMode.kidEmoji(),
                        modifier = Modifier.hideFromAccessibility(),
                        color = Paper,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "${selectedMode.kidLabel()} · " +
                            "$completedCount of $totalCount finished",
                        color = Paper,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
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
