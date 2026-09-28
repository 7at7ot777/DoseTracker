package com.dosetrack.app.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dosetrack.app.domain.DayAdherence
import com.dosetrack.app.ui.currentLocale
import java.time.LocalDate
import java.time.format.TextStyle

/**
 * One stacked bar per day: taken (primary), skipped (outline), missed (error), bottom to top,
 * scaled to that day's recorded doses. Tapping a day selects it.
 */
@Composable
fun WeekChart(
    days: List<DayAdherence>,
    selectedDate: LocalDate,
    description: String,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val colors = MaterialTheme.colorScheme
    val takenColor = colors.primary
    val skippedColor = colors.outline
    val missedColor = colors.error
    val trackColor = colors.surfaceVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        days.forEach { day ->
            val selected = day.date == selectedDate
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelectDate(day.date) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Canvas(
                    modifier = Modifier
                        .height(88.dp)
                        .width(18.dp),
                ) {
                    val radius = CornerRadius(size.width / 2, size.width / 2)
                    drawRoundRect(color = trackColor, cornerRadius = radius)
                    val recorded = day.taken + day.skipped + day.missed
                    if (recorded > 0) {
                        var bottom = size.height
                        listOf(
                            day.taken to takenColor,
                            day.skipped to skippedColor,
                            day.missed to missedColor,
                        ).forEach { (count, color) ->
                            if (count > 0) {
                                val segment = size.height * count / recorded
                                drawRoundRect(
                                    color = color,
                                    topLeft = Offset(0f, bottom - segment),
                                    size = Size(size.width, segment),
                                    cornerRadius = radius,
                                )
                                bottom -= segment
                            }
                        }
                    }
                }
                Text(
                    text = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) colors.primary else colors.onSurfaceVariant,
                )
            }
        }
    }
}
