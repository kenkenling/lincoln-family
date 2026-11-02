package com.lkeducation.lkpractice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PrivacySettingsDialog(
    acceptedAgeRange: ChildAgeRange?,
    onDeleteAllData: () -> Boolean,
    onDismiss: () -> Unit,
) {
    var showPolicy by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }

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
                "Privacy & data",
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
                    "Policy version $CURRENT_PRIVACY_POLICY_VERSION · Updated $PRIVACY_POLICY_LAST_UPDATED",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Confirmed student age range: ${acceptedAgeRange?.label ?: "Not recorded"}",
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedButton(
                    onClick = { showPolicy = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Read privacy policy")
                }

                HorizontalDivider()
                Text(
                    "Local-first data",
                    modifier = Modifier.accessibilityHeading(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Profiles, assignments, practice history, consent, and managed recordings stay in app storage. No analytics, advertising, or LK Practice cloud account is connected.",
                    color = Muted,
                )
                Text(
                    "YouTube receives network information when an assigned YouTube reference player is opened or used. Files deliberately exported or shared follow the destination app’s privacy rules.",
                    color = Muted,
                )

                HorizontalDivider()
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Delete all LK Practice data",
                            modifier = Modifier.accessibilityHeading(),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "This uses Android’s application-data wipe and removes consent, Parent PIN, profiles, assignments, history, local databases, and app-managed recordings.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "External Excel files, exported ZIP files, and videos already shared to another app are not app-managed and must be deleted separately.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(
                            onClick = { showDeleteConfirmation = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Danger),
                        ) {
                            Text("Delete all data…")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )

    if (showPolicy) {
        PrivacyPolicyDialog(onDismiss = { showPolicy = false })
    }

    if (showDeleteConfirmation) {
        DeleteAllDataConfirmationDialog(
            onDeleteAllData = onDeleteAllData,
            onDismiss = { showDeleteConfirmation = false },
        )
    }
}

@Composable
private fun DeleteAllDataConfirmationDialog(
    onDeleteAllData: () -> Boolean,
    onDismiss: () -> Unit,
) {
    var confirmation by rememberSaveable { mutableStateOf("") }
    var localError by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Permanently delete all data?",
                modifier = Modifier.accessibilityHeading(),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "LK Practice will close. The next launch starts again with the privacy consent screen.",
                )
                Text(
                    "Type DELETE to confirm.",
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = {
                        confirmation = it.take(12)
                        localError = ""
                    },
                    label = { Text("Confirmation") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (localError.isNotBlank()) {
                    Text(
                        localError,
                        modifier = Modifier.assertiveLiveRegion(),
                        color = Danger,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!onDeleteAllData()) {
                        localError =
                            "Android could not start the data wipe. No app data was deleted."
                    }
                },
                enabled = confirmation.trim() == DELETE_CONFIRMATION,
                colors = ButtonDefaults.buttonColors(containerColor = Danger),
            ) {
                Text("Delete all app data")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private const val DELETE_CONFIRMATION = "DELETE"
