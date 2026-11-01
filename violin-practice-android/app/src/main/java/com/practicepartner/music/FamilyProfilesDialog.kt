package com.practicepartner.music

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun FamilyProfilesDialog(
    profiles: List<ChildProfile>,
    activeProfileId: String,
    onSelectProfile: (ChildProfile) -> Boolean,
    onAddProfile: (String, String) -> Boolean,
    onExportProfile: (ChildProfile) -> Unit,
    onDeleteProfile: (ChildProfile) -> Boolean,
    onDismiss: () -> Unit,
) {
    var addingProfile by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var instrument by rememberSaveable { mutableStateOf("") }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteConfirmation by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SkyBottom,
        shape = RoundedCornerShape(30.dp),
        icon = {
            Icon(Icons.Default.PersonAdd, contentDescription = null, tint = DeepSky)
        },
        title = {
            Text(
                "Family profiles",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
            )
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Each child has separate assignments, progress, recordings, and a separate optional Excel file. Parents can export or delete one child at a time.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                profiles.forEach { profile ->
                    val selected = profile.id == activeProfileId
                    Surface(
                        color = if (selected) CloudBlue else CloudWhite,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(
                            if (selected) 2.dp else 1.dp,
                            if (selected) DeepSky else SkyOutline,
                        ),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(13.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Surface(color = LavenderCloud, shape = CircleShape) {
                                    Box(
                                        Modifier.size(44.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            profile.name.firstOrNull()?.uppercase() ?: "♪",
                                            fontWeight = FontWeight.Black,
                                        )
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(profile.name, fontWeight = FontWeight.Bold)
                                    Text(
                                        profile.instrument,
                                        color = Muted,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Current student",
                                        tint = DeepSky,
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                TextButton(
                                    onClick = {
                                        if (!selected && onSelectProfile(profile)) onDismiss()
                                    },
                                    enabled = !selected,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(if (selected) "Current" else "Select")
                                }
                                TextButton(
                                    onClick = { onExportProfile(profile) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Export")
                                }
                                TextButton(
                                    onClick = {
                                        pendingDeleteId = profile.id
                                        deleteConfirmation = ""
                                    },
                                    enabled = profiles.size > 1,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        "Delete",
                                        color = if (profiles.size > 1) Danger else Muted,
                                    )
                                }
                            }
                        }
                    }
                }

                if (addingProfile) {
                    Surface(
                        color = CloudWhite,
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("Add another child", fontWeight = FontWeight.Bold)
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
                                placeholder = { Text("Violin, piano, guitar…") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(
                                onClick = {
                                    if (onAddProfile(name, instrument)) {
                                        name = ""
                                        instrument = ""
                                        addingProfile = false
                                        onDismiss()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Create profile")
                            }
                            TextButton(
                                onClick = { addingProfile = false },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { addingProfile = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null)
                        Text(" Add child profile")
                    }
                }
                Text(
                    if (profiles.size > 1) {
                        "Export creates a ZIP with readable data and available app-managed recordings."
                    } else {
                        "The last child profile cannot be deleted. Add another profile first."
                    },
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )

    profiles.firstOrNull { it.id == pendingDeleteId }?.let { profile ->
        AlertDialog(
            onDismissRequest = {
                pendingDeleteId = null
                deleteConfirmation = ""
            },
            title = { Text("Delete ${profile.name}’s profile?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "This permanently removes this child’s app assignments, practice history, reflections, and app-managed recordings. Any external Excel file stays where you saved it.",
                    )
                    Text(
                        "Type ${profile.name} to confirm.",
                        fontWeight = FontWeight.SemiBold,
                    )
                    OutlinedTextField(
                        value = deleteConfirmation,
                        onValueChange = { deleteConfirmation = it },
                        label = { Text("Student name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (onDeleteProfile(profile)) {
                            pendingDeleteId = null
                            deleteConfirmation = ""
                        }
                    },
                    enabled = deleteConfirmation.trim() == profile.name,
                ) {
                    Text("Delete permanently")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingDeleteId = null
                        deleteConfirmation = ""
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}
