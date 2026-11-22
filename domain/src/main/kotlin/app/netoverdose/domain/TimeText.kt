package app.netoverdose.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

data class Remaining(
    val days: Int,
    val hours: Int,
    val minutes: Int,
    val overdue: Boolean
) {
    val showDays: Boolean get() = days > 0
    val showHours: Boolean get() = days == 0 && hours > 0
}

object TimeText {

    private val ru: Locale = Locale.forLanguageTag("ru")
    private val weekdayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", ru)

    private const val DAY_MS = 86_400_000L
    private const val HOUR_MS = 3_600_000L
    private const val MINUTE_MS = 60_000L

    fun remaining(deadline: Long, now: Long): Remaining {
        val diff = deadline - now
        val value = abs(diff)
        return Remaining(
            days = (value / DAY_MS).toInt(),
            hours = ((value % DAY_MS) / HOUR_MS).toInt(),
            minutes = ((value % HOUR_MS) / MINUTE_MS).toInt(),
            overdue = diff < 0L
        )
    }

    /** "Пт 20" */
    fun dayLabel(millis: Long, zone: ZoneId): String {
        val date = Instant.ofEpochMilli(millis).atZone(zone)
        val weekday = date.format(weekdayFormat).replace(".", "")
        return weekday.replaceFirstChar { it.uppercase(ru) } + " " + date.dayOfMonth
    }

    /** "21.11" */
    fun dateShort(millis: Long, zone: ZoneId): String {
        val date = Instant.ofEpochMilli(millis).atZone(zone)
        return String.format(ru, "%02d.%02d", date.dayOfMonth, date.monthValue)
    }

    /** "СБ" */
    fun weekdayUpper(millis: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(millis).atZone(zone).format(weekdayFormat).replace(".", "").uppercase(ru)

    fun isMegabytes(bytes: Long): Boolean = bytes >= 1_048_576L

    fun sizeValue(bytes: Long): String =
        if (isMegabytes(bytes)) String.format(ru, "%.1f", bytes / 1_048_576.0)
        else (bytes / 1024L).toString()

    fun extension(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot in 1 until name.length - 1) name.substring(dot + 1).uppercase(ru).take(5) else ""
    }
}
