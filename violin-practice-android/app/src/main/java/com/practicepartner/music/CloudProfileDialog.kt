package com.practicepartner.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
fun CloudProfileDialog(
    profile: StudentProfile,
    cloudState: CloudUiState,
    onDismiss: () -> Unit,
    onSaveProfile: (String, String, String) -> Boolean,
    onCreateExcel: () -> Unit,
    onChooseExistingExcel: () -> Unit,
    onSyncExcel: () -> Unit,
    onLoadExcel: () -> Unit,
    onOpenExcel: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    var name by remember(profile.id, profile.name) { mutableStateOf(profile.name) }
    var instrument by remember(profile.id, profile.instrument) {
        mutableStateOf(profile.instrument)
    }
    var driveAccountEmail by remember(profile.id, profile.driveAccountEmail) {
        mutableStateOf(profile.driveAccountEmail.orEmpty())
    }

    fun saveThen(action: () -> Unit) {
        if (onSaveProfile(name, instrument, driveAccountEmail)) action()
    }

    val workbookLinked = !cloudState.destinationUri.isNullOrBlank()
    val expectedAccount = normalizedAccountEmail(driveAccountEmail)
        ?.takeIf(String::isNotBlank)
    val accountReady = expectedAccount != null
    val driveConnected = workbookLinked &&
        cloudState.isGoogleDrive &&
        cloudState.destinationAccountEmail == expectedAccount
    val busy = cloudState.status == CloudSyncStatus.SYNCING

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Profile & connections",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Set up the student profile and private assignment workbook here. Practice screens stay simple.",
                    color = Muted,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Student name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = instrument,
                    onValueChange = { instrument = it },
                    label = { Text("Instrument") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                HorizontalDivider()
                Text(
                    "Excel workbook",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Create a real Excel .xlsx file in Google Drive. Assignments can be added in the app and synced to Excel, or edited in Excel and loaded into the app. Practice Partner verifies both the Drive provider and selected account before writing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                OutlinedTextField(
                    value = driveAccountEmail,
                    onValueChange = { driveAccountEmail = it },
                    label = { Text("Google Drive account to use") },
                    placeholder = { Text("name@gmail.com") },
                    supportingText = {
                        Text(
                            if (driveAccountEmail.isBlank() || accountReady) {
                                "Must match the account shown by Google Drive in the file picker."
                            } else {
                                "Enter a valid email address before connecting Drive."
                            },
                        )
                    },
                    isError = driveAccountEmail.isNotBlank() && !accountReady,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ExcelStatusCard(cloudState)

                if (busy) {
                    BusyRow(cloudState.detail.ifBlank { "Working with the Excel workbook…" })
                } else if (!driveConnected) {
                    Button(
                        onClick = { saveThen(onCreateExcel) },
                        enabled = accountReady,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Create Excel in Google Drive")
                    }
                    OutlinedButton(
                        onClick = { saveThen(onChooseExistingExcel) },
                        enabled = accountReady,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Reconnect Practice Partner workbook")
                    }
                    if (workbookLinked) {
                        cloudState.destinationUri?.let { workbookUri ->
                            TextButton(
                                onClick = { onOpenExcel(workbookUri) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Open current local workbook")
                            }
                        }
                    }
                    Text(
                        "Suggested file: Practice Partner - ${name.trim().ifBlank { "Lincoln" }}.xlsx",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Button(
                        onClick = { saveThen(onSyncExcel) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (cloudState.hasUnsyncedChanges) {
                                "Sync assignments to Excel"
                            } else {
                                "Save workbook snapshot now"
                            },
                        )
                    }
                    OutlinedButton(
                        onClick = { saveThen(onLoadExcel) },
                        enabled = !cloudState.hasUnsyncedChanges,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Load assignments from Excel")
                    }
                    cloudState.destinationUri?.let { workbookUri ->
                        OutlinedButton(
                            onClick = { onOpenExcel(workbookUri) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Open Excel workbook")
                        }
                    }
                    TextButton(
                        onClick = { saveThen(onCreateExcel) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Create a different Drive workbook")
                    }
                    if (cloudState.hasUnsyncedChanges) {
                        Text(
                            "Sync app assignment or profile changes before loading assignments from Excel. This prevents a remote edit from replacing newer work on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                        )
                    }
                }

                Text(
                    "Assignments entered in the app sync to Drive after editing. “Load assignments from Excel” imports teacher or parent additions and edits. New workbooks include all required columns on Profile, Assignments, Daily Progress, Daily Summary, and Practice Log. Detailed practice history remains local-first for now; the workbook receives a snapshot when assignments or the profile sync. Other Excel files are not overwritten, and recordings or private device paths are never copied. Drive controls the final server upload.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )

                HorizontalDivider()
                Text(
                    "YouTube connection — later",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            "Paused while Excel sync is stabilized",
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Teacher YouTube links still work in assignments. Recorded practice videos still use Share; direct channel connection will be handled separately.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                profile.youtubeChannelUrl.takeIf(String::isNotBlank)?.let { channelUrl ->
                    OutlinedButton(
                        onClick = { onOpenUrl(channelUrl) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Open saved YouTube channel")
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (onSaveProfile(name, instrument, driveAccountEmail)) onDismiss()
                },
            ) {
                Text("Save & close")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun ExcelStatusCard(state: CloudUiState) {
    val linked = !state.destinationUri.isNullOrBlank()
    val title = when (state.status) {
        CloudSyncStatus.DISCONNECTED -> "No Google Drive workbook linked"
        CloudSyncStatus.NEEDS_VERIFICATION -> "Drive connection needs verification"
        CloudSyncStatus.CONNECTED -> if (state.isGoogleDrive) {
            "Google Drive workbook connected"
        } else {
            "Workbook connection needs attention"
        }
        CloudSyncStatus.UNSYNCED -> "Local changes waiting for Drive"
        CloudSyncStatus.SYNCING -> "Writing Excel workbook…"
        CloudSyncStatus.ERROR -> if (linked) {
            "Workbook linked; sync needs attention"
        } else {
            "Excel setup needs attention"
        }
    }

    Surface(
        color = if (state.status == CloudSyncStatus.ERROR) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            statusCardColor(state.status)
        },
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                title,
                fontWeight = FontWeight.Bold,
                color = when {
                    state.status == CloudSyncStatus.ERROR ->
                        MaterialTheme.colorScheme.onErrorContainer
                    linked -> Forest
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            state.destinationName?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            state.destinationProviderName?.let {
                Text(
                    "Storage: $it",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            state.destinationAccountEmail?.let {
                Text(
                    "Verified account: $it",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (state.detail.isNotBlank()) {
                Text(state.detail, style = MaterialTheme.typography.bodySmall)
            }
            state.lastSyncedAtMillis?.let {
                Text(
                    "Last sync: ${
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                            .format(Date(it))
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
        }
    }
}

@Composable
private fun statusCardColor(status: CloudSyncStatus) = when (status) {
    CloudSyncStatus.CONNECTED -> Sage
    CloudSyncStatus.NEEDS_VERIFICATION,
    CloudSyncStatus.UNSYNCED,
    -> Amber.copy(alpha = 0.18f)
    CloudSyncStatus.DISCONNECTED,
    CloudSyncStatus.SYNCING,
    CloudSyncStatus.ERROR,
    -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun BusyRow(text: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
