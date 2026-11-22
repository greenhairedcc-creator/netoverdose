package app.netoverdose.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

private data class Sample(
    override val id: Long,
    override val status: TaskStatus,
    override val deadline: Long,
    override val windowStart: Long = 0L
) : OpenTask

class TaskRulesTest {

    @Test
    fun emptyListHasNoCurrent() {
        assertNull(TaskRules.current(emptyList()))
    }

    @Test
    fun inProgressWinsOverEarlierPlanned() {
        val tasks = listOf(
            Sample(1, TaskStatus.PLANNED, 100),
            Sample(2, TaskStatus.IN_PROGRESS, 500),
            Sample(3, TaskStatus.IN_PROGRESS, 300)
        )
        assertEquals(3L, TaskRules.current(tasks)?.id)
    }

    @Test
    fun plannedFallbackUsesNearestDeadline() {
        val tasks = listOf(
            Sample(1, TaskStatus.PLANNED, 900),
            Sample(2, TaskStatus.PLANNED, 200),
            Sample(3, TaskStatus.DONE, 10)
        )
        assertEquals(2L, TaskRules.current(tasks)?.id)
    }

    @Test
    fun planExcludesCurrentAndClosed() {
        val tasks = listOf(
            Sample(1, TaskStatus.PLANNED, 900),
            Sample(2, TaskStatus.IN_PROGRESS, 200),
            Sample(3, TaskStatus.DONE, 10),
            Sample(4, TaskStatus.POSTPONED, 20)
        )
        assertEquals(listOf(1L), TaskRules.plan(tasks, 2L).map { it.id })
    }
}

class WindowProgressTest {

    @Test
    fun heightsAreThirtyTwo() {
        assertEquals(32, WindowProgress.HEIGHTS.size)
    }

    @Test
    fun fractionClampsOutsideWindow() {
        assertEquals(0f, WindowProgress.fraction(100, 200, 0), 0.0001f)
        assertEquals(1f, WindowProgress.fraction(100, 200, 999), 0.0001f)
        assertEquals(0.5f, WindowProgress.fraction(100, 200, 150), 0.0001f)
    }

    @Test
    fun degenerateWindowIsFullyPassed() {
        assertEquals(1f, WindowProgress.fraction(200, 200, 100), 0.0001f)
        assertEquals(32, WindowProgress.filledColumns(200, 200, 100))
    }

    @Test
    fun filledColumnsRound() {
        assertEquals(8, WindowProgress.filledColumns(0, 100, 25))
        assertEquals(0, WindowProgress.filledColumns(0, 100, 0))
        assertEquals(32, WindowProgress.filledColumns(0, 100, 100))
    }

    @Test
    fun burningByHoursAndByPart() {
        val hour = 3_600_000L
        assertTrue(WindowProgress.isBurning(0, 10 * hour, 0))
        assertFalse(WindowProgress.isBurning(0, 100 * hour, 0))
        assertTrue(WindowProgress.isBurning(0, 100 * hour, 80 * hour))
        assertTrue(WindowProgress.isBurning(0, 10 * hour, 20 * hour))
    }
}

class TimeTextTest {

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")

    @Test
    fun remainingSplitsDaysHoursMinutes() {
        val now = 0L
        val deadline = 3 * 86_400_000L + 4 * 3_600_000L
        val r = TimeText.remaining(deadline, now)
        assertEquals(3, r.days)
        assertEquals(4, r.hours)
        assertTrue(r.showDays)
        assertFalse(r.overdue)
    }

    @Test
    fun overdueIsFlagged() {
        val r = TimeText.remaining(0, 5 * 3_600_000L)
        assertTrue(r.overdue)
        assertEquals(5, r.hours)
        assertTrue(r.showHours)
    }

    @Test
    fun dayLabelIsRussianCapitalized() {
        val millis = ZonedDateTime.of(2026, 11, 20, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("Пт 20", TimeText.dayLabel(millis, zone))
        assertEquals("20.11", TimeText.dateShort(millis, zone))
        assertEquals("ПТ", TimeText.weekdayUpper(millis, zone))
    }

    @Test
    fun sizeSwitchesUnits() {
        assertFalse(TimeText.isMegabytes(84 * 1024))
        assertEquals("84", TimeText.sizeValue(84 * 1024))
        assertTrue(TimeText.isMegabytes(3_565_158))
        assertEquals("3,4", TimeText.sizeValue(3_565_158))
    }

    @Test
    fun extensionIsUppercaseAndBounded() {
        assertEquals("DOCX", TimeText.extension("essay-final.docx"))
        assertEquals("", TimeText.extension("noext"))
        assertEquals("TAR", TimeText.extension("archive.tar"))
    }
}

class DeadlineOptionsTest {

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")
    private val saturday = ZonedDateTime.of(2026, 11, 21, 10, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun todayEndsAt2359() {
        val end = ZonedDateTime.of(2026, 11, 21, 23, 59, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(end, DeadlineOptions.today(saturday, zone))
    }

    @Test
    fun tomorrowIsNextCalendarDay() {
        val end = ZonedDateTime.of(2026, 11, 22, 23, 59, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(end, DeadlineOptions.tomorrow(saturday, zone))
    }

    @Test
    fun nextMondayFromSaturday() {
        val end = ZonedDateTime.of(2026, 11, 23, 23, 59, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(end, DeadlineOptions.nextMonday(saturday, zone))
    }

    @Test
    fun nextMondayFromMondayJumpsWeek() {
        val monday = ZonedDateTime.of(2026, 11, 23, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
        val end = ZonedDateTime.of(2026, 11, 30, 23, 59, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(end, DeadlineOptions.nextMonday(monday, zone))
    }

    @Test
    fun utcPickerDateBecomesLocalEndOfDay() {
        val utcMidnight = ZonedDateTime.of(2026, 11, 25, 0, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        val end = ZonedDateTime.of(2026, 11, 25, 23, 59, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(end, DeadlineOptions.fromUtcDate(utcMidnight, zone))
    }

    @Test
    fun pickerRoundTripReturnsSameDay() {
        val date = java.time.LocalDate.of(2026, 11, 25)
        val utc = DeadlineOptions.toUtcDate(date)
        val localEnd = DeadlineOptions.fromUtcDate(utc, zone)
        assertEquals("25.11", TimeText.dateShort(localEnd, zone))
    }
}
