package com.practicepartner.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProfileChooserScreen(
    household: Household,
    onSelectChild: (ChildProfile) -> Unit,
    onOpenParentSpace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            ) {
                Surface(
                    color = SunYellow,
                    shape = CircleShape,
                ) {
                    Box(
                        Modifier.size(68.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Ink,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
                Text(
                    "LK Practice",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Ink,
                    fontFamily = FontFamily.Cursive,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .accessibilityHeading(),
                )
                Text(
                    "Practice Makes Progress.",
                    color = DeepSky,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "Who is practicing today?",
                    color = Muted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .accessibilityHeading(),
                )
            }
        }

        household.childProfiles.forEachIndexed { index, profile ->
            item(key = profile.id) {
                ChildProfileCard(
                    profile = profile,
                    color = when (index % 3) {
                        1 -> MintCloud
                        2 -> LavenderCloud
                        else -> CloudBlue
                    },
                    onClick = { onSelectChild(profile) },
                )
            }
        }

        item {
            Surface(
                onClick = onOpenParentSpace,
                modifier = Modifier.mergeForAccessibility(),
                color = CloudWhite.copy(alpha = 0.9f),
                shape = RoundedCornerShape(26.dp),
                shadowElevation = 2.dp,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(17.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        color = SunYellow.copy(alpha = 0.72f),
                        shape = CircleShape,
                    ) {
                        Box(
                            Modifier.size(52.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Ink)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Parent Space",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            "Assignments, summaries, profiles, files, and sharing",
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = DeepSky,
                    )
                }
            }
        }

        item {
            Text(
                "Small steps. Every day. Big achievements.",
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                color = Muted,
                fontFamily = FontFamily.Cursive,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ChildProfileCard(
    profile: ChildProfile,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.mergeForAccessibility(),
        color = CloudWhite.copy(alpha = 0.96f),
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 4.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Surface(color = color, shape = CircleShape) {
                Box(
                    Modifier.size(58.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        profile.name.trim().firstOrNull()?.uppercase() ?: "♪",
                        modifier = Modifier.hideFromAccessibility(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Ink,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "${profile.name}’s Practice",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    profile.instrument,
                    color = Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = DeepSky,
            )
        }
    }
}
