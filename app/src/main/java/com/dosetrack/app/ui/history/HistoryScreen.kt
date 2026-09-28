package com.dosetrack.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.dosetrack.app.data.model.MedicationStatus
import com.dosetrack.app.domain.AdherenceStats
import com.dosetrack.app.domain.DoseDisplayState
import com.dosetrack.app.ui.Formats
import com.dosetrack.app.ui.Labels
import com.dosetrack.app.ui.appResources
import com.dosetrack.app.ui.components.DatePickerModal
import com.dosetrack.app.ui.components.DoseStatusBadge
import com.dosetrack.app.ui.components.EmptyState
import com.dosetrack.app.ui.components.StatusBadge
import com.dosetrack.app.ui.components.TimePickerDialog
import com.dosetrack.app.ui.currentLocale
import com.dosetrack.app.ui.is24HourClock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    var correcting by remember { mutableStateOf<HistoryRow?>(null) }
    var pickingTakenTime by remember { mutableStateOf<HistoryRow?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.history_title)) }) },
    ) { padding ->
        if (!state.loading) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "week") {
                    WeeklyCard(state = state, onSelectDate = viewModel::selectDate)
                }
                item(key = "navigator") {
                    DateNavigator(
                        state = state,
                        onPrevious = viewModel::previousDay,
                        onNext = viewModel::nextDay,
                        onPickDate = { showDatePicker = true },
                        onToday = viewModel::goToToday,
                    )
                }
                if (state.rows.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            icon = Icons.Filled.EventBusy,
                            title = stringResource(R.string.history_empty_day),
                            message = stringResource(R.string.history_empty_day_message),
                        )
                    }
                }
                items(state.rows, key = { it.dose.id }) { row ->
                    HistoryRowCard(
                        row = row,
                        onClick = if (row.state != DoseDisplayState.UPCOMING) {
                            { correcting = row }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerModal(
            initialDate = state.selectedDate,
            onConfirm = { date ->
                viewModel.selectDate(date)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }

    correcting?.let { row ->
        ModalBottomSheet(onDismissRequest = { correcting = null }) {
            CorrectionSheet(
                row = row,
                onTaken = {
                    correcting = null
                    pickingTakenTime = row
                },
                onSkipped = {
                    correcting = null
                    viewModel.correct(row, Correction.Skipped)
                },
                onMissed = {
                    correcting = null
                    viewModel.correct(row, Correction.Missed)
                },
            )
        }
    }

    pickingTakenTime?.let { row ->
        val zone = ZoneId.systemDefault()
        val scheduled = Instant.ofEpochMilli(row.dose.scheduledAt).atZone(zone)
        TimePickerDialog(
            initialMinutes = scheduled.hour * 60 + scheduled.minute,
            onConfirm = { minutes ->
                val takenAt = scheduled.toLocalDate()
                    .atTime(LocalTime.of(minutes / 60, minutes % 60))
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
                    // A time later today than now can't have happened yet.
                    .coerceAtMost(System.currentTimeMillis())
                viewModel.correct(row, Correction.Taken(takenAt))
                pickingTakenTime = null
            },
            onDismiss = { pickingTakenTime = null },
        )
    }
}

@Composable
private fun WeeklyCard(state: HistoryUiState, onSelectDate: (java.time.LocalDate) -> Unit) {
    val stats = state.weekStats
    val description = weekDescription(stats)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.week_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stats.percent?.let { stringResource(R.string.percent_value, it) } ?: "—",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = if (stats.total == 0) {
                    stringResource(R.string.adherence_none)
                } else {
                    stringResource(R.string.adherence_counts, stats.taken, stats.missed, stats.skipped)
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            WeekChart(
                days = state.week,
                selectedDate = state.selectedDate,
                description = description,
                onSelectDate = onSelectDate,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = stringResource(R.string.adherence_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun weekDescription(stats: AdherenceStats): String =
    if (stats.percent == null) {
        stringResource(R.string.adherence_none)
    } else {
        stringResource(R.string.week_chart_description, stats.percent!!, stats.taken, stats.missed, stats.skipped)
    }

@Composable
private fun DateNavigator(
    state: HistoryUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPickDate: () -> Unit,
    onToday: () -> Unit,
) {
    val locale = currentLocale()
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.history_previous_day))
            }
            TextButton(
                onClick = onPickDate,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
            ) {
                Text(
                    text = Formats.dayHeader(state.selectedDate, locale),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            IconButton(onClick = onNext) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.history_next_day))
            }
        }
        if (state.selectedDate != state.today) {
            TextButton(onClick = onToday) { Text(stringResource(R.string.history_today)) }
        }
    }
}

@Composable
private fun HistoryRowCard(row: HistoryRow, onClick: (() -> Unit)?) {
    val res = appResources()
    val locale = currentLocale()
    val is24 = is24HourClock()
    val note = Labels.doseNote(res, row.dose.reminderNote, row.medication)
    val tag = when (row.medication.status) {
        MedicationStatus.ARCHIVED -> stringResource(R.string.status_archived)
        MedicationStatus.PAUSED -> stringResource(R.string.status_paused)
        MedicationStatus.ACTIVE -> null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Text(
                text = Formats.time(row.dose.scheduledAt, is24, locale),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = row.medication.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (tag != null) {
                        StatusBadge(
                            text = tag,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                Text(
                    text = Labels.doseLine(res, row.medication),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (note != null) {
                    Text(text = note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DoseStatusBadge(
                    state = row.state,
                    takenAtText = row.dose.takenAt?.let { Formats.time(it, is24, locale) },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun CorrectionSheet(
    row: HistoryRow,
    onTaken: () -> Unit,
    onSkipped: () -> Unit,
    onMissed: () -> Unit,
) {
    Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
        Text(
            text = stringResource(R.string.correct_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Text(
            text = row.medication.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        if (row.state != DoseDisplayState.TAKEN) {
            SheetAction(stringResource(R.string.action_mark_taken), onTaken)
        } else {
            SheetAction(stringResource(R.string.action_change_taken_time), onTaken)
        }
        if (row.state != DoseDisplayState.SKIPPED) SheetAction(stringResource(R.string.action_mark_skipped), onSkipped)
        if (row.state != DoseDisplayState.MISSED) SheetAction(stringResource(R.string.action_mark_missed), onMissed)
    }
}

@Composable
private fun SheetAction(label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick),
    )
}
