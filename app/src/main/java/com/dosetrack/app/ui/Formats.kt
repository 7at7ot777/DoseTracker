package com.dosetrack.app.ui

import android.content.res.Resources
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.dosetrack.app.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formats {
    fun time(minutesOfDay: Int, is24Hour: Boolean, locale: Locale): String =
        time(LocalTime.of(minutesOfDay / 60, minutesOfDay % 60), is24Hour, locale)

    fun time(time: LocalTime, is24Hour: Boolean, locale: Locale): String =
        time.format(DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", locale))

    fun time(epochMillis: Long, is24Hour: Boolean, locale: Locale, zone: ZoneId = ZoneId.systemDefault()): String =
        time(Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime(), is24Hour, locale)

    /** "October 4", or "October 4, 2027" when not in the current year. */
    fun date(date: LocalDate, locale: Locale): String {
        val pattern = if (date.year == LocalDate.now().year) "MMMM d" else "MMMM d, yyyy"
        return date.format(DateTimeFormatter.ofPattern(pattern, locale))
    }

    /** "Mon, September 28" for day headers. */
    fun dayHeader(date: LocalDate, locale: Locale): String {
        val pattern = if (date.year == LocalDate.now().year) "EEE, MMMM d" else "EEE, MMMM d, yyyy"
        return date.format(DateTimeFormatter.ofPattern(pattern, locale))
    }

    /** "Today, 10:00 PM", "Tomorrow, 8:00 AM" or "October 4, 8:00 AM". */
    fun relativeDateTime(
        res: Resources,
        epochMillis: Long,
        is24Hour: Boolean,
        locale: Locale,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val dateTime = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val day = dateTime.toLocalDate()
        val today = LocalDate.now(zone)
        val time = time(dateTime.toLocalTime(), is24Hour, locale)
        return when (day) {
            today -> res.getString(R.string.today_at, time)
            today.plusDays(1) -> res.getString(R.string.tomorrow_at, time)
            else -> res.getString(R.string.date_at, date(day, locale), time)
        }
    }
}

@Composable
fun is24HourClock(): Boolean = DateFormat.is24HourFormat(LocalContext.current)
