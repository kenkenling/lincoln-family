package com.practicepartner.music

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun GrownUpToolsDialog(
    currentPage: AppPage,
    studentName: String,
    onOpenToday: () -> Unit,
    onOpenAssignments: () -> Unit,
    onOpenSummary: () -> Unit,
    onOpenProfile: () -> Unit,
    onDismiss: () -> Unit,
) {
    val firstName = studentName.trim().substringBefore(' ').ifBlank { "the student" }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SkyBottom,
        shape = RoundedCornerShape(30.dp),
        icon = {
            Icon(
                Icons.Default.SupervisorAccount,
                contentDescription = null,
                tint = DeepSky,
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Grown-up tools",
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FontFamily.Cursive,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "For parents and coaches",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GrownUpToolButton(
                    title = "Today’s practice",
                    detail = "Return to $firstName’s simple practice screen.",
                    icon = Icons.Default.Home,
                    iconBackground = CloudBlue,
                    selected = currentPage == AppPage.TODAY,
                    onClick = onOpenToday,
                )
                GrownUpToolButton(
                    title = "Plan assignments",
                    detail = "Add videos, notes, repetitions, and sheet music.",
                    icon = Icons.AutoMirrored.Filled.Assignment,
                    iconBackground = Color(0xFFFFF0C7),
                    selected = currentPage == AppPage.ASSIGN,
                    onClick = onOpenAssignments,
                )
                GrownUpToolButton(
                    title = "Practice summary",
                    detail = "Review time, repetitions, progress, and videos.",
                    icon = Icons.Default.BarChart,
                    iconBackground = LavenderCloud,
                    selected = currentPage == AppPage.SUMMARY,
                    onClick = onOpenSummary,
                )
                GrownUpToolButton(
                    title = "Profile & local file",
                    detail = "Update the student profile and Excel file.",
                    icon = Icons.Default.Person,
                    iconBackground = MintCloud,
                    onClick = onOpenProfile,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun GrownUpToolButton(
    title: String,
    detail: String,
    icon: ImageVector,
    iconBackground: Color,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = if (selected) CloudBlue else CloudWhite,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) DeepSky else SkyOutline,
        ),
        tonalElevation = if (selected) 2.dp else 0.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                color = iconBackground,
                shape = CircleShape,
            ) {
                Box(
                    Modifier.size(46.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = Ink)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    detail,
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
