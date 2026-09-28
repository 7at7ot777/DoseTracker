package com.dosetrack.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dosetrack.app.R
import com.dosetrack.app.domain.DoseDisplayState

/** Colour-coded dose status; the text always names the state so colour is never the only cue. */
@Composable
fun DoseStatusBadge(
    state: DoseDisplayState,
    modifier: Modifier = Modifier,
    takenAtText: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (state) {
        DoseDisplayState.UPCOMING -> colors.secondaryContainer to colors.onSecondaryContainer
        DoseDisplayState.DUE -> colors.tertiaryContainer to colors.onTertiaryContainer
        DoseDisplayState.TAKEN -> colors.primaryContainer to colors.onPrimaryContainer
        DoseDisplayState.SKIPPED -> colors.surfaceVariant to colors.onSurfaceVariant
        DoseDisplayState.MISSED -> colors.errorContainer to colors.onErrorContainer
    }
    val text = when (state) {
        DoseDisplayState.UPCOMING -> stringResource(R.string.state_upcoming)
        DoseDisplayState.DUE -> stringResource(R.string.state_due)
        DoseDisplayState.TAKEN -> if (takenAtText != null) {
            stringResource(R.string.state_taken_at, takenAtText)
        } else {
            stringResource(R.string.state_taken)
        }
        DoseDisplayState.SKIPPED -> stringResource(R.string.state_skipped)
        DoseDisplayState.MISSED -> stringResource(R.string.state_missed)
    }
    StatusBadge(text = text, containerColor = container, contentColor = content, modifier = modifier)
}
