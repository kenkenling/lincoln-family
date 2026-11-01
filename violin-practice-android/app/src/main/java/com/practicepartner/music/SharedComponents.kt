package com.practicepartner.music

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun EmptyCard(title: String, detail: String) {
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

internal fun PracticeSessionStatus.summaryLabel(): String = when (this) {
    PracticeSessionStatus.ACTIVE -> "In progress"
    PracticeSessionStatus.COMPLETED -> "Goal reached"
    PracticeSessionStatus.ENDED -> "Finished for now"
    PracticeSessionStatus.INTERRUPTED -> "Interrupted safely"
}

internal fun timeLabel(seconds: Int): String =
    "%d:%02d".format(seconds / 60, seconds % 60)

internal fun friendlyFullDate(date: String): String = runCatching {
    LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"))
}.getOrDefault(date)
