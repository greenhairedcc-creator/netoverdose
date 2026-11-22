package app.netoverdose.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

object DeadlineOptions {

    fun endOfDay(date: LocalDate, zone: ZoneId): Long =
        date.atTime(23, 59).atZone(zone).toInstant().toEpochMilli()

    fun today(now: Long, zone: ZoneId): Long =
        endOfDay(Instant.ofEpochMilli(now).atZone(zone).toLocalDate(), zone)

    fun tomorrow(now: Long, zone: ZoneId): Long =
        endOfDay(Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1), zone)

    /** Nearest weekday option: next Monday, never today. */
    fun nextMonday(now: Long, zone: ZoneId): Long =
        endOfDay(
            Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                .with(TemporalAdjusters.next(DayOfWeek.MONDAY)),
            zone
        )

    /** Material DatePicker returns UTC midnight; convert to local end of day. */
    fun fromUtcDate(utcMillis: Long, zone: ZoneId): Long =
        endOfDay(Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate(), zone)
}
