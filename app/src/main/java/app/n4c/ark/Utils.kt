package app.n4c.ark

import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun formatSize(b: Long): String = when {
    b >= 1L shl 30 -> String.format(Locale.US, "%.2f GB", b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", b / (1L shl 20).toDouble())
    b >= 1L shl 10 -> String.format(Locale.US, "%.0f KB", b / (1L shl 10).toDouble())
    else -> "$b B"
}

fun formatDate(ms: Long): String =
    if (ms <= 0) "-" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))
