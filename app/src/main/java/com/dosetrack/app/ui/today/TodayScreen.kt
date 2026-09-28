package com.dosetrack.app.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import com.dosetrack.app.domain.DoseDisplayState
import com.dosetrack.app.ui.Formats
import com.dosetrack.app.ui.Labels
import com.dosetrack.app.ui.appResources
import com.dosetrack.app.ui.components.ConfirmDialog
import com.dosetrack.app.ui.components.DoseStatusBadge
import com.dosetrack.app.ui.components.EmptyState
import com.dosetrack.app.ui.components.ReminderHealthBanners
import com.dosetrack.app.ui.components.SnoozeDialog
import com.dosetrack.app.ui.currentLocale
import com.dosetrack.app.ui.is24HourClock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onAddMedication: () -> Unit,
    onOpenMedication: (Long) -> Unit,
    viewModel: TodayViewModel = viewModel(factory = TodayViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val res = appResources()
    val locale = currentLocale()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmTake by remember { mutableStateOf<TodayDose?>(null) }
    var confirmSkip by remember { mutableStateOf<TodayDose?>(null) }
    var snoozeTarget by remember { mutableStateOf<TodayDose?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val (message, previous) = when (event) {
                is TodayEvent.Taken -> res.getString(R.string.snackbar_taken, event.medicationName) to event.previous
                is TodayEvent.Skipped -> res.getString(R.string.snackbar_skipped, event.medicationName) to event.previous
            }
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = res.getString(R.string.action_undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undo(previous)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.title_today))
                        Text(
                            text = Formats.dayHeader(state.date, locale),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Unit
            !state.hasMedications && state.doses.isEmpty() -> EmptyState(
                icon = Icons.Filled.Medication,
                title = stringResource(R.string.medications_empty_title),
                message = stringResource(R.string.medications_empty_message),
                actionLabel = stringResource(R.string.action_add_medication),
                onAction = onAddMedication,
                modifier = Modifier.padding(padding),
            )
            else -> TodayList(
                state = state,
                onTake = { confirmTake = it },
                onSkip = { confirmSkip = it },
                onSnooze = { snoozeTarget = it },
                onMarkNotTaken = viewModel::markNotTaken,
                onOpenMedication = onOpenMedication,
                modifier = Modifier.padding(padding),
            )
        }
    }

    confirmTake?.let { item ->
        ConfirmDialog(
            title = stringResource(R.string.confirm_take_title),
            message = stringResource(R.string.confirm_dose_message, item.medication.name, Labels.doseLine(res, item.medication)),
            confirmLabel = stringResource(R.string.action_mark_taken),
            onConfirm = {
                viewModel.take(item)
                confirmTake = null
            },
            onDismiss = { confirmTake = null },
        )
    }
    snoozeTarget?.let { item ->
        SnoozeDialog(
            title = stringResource(R.string.snooze_title, item.medication.name),
            initialMinutes = state.defaultSnoozeMinutes,
            confirmLabel = stringResource(R.string.action_snooze),
            onConfirm = { minutes ->
                viewModel.snooze(item, minutes)
                snoozeTarget = null
            },
            onDismiss = { snoozeTarget = null },
        )
    }
    confirmSkip?.let { item ->
        ConfirmDialog(
            title = stringResource(R.string.confirm_skip_title),
            message = stringResource(R.string.confirm_skip_message),
            confirmLabel = stringResource(R.string.action_skip),
            onConfirm = {
                viewModel.skip(item)
                confirmSkip = null
            },
            onDismiss = { confirmSkip = null },
        )
    }
}

@Composable
private fun TodayList(
    state: TodayUiState,
    onTake: (TodayDose) -> Unit,
    onSkip: (TodayDose) -> Unit,
    onSnooze: (TodayDose) -> Unit,
    onMarkNotTaken: (TodayDose) -> Unit,
    onOpenMedication: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val is24 = is24HourClock()
    val groups = state.doses.groupBy { it.dose.scheduledAt }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.hasMedications) {
            item(key = "banners") {
                ReminderHealthBanners(remindersEnabled = state.remindersEnabled, usesAlarmMode = state.usesAlarmMode)
            }
        }
        item(key = "progress") { ProgressCard(state) }
        if (state.doses.isEmpty()) {
            item(key = "none") {
                EmptyState(
                    icon = Icons.Filled.EventAvailable,
                    title = stringResource(R.string.today_no_doses_title),
                    message = stringResource(R.string.today_no_doses_message),
                )
            }
        }
        groups.forEach { (scheduledAt, doses) ->
            item(key = "time-$scheduledAt") {
                Text(
                    text = Formats.time(scheduledAt, is24, locale),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(doses, key = { it.dose.id }) { item ->
                DoseCard(
                    item = item,
                    onTake = { onTake(item) },
                    onSkip = { onSkip(item) },
                    onSnooze = { onSnooze(item) },
                    onMarkNotTaken = { onMarkNotTaken(item) },
                    onOpen = { onOpenMedication(item.medication.id) },
                )
            }
        }
    }
}

@Composable
private fun ProgressCard(state: TodayUiState) {
    val res = appResources()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.progress_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = res.getQuantityString(R.plurals.progress_count, state.total, state.taken, state.total),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            LinearProgressIndicator(
                progress = { if (state.total == 0) 0f else state.taken.toFloat() / state.total },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
            )
            if (state.missed > 0 || state.skipped > 0) {
                Text(
                    text = stringResource(R.string.progress_details, state.missed, state.skipped),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun DoseCard(
    item: TodayDose,
    onTake: () -> Unit,
    onSkip: () -> Unit,
    onSnooze: () -> Unit,
    onMarkNotTaken: () -> Unit,
    onOpen: () -> Unit,
) {
    val res = appResources()
    val locale = currentLocale()
    val is24 = is24HourClock()
    val note = Labels.doseNote(res, item.dose.reminderNote, item.medication)
    val actionable = item.state == DoseDisplayState.UPCOMING || item.state == DoseDisplayState.DUE

    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        colors = if (item.state == DoseDisplayState.DUE) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f))
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = item.medication.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(text = Labels.doseLine(res, item.medication), style = MaterialTheme.typography.bodyLarge)
                    if (note != null) {
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val snoozedUntil = item.dose.snoozedUntil
                    if (actionable && snoozedUntil != null) {
                        Text(
                            text = stringResource(R.string.snoozed_until, Formats.time(snoozedUntil, is24, locale)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    DoseStatusBadge(
                        state = item.state,
                        takenAtText = item.dose.takenAt?.let { Formats.time(it, is24, locale) },
                    )
                    if (!actionable) {
                        CorrectionMenu(item = item, onMarkTaken = onTake, onMarkSkipped = onSkip, onMarkNotTaken = onMarkNotTaken)
                    }
                }
            }
            if (actionable) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onTake,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Text(stringResource(R.string.action_take), modifier = Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(
                        onClick = onSkip,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp),
                    ) {
                        Text(stringResource(R.string.action_skip))
                    }
                }
                if (item.state == DoseDisplayState.DUE) {
                    TextButton(
                        onClick = onSnooze,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Filled.Snooze, contentDescription = null)
                        Text(stringResource(R.string.action_snooze), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

/** Lets the user fix a recorded dose (e.g. taken but forgot to record, or tapped by mistake). */
@Composable
private fun CorrectionMenu(
    item: TodayDose,
    onMarkTaken: () -> Unit,
    onMarkSkipped: () -> Unit,
    onMarkNotTaken: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (item.state != DoseDisplayState.TAKEN) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_mark_taken)) },
                    onClick = {
                        expanded = false
                        onMarkTaken()
                    },
                )
            }
            if (item.state == DoseDisplayState.MISSED) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_mark_skipped)) },
                    onClick = {
                        expanded = false
                        onMarkSkipped()
                    },
                )
            }
            if (item.state == DoseDisplayState.TAKEN || item.state == DoseDisplayState.SKIPPED) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_mark_not_taken)) },
                    onClick = {
                        expanded = false
                        onMarkNotTaken()
                    },
                )
            }
        }
    }
}
