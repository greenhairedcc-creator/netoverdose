package app.netoverdose.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonTest {

    private val day = 86_400_000L
    private val hour = 3_600_000L
    private val now = 1_800_000_000_000L
    private val anchor = now - 30 * day

    /** Identity startOfDay keeps the test timezone-free. */
    private val startOfDay: (Long) -> Long = { millis -> millis - Math.floorMod(millis, day) }

    private fun closed(id: Long, difficulty: Int, closedAt: Long, reserveDays: Double, startedAt: Long? = null): ProgressTask {
        val deadline = closedAt + (reserveDays * day).toLong()
        return ProgressTask(
            id = id,
            difficulty = difficulty,
            windowStart = deadline - 4 * day,
            deadline = deadline,
            status = TaskStatus.DONE,
            startedAt = startedAt,
            closedAt = closedAt
        )
    }

    @Test
    fun seasonIndexCountsFullWindowsFromAnchor() {
        assertEquals(0, SeasonRules.of(anchor + day, anchor))
        assertEquals(1, SeasonRules.of(anchor + 28 * day, anchor))
        assertEquals(1, SeasonRules.of(anchor + 40 * day, anchor))
    }

    @Test
    fun nowBeforeAnchorIsFirstSeason() {
        assertEquals(0, SeasonRules.of(anchor - 5 * day, anchor))
    }

    @Test
    fun boundsAreTwentyEightDaysApart() {
        val (start, end) = SeasonRules.bounds(1, anchor)
        assertEquals(anchor + 28 * day, start)
        assertEquals(end - start, 28 * day)
    }

    @Test
    fun statsCountOnlyTasksInsideTheSeason() {
        val (start, end) = SeasonRules.bounds(1, anchor)
        val inside = closed(1, 4, start + 2 * day, 1.0)
        val outside = closed(2, 5, end + day, 1.0)
        val stats = SeasonRules.stats(listOf(inside, outside), now, anchor, 1, startOfDay)
        assertEquals(1, stats.closedCount)
        assertEquals(4, stats.weight)
    }

    @Test
    fun emptySeasonHasNoReserveOrMedian() {
        val stats = SeasonRules.stats(emptyList(), now, anchor, 1, startOfDay)
        assertNull(stats.reserveDays)
        assertNull(stats.medianStartHours)
        assertEquals(0, stats.closedCount)
    }

    @Test
    fun gridHasTwentyEightDays() {
        val stats = SeasonRules.stats(emptyList(), now, anchor, 1, startOfDay)
        assertEquals(28, stats.days.size)
    }

    @Test
    fun burnedDeadlineIsCounted() {
        val (start, _) = SeasonRules.bounds(1, anchor)
        // Season 1 starts at +28 days while `now` sits part-way into day +30, so a deadline
        // at start + 1 day is safely in the past.
        val deadline = start + day
        assertTrue(deadline < now)
        val missed = ProgressTask(
            id = 9, difficulty = 3, windowStart = deadline - 4 * day, deadline = deadline,
            status = TaskStatus.PLANNED, startedAt = null, closedAt = null
        )
        val stats = SeasonRules.stats(listOf(missed), now, anchor, 1, startOfDay)
        assertEquals(1, stats.days.sumOf { it.burned })
    }

    @Test
    fun futureDeadlineIsNotBurned() {
        val deadline = now + 2 * day
        val upcoming = ProgressTask(
            id = 9, difficulty = 3, windowStart = deadline - 4 * day, deadline = deadline,
            status = TaskStatus.PLANNED, startedAt = null, closedAt = null
        )
        val stats = SeasonRules.stats(listOf(upcoming), now, anchor, 1, startOfDay)
        assertEquals(0, stats.days.sumOf { it.burned })
    }

    @Test
    fun pulledFromFireCountsLateStartClosedOnTime() {
        val (start, _) = SeasonRules.bounds(1, anchor)
        val closedAt = start + 5 * day
        val deadline = closedAt + day
        // window is 4 days; started 1 hour before the deadline -> burning
        val task = ProgressTask(
            id = 3, difficulty = 3, windowStart = deadline - 4 * day, deadline = deadline,
            status = TaskStatus.DONE, startedAt = deadline - hour, closedAt = closedAt
        )
        val stats = SeasonRules.stats(listOf(task), now, anchor, 1, startOfDay)
        assertEquals(1, stats.pulledFromFire)
    }

    @Test
    fun deltaComparesAgainstPrevious() {
        val current = Season(1, 0, 0, 3, 5, 20, 1.5, 4.0, 2, emptyList())
        val previous = Season(0, 0, 0, 0, 4, 16, 1.0, 6.0, 1, emptyList())
        val delta = SeasonRules.delta(current, previous)
        assertEquals(0.5, delta.reserve!!, 0.0001)
        assertEquals(4, delta.weight)
        assertEquals(-2.0, delta.medianStart!!, 0.0001)
    }

    @Test
    fun deltaIsNullWhenPreviousHasNoData() {
        val current = Season(1, 0, 0, 3, 5, 20, 1.5, 4.0, 2, emptyList())
        val previous = Season(0, 0, 0, 0, 0, 0, null, null, 0, emptyList())
        val delta = SeasonRules.delta(current, previous)
        assertNull(delta.reserve)
        assertNull(delta.medianStart)
    }

    @Test
    fun maxWeightNeverZero() {
        val stats = SeasonRules.stats(emptyList(), now, anchor, 1, startOfDay)
        assertTrue(stats.maxWeight >= 1)
    }


    @Test
    fun gridCoversEveryDayOfTheSeason() {
        val (start, end) = SeasonRules.bounds(1, anchor)
        val stats = SeasonRules.stats(emptyList(), now, anchor, 1, startOfDay)
        assertEquals(startOfDay(start), stats.days.first().dayStart)
        // 28 buckets of one day each cover the season start
        assertTrue(stats.days.first().dayStart + 28 * day >= end - day)
    }
}
