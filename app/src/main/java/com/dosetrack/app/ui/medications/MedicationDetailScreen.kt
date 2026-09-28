package com.dosetrack.app.ui.medications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dosetrack.app.R
import com.dosetrack.app.data.db.MedicationWithSchedules
import com.dosetrack.app.data.db.effectiveReminderStyle
import com.dosetrack.app.data.model.FoodInstruction
import com.dosetrack.app.data.model.MedicationStatus
import com.dosetrack.app.ui.Formats
import com.dosetrack.app.ui.Labels
import com.dosetrack.app.ui.appResources
import com.dosetrack.app.ui.components.ConfirmDialog
import com.dosetrack.app.ui.components.StatusBadge
import com.dosetrack.app.ui.currentLocale
import com.dosetrack.app.ui.is24HourClock
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: MedicationDetailViewModel = viewModel(factory = MedicationDetailViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(deleted) { if (deleted) onBack() }

    val data = state.data
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(data?.medication?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    if (data != null) {
                        IconButton(onClick = { onEdit(data.medication.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Unit
            data == null -> Text(
                text = stringResource(R.string.medication_not_found),
                modifier = Modifier
                    .padding(padding)
                    .padding(24.dp),
            )
            else -> DetailContent(
                data = data,
                onPause = viewModel::pause,
                onResume = viewModel::resume,
                onDelete = { confirmDelete = true },
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_confirm_title),
            message = stringResource(R.string.delete_confirm_message),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun DetailContent(
    data: MedicationWithSchedules,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val res = appResources()
    val locale = currentLocale()
    val is24 = is24HourClock()
    val medication = data.medication
    val paused = medication.status == MedicationStatus.PAUSED

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = Labels.doseLine(res, medication),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            StatusBadge(
                text = stringResource(if (paused) R.string.status_paused else R.string.status_active),
                containerColor = if (paused) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (paused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        if (paused) {
            Text(
                text = stringResource(R.string.paused_info),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                medication.genericName?.let { DetailRow(stringResource(R.string.field_generic_name), it) }
                DetailRow(stringResource(R.string.field_type), Labels.medicationType(res, medication.medicationType))
                DetailRow(
                    stringResource(R.string.detail_schedule),
                    Labels.frequency(res, medication.frequency, data.schedules.size),
                )
                DetailRow(
                    stringResource(R.string.field_start_date),
                    Formats.date(LocalDate.ofEpochDay(medication.startDate), locale),
                )
                DetailRow(stringResource(R.string.detail_duration), Labels.duration(res, medication, locale))
                DetailRow(
                    stringResource(R.string.reminder_style_title),
                    Labels.reminderStyle(res, medication.effectiveReminderStyle),
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.detail_times), style = MaterialTheme.typography.titleMedium)
                data.schedules.sortedBy { it.timeOfDay }.forEachIndexed { index, schedule ->
                    if (index > 0) HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = Formats.time(schedule.timeOfDay, is24, locale),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = schedule.note ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        val instructions = when (medication.foodInstruction) {
            FoodInstruction.CUSTOM -> medication.customInstructions
            FoodInstruction.ANY_TIME -> null
            else -> Labels.foodInstruction(res, medication.foodInstruction)
        }
        if (instructions != null || medication.notes != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    instructions?.let { DetailRow(stringResource(R.string.detail_instructions), it) }
                    medication.notes?.let { DetailRow(stringResource(R.string.field_notes), it) }
                }
            }
        }

        OutlinedButton(
            onClick = if (paused) onResume else onPause,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            Icon(if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = null)
            Text(
                text = stringResource(if (paused) R.string.action_resume else R.string.action_pause),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        TextButton(
            onClick = onDelete,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null)
            Text(text = stringResource(R.string.action_delete), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
