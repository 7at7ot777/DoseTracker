package com.dosetrack.app.ui.medications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dosetrack.app.R
import com.dosetrack.app.data.model.MedicationStatus
import com.dosetrack.app.ui.Formats
import com.dosetrack.app.ui.Labels
import com.dosetrack.app.ui.appResources
import com.dosetrack.app.ui.components.EmptyState
import com.dosetrack.app.ui.components.StatusBadge
import com.dosetrack.app.ui.currentLocale
import com.dosetrack.app.ui.is24HourClock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationListScreen(
    onAddMedication: () -> Unit,
    onOpenMedication: (Long) -> Unit,
    viewModel: MedicationListViewModel = viewModel(factory = MedicationListViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_medications)) }) },
        floatingActionButton = {
            if (state.items.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAddMedication,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.action_add_medication)) },
                )
            }
        },
    ) { padding ->
        when {
            state.loading -> Unit
            state.items.isEmpty() -> EmptyState(
                icon = Icons.Filled.Medication,
                title = stringResource(R.string.medications_empty_title),
                message = stringResource(R.string.medications_empty_message),
                actionLabel = stringResource(R.string.action_add_medication),
                onAction = onAddMedication,
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.items, key = { it.data.medication.id }) { item ->
                    MedicationCard(item = item, onClick = { onOpenMedication(item.data.medication.id) })
                }
            }
        }
    }
}

@Composable
private fun MedicationCard(item: MedicationListItem, onClick: () -> Unit) {
    val res = appResources()
    val locale = currentLocale()
    val is24 = is24HourClock()
    val medication = item.data.medication
    val paused = medication.status == MedicationStatus.PAUSED

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = medication.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(
                    text = stringResource(if (paused) R.string.status_paused else R.string.status_active),
                    containerColor = if (paused) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    contentColor = if (paused) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                )
            }
            Text(text = Labels.doseLine(res, medication), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = Labels.frequency(res, medication.frequency, item.data.schedules.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val next = when {
                paused -> stringResource(R.string.next_dose_paused)
                item.nextDoseAt != null -> stringResource(
                    R.string.next_dose,
                    Formats.relativeDateTime(res, item.nextDoseAt, is24, locale),
                )
                else -> stringResource(R.string.next_dose_none)
            }
            Text(
                text = next,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
