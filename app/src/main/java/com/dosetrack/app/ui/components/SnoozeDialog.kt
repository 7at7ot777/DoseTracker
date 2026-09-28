package com.dosetrack.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dosetrack.app.R
import com.dosetrack.app.domain.Digits
import com.dosetrack.app.ui.appResources

val SNOOZE_PRESETS = listOf(5, 10, 15, 30)
const val MAX_SNOOZE_MINUTES = 12 * 60

/** Parses a custom snooze length; null unless 1..[MAX_SNOOZE_MINUTES]. */
fun parseSnoozeMinutes(text: String): Int? =
    Digits.normalize(text).trim().toIntOrNull()?.takeIf { it in 1..MAX_SNOOZE_MINUTES }

/** 5 / 10 / 15 / 30 minutes or a custom number of minutes. */
@Composable
fun SnoozeDialog(
    title: String,
    initialMinutes: Int,
    confirmLabel: String,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val res = appResources()
    // null selection = custom
    var selected by remember { mutableStateOf(initialMinutes.takeIf { it in SNOOZE_PRESETS }) }
    var customText by remember {
        mutableStateOf(if (initialMinutes in SNOOZE_PRESETS) "" else initialMinutes.toString())
    }
    val customMinutes = parseSnoozeMinutes(customText)
    val minutes = selected ?: customMinutes

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                SNOOZE_PRESETS.forEach { preset ->
                    RadioRow(
                        label = res.getQuantityString(R.plurals.minutes_count, preset, preset),
                        selected = selected == preset,
                        onClick = { selected = preset },
                    )
                }
                RadioRow(
                    label = stringResource(R.string.snooze_custom),
                    selected = selected == null,
                    onClick = { selected = null },
                )
                if (selected == null) {
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { customText = it },
                        label = { Text(stringResource(R.string.snooze_custom_minutes)) },
                        singleLine = true,
                        isError = customText.isNotBlank() && customMinutes == null,
                        supportingText = { Text(stringResource(R.string.snooze_custom_range, MAX_SNOOZE_MINUTES)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { minutes?.let(onConfirm) }, enabled = minutes != null) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label)
    }
}
