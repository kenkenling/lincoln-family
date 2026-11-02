package com.lkeducation.lkpractice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrivacyConsentScreen(
    onAccept: (ChildAgeRange) -> Boolean,
    onExit: () -> Unit,
) {
    var selectedAgeName by rememberSaveable { mutableStateOf<String?>(null) }
    var parentConfirmed by rememberSaveable { mutableStateOf(false) }
    var policyAccepted by rememberSaveable { mutableStateOf(false) }
    var showPolicy by rememberSaveable { mutableStateOf(false) }
    var localError by rememberSaveable { mutableStateOf("") }
    val selectedAge = selectedAgeName?.let { name ->
        ChildAgeRange.entries.firstOrNull { it.name == name }
    }
    val canContinue = canAcceptPrivacyConsent(
        ageRange = selectedAge,
        parentConfirmed = parentConfirmed,
        privacyPolicyAccepted = policyAccepted,
    )

    SkyAppBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Default.PrivacyTip,
                        contentDescription = null,
                        tint = DeepSky,
                    )
                    Text(
                        "Parent privacy setup",
                        modifier = Modifier.accessibilityHeading(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = FontFamily.Cursive,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Please review this before LK Practice creates a student profile or practice data.",
                        color = Muted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CloudWhite),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "What happens to family data",
                            modifier = Modifier.accessibilityHeading(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        ConsentSummaryLine("Practice plans, progress, and recordings stay on this device.")
                        ConsentSummaryLine("There is no LK Practice account, advertising, or analytics service.")
                        ConsentSummaryLine("A parent chooses whether assignments use YouTube and when to export or share.")
                        ConsentSummaryLine("Parent Space can erase all app data at any time.")
                        OutlinedButton(
                            onClick = { showPolicy = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Read the full privacy policy")
                        }
                        Text(
                            "Policy version $CURRENT_PRIVACY_POLICY_VERSION · Updated $PRIVACY_POLICY_LAST_UPDATED",
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CloudWhite),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "Confirm the student’s age range",
                            modifier = Modifier.accessibilityHeading(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Choose a range only. LK Practice does not need a birth date.",
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ChildAgeRange.entries.forEach { ageRange ->
                                FilterChip(
                                    selected = selectedAge == ageRange,
                                    onClick = {
                                        selectedAgeName = ageRange.name
                                        localError = ""
                                    },
                                    label = { Text(ageRange.label) },
                                )
                            }
                        }
                    }
                }
            }

            item {
                ConsentCheckboxRow(
                    checked = parentConfirmed,
                    text = "I am the student’s parent or legal guardian, or I am the adult student.",
                    onCheckedChange = {
                        parentConfirmed = it
                        localError = ""
                    },
                )
            }

            item {
                ConsentCheckboxRow(
                    checked = policyAccepted,
                    text = "I have reviewed and accept the privacy policy and data-handling summary.",
                    onCheckedChange = {
                        policyAccepted = it
                        localError = ""
                    },
                )
            }

            if (localError.isNotBlank()) {
                item {
                    Text(
                        localError,
                        modifier = Modifier.assertiveLiveRegion(),
                        color = Danger,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            item {
                Button(
                    onClick = {
                        val ageRange = selectedAge
                        if (ageRange == null || !canContinue || !onAccept(ageRange)) {
                            localError = "Review each confirmation before continuing."
                        }
                    },
                    enabled = canContinue,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Accept and continue")
                }
            }

            item {
                TextButton(
                    onClick = onExit,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Exit for now")
                }
            }
        }
    }

    if (showPolicy) {
        PrivacyPolicyDialog(onDismiss = { showPolicy = false })
    }
}

@Composable
private fun ConsentSummaryLine(text: String) {
    Text(
        "• $text",
        color = Ink,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun ConsentCheckboxRow(
    checked: Boolean,
    text: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        color = CloudWhite,
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = checked,
                    role = Role.Checkbox,
                    onValueChange = onCheckedChange,
                )
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
            )
            Text(
                text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SkyBottom,
        shape = RoundedCornerShape(30.dp),
        icon = {
            Icon(
                Icons.Default.PrivacyTip,
                contentDescription = null,
                tint = DeepSky,
            )
        },
        title = {
            Text(
                "LK Practice privacy policy",
                modifier = Modifier.accessibilityHeading(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 580.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Version $CURRENT_PRIVACY_POLICY_VERSION · Updated $PRIVACY_POLICY_LAST_UPDATED",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                PRIVACY_POLICY_SECTIONS.forEach { section ->
                    Text(
                        section.title,
                        modifier = Modifier.accessibilityHeading(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    section.paragraphs.forEach { paragraph ->
                        Text(
                            paragraph,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}
