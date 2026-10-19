package com.sortr.app.ui.common

import android.content.Context
import android.text.format.DateFormat
import android.text.format.Formatter
import java.text.NumberFormat
import java.time.Instant
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** All user-visible numbers go through these so they follow the phone's language. */
fun formatBytes(context: Context, bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

fun formatCount(count: Int): String = NumberFormat.getIntegerInstance().format(count)

fun formatDate(epochMillis: Long): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .localDigits()
        .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

/** "0:42" or "1:03:15". */
fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

/** Month name on its own, e.g. "March", in the phone's language. */
fun formatMonth(month: Int): String =
    Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())

/** Month and year, e.g. "March 2024", in the phone's language. */
fun formatMonthYear(year: Int, month: Int): String =
    DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMMyyyy").let { pattern ->
        DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).localDigits().format(YearMonth.of(year, month))
    }

/** A year as written in the phone's language (no thousands separator). */
fun formatYear(year: Int): String =
    DateTimeFormatter.ofPattern("yyyy", Locale.getDefault()).localDigits().format(YearMonth.of(year, 1))

/** java.time prints 0-9 by default; this uses the phone's own digits, e.g. Arabic-Indic ones. */
private fun DateTimeFormatter.localDigits(): DateTimeFormatter =
    withDecimalStyle(DecimalStyle.of(Locale.getDefault()))

/** The year a moment falls in, in the phone's time zone. */
fun yearOf(epochMillis: Long): Int = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).year
