package com.lkeducation.lkpractice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun AdultGateDialog(
    pinConfigured: Boolean,
    onUnlock: (String) -> Boolean,
    onCreatePin: (String, String) -> Boolean,
    onGranted: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by rememberSaveable(pinConfigured) { mutableStateOf("") }
    var confirmation by rememberSaveable(pinConfigured) { mutableStateOf("") }
    var localError by rememberSaveable(pinConfigured) { mutableStateOf("") }

    fun submit() {
        val granted = if (pinConfigured) {
            onUnlock(pin)
        } else {
            onCreatePin(pin, confirmation)
        }
        if (granted) {
            localError = ""
            onGranted()
        } else {
            localError = if (pinConfigured) {
                "That PIN did not unlock Parent Space."
            } else {
                "Use matching 4-digit PIN entries."
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SkyBottom,
        shape = RoundedCornerShape(30.dp),
        icon = {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = DeepSky,
            )
        },
        title = {
            Text(
                if (pinConfigured) "Parent Space" else "Protect Parent Space",
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Cursive,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.accessibilityHeading(),
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    if (pinConfigured) {
                        "Enter the 4-digit parent PIN to open assignments, summaries, profiles, files, and video sharing."
                    } else {
                        "Create a 4-digit PIN for this device. Children can still open their practice profile without it."
                    },
                    color = Muted,
                )
                PinField(
                    value = pin,
                    label = if (pinConfigured) "Parent PIN" else "Create PIN",
                    imeAction = if (pinConfigured) ImeAction.Done else ImeAction.Next,
                    onValueChange = {
                        pin = it
                        localError = ""
                    },
                )
                if (!pinConfigured) {
                    PinField(
                        value = confirmation,
                        label = "Confirm PIN",
                        imeAction = ImeAction.Done,
                        onValueChange = {
                            confirmation = it
                            localError = ""
                        },
                    )
                    Text(
                        "This is a local adult gate, not an online account password.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                }
                if (localError.isNotBlank()) {
                    Text(
                        localError,
                        modifier = Modifier.assertiveLiveRegion(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = ::submit,
                enabled = pin.length == 4 && (pinConfigured || confirmation.length == 4),
            ) {
                Text(if (pinConfigured) "Unlock" else "Create PIN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun PinField(
    value: String,
    label: String,
    imeAction: ImeAction,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { entered ->
            onValueChange(entered.filter(Char::isDigit).take(4))
        },
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = imeAction,
        ),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
