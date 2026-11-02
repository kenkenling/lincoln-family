package com.lkeducation.lkpractice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AssignmentEditorCard(
    studentName: String,
    instrument: String,
    currentDate: DateKey,
    form: AssignmentForm,
    templates: List<AssignmentTemplate>,
    hasAssignmentsForDate: Boolean,
    onFormChanged: (AssignmentForm) -> Unit,
    onChooseStartDate: () -> Unit,
    onChooseEndDate: () -> Unit,
    onPickVideo: () -> Unit,
    onPickSheetMusic: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Paper),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (form.editingTaskId == null) {
                    "Build $studentName’s practice plan"
                } else {
                    "Edit $studentName’s assignment"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.accessibilityHeading(),
            )
            Text(
                "$instrument · ${assignmentDateRangeLabel(form.startDate, form.endDate)}",
                color = Muted,
            )
            Text(
                "Quick start",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.accessibilityHeading(),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                templates.forEach { template ->
                    OutlinedButton(
                        onClick = {
                            onFormChanged(form.applyTemplate(template))
                        },
                    ) {
                        Text(template.label)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onChooseStartDate,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Start date")
                }
                OutlinedButton(
                    onClick = onChooseEndDate,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("End date")
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        onFormChanged(
                            form.copy(
                                startDate = currentDate,
                                endDate = currentDate,
                            ),
                        )
                    },
                ) {
                    Text("Today")
                }
                OutlinedButton(
                    onClick = {
                        val (startDate, endDate) = thisWeekAssignmentRange(currentDate)
                        onFormChanged(
                            form.copy(
                                startDate = startDate,
                                endDate = endDate,
                            ),
                        )
                    },
                ) {
                    Text("This week")
                }
                OutlinedButton(
                    onClick = {
                        val (startDate, endDate) = nextSevenDaysAssignmentRange(currentDate)
                        onFormChanged(
                            form.copy(
                                startDate = startDate,
                                endDate = endDate,
                            ),
                        )
                    },
                ) {
                    Text("Next 7 days")
                }
            }
            OutlinedTextField(
                value = form.title,
                onValueChange = { onFormChanged(form.copy(title = it)) },
                label = { Text("Technique, piece, or passage") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Assignment kind",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.accessibilityHeading(),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssignmentType.entries.forEach { option ->
                    FilterChip(
                        selected = form.assignmentType == option,
                        onClick = {
                            onFormChanged(form.copy(assignmentType = option))
                        },
                        label = { Text(option.label) },
                    )
                }
            }
            Text(
                "Practice goal",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.accessibilityHeading(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PracticeGoalType.entries.forEach { option ->
                    FilterChip(
                        selected = form.goalType == option,
                        onClick = { onFormChanged(form.copy(goalType = option)) },
                        label = { Text(option.label) },
                    )
                }
            }
            if (form.goalType == PracticeGoalType.REPETITIONS) {
                OutlinedTextField(
                    value = form.repetitions,
                    onValueChange = {
                        onFormChanged(form.copy(repetitions = it))
                    },
                    label = { Text("Target repetitions (1–20)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedTextField(
                    value = form.targetMinutes,
                    onValueChange = {
                        onFormChanged(form.copy(targetMinutes = it))
                    },
                    label = { Text("Target practice minutes (1–60)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = form.source,
                onValueChange = { onFormChanged(form.copy(source = it)) },
                label = { Text("Reference video link (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = onPickVideo, modifier = Modifier.fillMaxWidth()) {
                Text("Choose optional reference video from device")
            }
            Text(
                "Practice steps",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.accessibilityHeading(),
            )
            Text(
                "Choose the steps the student can use. Listen and Play Along require a reference video.",
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PracticeMode.entries.forEach { option ->
                    FilterChip(
                        selected = option in form.enabledModes,
                        onClick = {
                            val next = if (option in form.enabledModes) {
                                form.enabledModes - option
                            } else {
                                form.enabledModes + option
                            }
                            onFormChanged(form.copy(enabledModes = next))
                        },
                        label = { Text(option.kidLabel()) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = form.start,
                    onValueChange = { onFormChanged(form.copy(start = it)) },
                    label = { Text("Start seconds") },
                    placeholder = { Text("0") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = form.end,
                    onValueChange = { onFormChanged(form.copy(end = it)) },
                    label = { Text("End seconds") },
                    placeholder = { Text("Video end") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = form.coachNote,
                onValueChange = { onFormChanged(form.copy(coachNote = it)) },
                label = { Text("Coach focus") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.whyImportant,
                onValueChange = { onFormChanged(form.copy(whyImportant = it)) },
                label = { Text("Why this matters") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Sheet music (optional)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.accessibilityHeading(),
            )
            Text(
                "Attach a PDF or image for $studentName to open during practice, or leave this blank.",
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                onClick = onPickSheetMusic,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (form.sheetMusicUri == null) {
                        "Attach sheet music (optional)"
                    } else {
                        "Replace attached sheet music"
                    },
                )
            }
            if (form.sheetMusicUri != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Sheet music attached and ready.",
                        color = Forest,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            onFormChanged(form.copy(sheetMusicUri = null))
                        },
                    ) {
                        Text("Remove")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                    Text("Import CSV")
                }
                OutlinedButton(
                    onClick = onExport,
                    enabled = hasAssignmentsForDate,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Export date")
                }
            }
            Text(
                "CSV now preserves commas, quotation marks, dates, coaching notes, and optional sheet-music URIs.",
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
