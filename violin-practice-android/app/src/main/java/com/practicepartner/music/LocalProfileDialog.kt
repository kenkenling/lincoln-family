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
fun LocalProfileDialog(
    profile: StudentProfile,
    fileState: LocalFileUiState,
    onDismiss: () -> Unit,
    onSaveProfile: (String, String) -> Boolean,
    onCreateFile: () -> Unit,
    onChooseExistingFile: () -> Unit,
    onSaveFile: () -> Unit,
    onLoadFile: () -> Unit,
    onOpenFile: (String) -> Unit,
) {
    var name by remember(profile.id, profile.name) { mutableStateOf(profile.name) }
    var instrument by remember(profile.id, profile.instrument) {
        mutableStateOf(profile.instrument)
    }

    fun saveThen(action: () -> Unit) {
        if (onSaveProfile(name, instrument)) action()
    }

    val fileConfigured = !fileState.fileUri.isNullOrBlank()
    val busy = fileState.status == LocalFileStatus.SAVING

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Profile & local file",
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
                    "Keep the setup simple and private. No Google Drive or YouTube account is connected.",
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
                    "Local Excel file",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Choose one .xlsx file stored on this phone or tablet. The app can save assignments to it and load parent or teacher edits back from it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                LocalFileStatusCard(fileState)

                if (busy) {
                    BusyRow(fileState.detail.ifBlank { "Working with the local Excel file…" })
                } else if (!fileConfigured) {
                    Button(
                        onClick = { saveThen(onCreateFile) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Create local Excel file")
                    }
                    OutlinedButton(
                        onClick = { saveThen(onChooseExistingFile) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Choose existing Practice Partner file")
                    }
                    Text(
                        "Suggested file: Practice Partner - ${name.trim().ifBlank { "Lincoln" }}.xlsx",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Button(
                        onClick = { saveThen(onSaveFile) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (fileState.hasUnsavedChanges) {
                                "Save changes to local file"
                            } else {
                                "Save file snapshot now"
                            },
                        )
                    }
                    OutlinedButton(
                        onClick = { saveThen(onLoadFile) },
                        enabled = !fileState.hasUnsavedChanges,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Load assignments from local file")
                    }
                    fileState.fileUri?.let { fileUri ->
                        OutlinedButton(
                            onClick = { onOpenFile(fileUri) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Open local Excel file")
                        }
                    }
                    TextButton(
                        onClick = { saveThen(onChooseExistingFile) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Choose a different local file")
                    }
                    TextButton(
                        onClick = { saveThen(onCreateFile) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Create a different local file")
                    }
                    if (fileState.hasUnsavedChanges) {
                        Text(
                            "Save app changes before loading assignments from the Excel file. This avoids replacing newer work on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                        )
                    }
                }

                Text(
                    "The file contains Profile, Assignments, Daily Progress, Daily Summary, and Practice Log sheets. Practice Log includes safe recording filenames, but video content and private device paths are never written into Excel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )

                HorizontalDivider()
                Text(
                    "Practice videos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        "Recorded takes stay privately inside Practice Partner. Open them from Summary and use Android Share only when you choose.",
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(2.dp))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (onSaveProfile(name, instrument)) onDismiss()
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
private fun LocalFileStatusCard(state: LocalFileUiState) {
    val configured = !state.fileUri.isNullOrBlank()
    val title = when (state.status) {
        LocalFileStatus.NOT_CONFIGURED -> "No local Excel file configured"
        LocalFileStatus.READY -> "Local Excel file ready"
        LocalFileStatus.UNSAVED -> "Changes waiting to be saved"
        LocalFileStatus.SAVING -> "Saving local Excel file…"
        LocalFileStatus.ERROR -> if (configured) {
            "Local file needs attention"
        } else {
            "Local file setup needs attention"
        }
    }

    Surface(
        color = if (state.status == LocalFileStatus.ERROR) {
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
                    state.status == LocalFileStatus.ERROR ->
                        MaterialTheme.colorScheme.onErrorContainer
                    configured -> Forest
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            state.fileName?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            if (state.detail.isNotBlank()) {
                Text(state.detail, style = MaterialTheme.typography.bodySmall)
            }
            state.lastSavedAtMillis?.let {
                Text(
                    "Last saved: ${
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
private fun statusCardColor(status: LocalFileStatus) = when (status) {
    LocalFileStatus.READY -> Sage
    LocalFileStatus.UNSAVED -> Amber.copy(alpha = 0.18f)
    LocalFileStatus.NOT_CONFIGURED,
    LocalFileStatus.SAVING,
    LocalFileStatus.ERROR,
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
