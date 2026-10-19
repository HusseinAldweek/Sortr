package com.sortr.app.ui.common

import android.content.Context
import android.text.format.Formatter
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** All user-visible numbers go through these so they follow the phone's language. */
fun formatBytes(context: Context, bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

fun formatCount(count: Int): String = NumberFormat.getIntegerInstance().format(count)

fun formatDate(epochMillis: Long): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
