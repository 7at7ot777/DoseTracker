package com.dosetrack.app.ui.medications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dosetrack.app.R
import com.dosetrack.app.data.model.DosageUnit
import com.dosetrack.app.data.model.DurationType
import com.dosetrack.app.data.model.FoodInstruction
import com.dosetrack.app.data.model.Frequency
import com.dosetrack.app.data.model.MedicationType
import com.dosetrack.app.data.model.ReminderStyle
import com.dosetrack.app.domain.DurationError
import com.dosetrack.app.domain.MAX_DURATION_DAYS
import com.dosetrack.app.domain.MedicationDuration
import com.dosetrack.app.domain.MedicationFieldError
import com.dosetrack.app.domain.MedicationValidation
import com.dosetrack.app.ui.Formats
import com.dosetrack.app.ui.Labels
import com.dosetrack.app.ui.appResources
import com.dosetrack.app.ui.components.DatePickerModal
import com.dosetrack.app.ui.components.DropdownField
import com.dosetrack.app.ui.components.TimePickerDialog
import com.dosetrack.app.ui.currentLocale
import com.dosetrack.app.ui.is24HourClock

private enum class DateTarget { START, END }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationEditScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: MedicationEditViewModel = viewModel(factory = MedicationEditViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (state.isEdit) R.string.title_edit_medication else R.string.title_add_medication))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Unit
            state.notFound -> Text(
                text = stringResource(R.string.medication_not_found),
                modifier = Modifier
                    .padding(padding)
                    .padding(24.dp),
            )
            else -> EditForm(state = state, viewModel = viewModel, modifier = Modifier.padding(padding))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditForm(
    state: MedicationFormState,
    viewModel: MedicationEditViewModel,
    modifier: Modifier = Modifier,
) {
    val res = appResources()
    val locale = currentLocale()
    val is24 = is24HourClock()
    val validation = state.validation
    val errors = if (state.showErrors) validation.fieldErrors else emptySet()
    val durationError = if (state.showErrors) validation.durationError else null
    val duplicates = if (state.showErrors) MedicationValidation.duplicateTimes(state.times.map { it.minutes }) else emptySet()

    var editingTimeKey by remember { mutableStateOf<Long?>(null) }
    var dateTarget by remember { mutableStateOf<DateTarget?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // --- Medication ---
        SectionTitle(stringResource(R.string.section_medication))
        OutlinedTextField(
            value = state.name,
            onValueChange = { v -> viewModel.edit { copy(name = v) } },
            label = { Text(stringResource(R.string.field_name)) },
            singleLine = true,
            isError = MedicationFieldError.NAME_REQUIRED in errors,
            supportingText = if (MedicationFieldError.NAME_REQUIRED in errors) {
                { Text(stringResource(R.string.error_name_required)) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.genericName,
            onValueChange = { v -> viewModel.edit { copy(genericName = v) } },
            label = { Text(stringResource(R.string.field_generic_name_optional)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = state.amountText,
                onValueChange = { v -> viewModel.edit { copy(amountText = v) } },
                label = { Text(stringResource(R.string.field_amount)) },
                singleLine = true,
                isError = MedicationFieldError.AMOUNT_INVALID in errors,
                supportingText = if (MedicationFieldError.AMOUNT_INVALID in errors) {
                    { Text(stringResource(R.string.error_amount_invalid)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            DropdownField(
                label = stringResource(R.string.field_unit),
                options = DosageUnit.entries,
                selected = state.unit,
                optionLabel = { Labels.dosageUnit(res, it) },
                onSelected = { v -> viewModel.edit { copy(unit = v) } },
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = state.strength,
            onValueChange = { v -> viewModel.edit { copy(strength = v) } },
            label = { Text(stringResource(R.string.field_strength)) },
            placeholder = { Text(stringResource(R.string.field_strength_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownField(
            label = stringResource(R.string.field_type),
            options = MedicationType.entries,
            selected = state.type,
            optionLabel = { Labels.medicationType(res, it) },
            onSelected = { v -> viewModel.edit { copy(type = v) } },
            modifier = Modifier.fillMaxWidth(),
        )

        // --- Schedule ---
        SectionTitle(stringResource(R.string.section_schedule))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Frequency.entries.forEach { frequency ->
                FilterChip(
                    selected = state.frequency == frequency,
                    onClick = { viewModel.setFrequency(frequency) },
                    label = { Text(Labels.frequencyOption(res, frequency)) },
                )
            }
        }
        state.times.forEach { row ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { editingTimeKey = row.key },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Icon(Icons.Filled.Schedule, contentDescription = null)
                            Text(
                                text = Formats.time(row.minutes, is24, locale),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        if (row.minutes in duplicates) {
                            Text(
                                text = stringResource(R.string.error_duplicate_time_short),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                            if (state.times.size > 1) {
                                IconButton(onClick = { viewModel.removeTime(row.key) }) {
                                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_remove_time))
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = row.note,
                        onValueChange = { viewModel.setTimeNote(row.key, it) },
                        placeholder = { Text(stringResource(R.string.field_time_note_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        TextButton(onClick = viewModel::addTime) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(stringResource(R.string.action_add_time), modifier = Modifier.padding(start = 8.dp))
        }
        if (MedicationFieldError.NO_TIMES in errors) ErrorText(stringResource(R.string.error_no_times))
        if (MedicationFieldError.DUPLICATE_TIMES in errors) ErrorText(stringResource(R.string.error_duplicate_times))

        Text(stringResource(R.string.reminder_style_title), style = MaterialTheme.typography.titleSmall)
        Column(modifier = Modifier.selectableGroup()) {
            ReminderStyle.entries.forEach { style ->
                val selected = state.reminderStyle == style
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .selectable(
                            selected = selected,
                            onClick = { viewModel.edit { copy(reminderStyle = style) } },
                            role = Role.RadioButton,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(Labels.reminderStyle(res, style), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = Labels.reminderStyleDescription(res, style),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // --- Duration ---
        SectionTitle(stringResource(R.string.section_duration))
        OutlinedButton(
            onClick = { dateTarget = DateTarget.START },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = null)
            Text(
                text = stringResource(R.string.start_date_value, Formats.date(state.startDate, locale)),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        val durationOptions = DurationType.entries
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            durationOptions.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = state.durationType == type,
                    onClick = { viewModel.edit { copy(durationType = type) } },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = durationOptions.size),
                ) {
                    Text(
                        stringResource(
                            when (type) {
                                DurationType.ONGOING -> R.string.duration_ongoing
                                DurationType.DAYS -> R.string.duration_option_days
                                DurationType.END_DATE -> R.string.duration_option_end_date
                            },
                        ),
                    )
                }
            }
        }
        when (state.durationType) {
            DurationType.ONGOING -> Text(
                text = stringResource(R.string.duration_ongoing_info),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DurationType.DAYS -> {
                OutlinedTextField(
                    value = state.daysText,
                    onValueChange = { v -> viewModel.edit { copy(daysText = v) } },
                    label = { Text(stringResource(R.string.field_days)) },
                    singleLine = true,
                    isError = durationError != null,
                    supportingText = if (durationError != null) {
                        { Text(durationErrorText(durationError)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                val end = state.computedEndDate
                val days = MedicationDuration.parseDays(state.daysText)
                if (end != null && days != null && days in 1..MAX_DURATION_DAYS) {
                    Text(
                        text = Labels.duration(res, DurationType.DAYS, days, end, locale),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            DurationType.END_DATE -> {
                OutlinedButton(
                    onClick = { dateTarget = DateTarget.END },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                    Text(
                        text = state.endDate?.let { stringResource(R.string.end_date_value, Formats.date(it, locale)) }
                            ?: stringResource(R.string.action_choose_end_date),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                durationError?.let { ErrorText(durationErrorText(it)) }
            }
        }

        // --- Instructions ---
        SectionTitle(stringResource(R.string.section_instructions))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FoodInstruction.entries.forEach { instruction ->
                FilterChip(
                    selected = state.foodInstruction == instruction,
                    onClick = { viewModel.edit { copy(foodInstruction = instruction) } },
                    label = { Text(Labels.foodInstruction(res, instruction)) },
                )
            }
        }
        if (state.foodInstruction == FoodInstruction.CUSTOM) {
            OutlinedTextField(
                value = state.customInstructions,
                onValueChange = { v -> viewModel.edit { copy(customInstructions = v) } },
                label = { Text(stringResource(R.string.field_custom_instructions)) },
                placeholder = { Text(stringResource(R.string.field_custom_instructions_hint)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = state.notes,
            onValueChange = { v -> viewModel.edit { copy(notes = v) } },
            label = { Text(stringResource(R.string.field_notes_optional)) },
            minLines = 2,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.showErrors && !validation.isValid) ErrorText(stringResource(R.string.error_fix_fields))
        Button(
            onClick = viewModel::save,
            enabled = !state.saving,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            Text(stringResource(R.string.action_save))
        }
    }

    val editingRow = editingTimeKey?.let { key -> state.times.firstOrNull { it.key == key } }
    if (editingRow != null) {
        TimePickerDialog(
            initialMinutes = editingRow.minutes,
            onConfirm = { minutes ->
                viewModel.setTime(editingRow.key, minutes)
                editingTimeKey = null
            },
            onDismiss = { editingTimeKey = null },
        )
    }

    dateTarget?.let { target ->
        DatePickerModal(
            initialDate = when (target) {
                DateTarget.START -> state.startDate
                DateTarget.END -> state.endDate ?: state.startDate.plusDays(6)
            },
            minDate = if (target == DateTarget.END) state.startDate else null,
            onConfirm = { date ->
                when (target) {
                    DateTarget.START -> viewModel.edit { copy(startDate = date) }
                    DateTarget.END -> viewModel.edit { copy(endDate = date) }
                }
                dateTarget = null
            },
            onDismiss = { dateTarget = null },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun ErrorText(text: String) {
    Text(text = text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun durationErrorText(error: DurationError): String = when (error) {
    DurationError.DAYS_REQUIRED -> stringResource(R.string.error_days_required)
    DurationError.DAYS_NOT_POSITIVE -> stringResource(R.string.error_days_positive)
    DurationError.DAYS_TOO_LARGE -> stringResource(R.string.error_days_too_large, MAX_DURATION_DAYS)
    DurationError.END_DATE_REQUIRED -> stringResource(R.string.error_end_date_required)
    DurationError.END_BEFORE_START -> stringResource(R.string.error_end_before_start)
}
