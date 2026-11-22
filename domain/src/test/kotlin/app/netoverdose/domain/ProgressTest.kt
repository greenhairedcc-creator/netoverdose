package app.netoverdose.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressTest {

    private val day = 86_400_000L
    private val hour = 3_600_000L
    private val now = 1_800_000_000_000L

    private fun closed(
        id: Long,
        difficulty: Int,
        reserveDays: Double,
        daysAgo: Long = 1,
        delayHours: Double = 1.0
    ): ProgressTask {
        val closedAt = now - daysAgo * day
        val deadline = closedAt + (reserveDays * day).toLong()
        val windowStart = deadline - 4 * day
        return ProgressTask(
            id = id,
            difficulty = difficulty,
            windowStart = windowStart,
            deadline = deadline,
            status = TaskStatus.DONE,
            startedAt = windowStart + (delayHours * hour).toLong(),
            closedAt = closedAt
        )
    }

    private fun open(id: Long, difficulty: Int, deadline: Long, status: TaskStatus = TaskStatus.PLANNED) =
        ProgressTask(id, difficulty, now - day, deadline, status, null, null)

    @Test
    fun emptyHistoryHasNoData() {
        val summary = Progress.summarize(emptyList(), now)
        assertEquals(0, summary.closedCount)
        assertFalse(summary.enoughData)
        assertNull(summary.reserveDays)
        assertNull(summary.startDelayHours)
        assertEquals(PaceTone.NO_DATA, Progress.pace(summary).tone)
    }

    @Test
    fun twoClosedIsStillNotEnough() {
        val tasks = listOf(closed(1, 3, 2.0), closed(2, 2, 1.0))
        val summary = Progress.summarize(tasks, now)
        assertEquals(2, summary.closedCount)
        assertFalse(summary.enoughData)
        assertEquals(PaceTone.NO_DATA, Progress.pace(summary).tone)
    }

    @Test
    fun threeClosedIsEnough() {
        val tasks = listOf(closed(1, 3, 2.0), closed(2, 2, 1.0), closed(3, 1, 0.5))
        val summary = Progress.summarize(tasks, now)
        assertTrue(summary.enoughData)
        assertEquals(PaceTone.AHEAD, Progress.pace(summary).tone)
    }

    @Test
    fun reserveIsWeightedByDifficulty() {
        val tasks = listOf(closed(1, 5, 3.0), closed(2, 1, -1.0))
        val reserve = Progress.weightedReserveDays(tasks)!!
        assertEquals((3.0 * 5 + -1.0 * 1) / 6.0, reserve, 0.0001)
    }

    @Test
    fun oneHeavyTaskDominatesLightOnes() {
        val tasks = listOf(
            closed(1, 5, 4.0),
            closed(2, 1, -3.0),
            closed(3, 1, -3.0),
            closed(4, 1, -3.0)
        )
        // (4*5 + 3*(-3*1)) / 8 = 11/8 = 1.375, far above EPS_DAYS
        val pace = Progress.pace(Progress.summarize(tasks, now))
        assertEquals(PaceTone.AHEAD, pace.tone)
        assertEquals((4.0 * 5 - 3.0 * 3) / 8.0, pace.days, 0.0001)
    }

    @Test
    fun allOverdueMeansBehind() {
        val tasks = listOf(closed(1, 3, -2.0), closed(2, 2, -1.0), closed(3, 4, -3.0))
        val pace = Progress.pace(Progress.summarize(tasks, now))
        assertEquals(PaceTone.BEHIND, pace.tone)
        assertTrue(pace.days > 0.0)
    }

    @Test
    fun thresholdsAreInclusive() {
        val ahead = Progress.pace(ReserveSummary(3, Progress.EPS_DAYS + 0.001, 1.0, true))
        val onTimeLow = Progress.pace(ReserveSummary(3, -Progress.EPS_DAYS, 1.0, true))
        val onTimeHigh = Progress.pace(ReserveSummary(3, Progress.EPS_DAYS, 1.0, true))
        val behind = Progress.pace(ReserveSummary(3, -Progress.EPS_DAYS - 0.001, 1.0, true))
        assertEquals(PaceTone.AHEAD, ahead.tone)
        assertEquals(PaceTone.ON_TIME, onTimeLow.tone)
        assertEquals(PaceTone.ON_TIME, onTimeHigh.tone)
        assertEquals(PaceTone.BEHIND, behind.tone)
    }

    @Test
    fun oldClosedTasksFallOutOfWorkingWindow() {
        val tasks = listOf(closed(1, 3, 2.0, daysAgo = 20), closed(2, 3, 2.0, daysAgo = 1))
        val summary = Progress.summarize(tasks, now)
        assertEquals(1, summary.closedCount)
        assertFalse(summary.enoughData)
    }

    @Test
    fun openAndPostponedAreNotCounted() {
        val tasks = listOf(
            open(1, 3, now + day),
            open(2, 3, now + day, TaskStatus.POSTPONED),
            open(3, 3, now + day, TaskStatus.IN_PROGRESS)
        )
        assertEquals(0, Progress.summarize(tasks, now).closedCount)
    }

    @Test
    fun weightedMedianUsesWeights() {
        val tasks = listOf(
            closed(1, 1, 1.0, delayHours = 1.0),
            closed(2, 5, 1.0, delayHours = 8.0)
        )
        assertEquals(8.0, Progress.weightedMedianStartHours(tasks)!!, 0.0001)
    }

    @Test
    fun medianFallsBackWhenNothingStarted() {
        val task = closed(1, 3, 1.0).copy(startedAt = null)
        assertNull(Progress.weightedMedianStartHours(listOf(task)))
    }

    @Test
    fun openOverdueReserveIsNegative() {
        val task = open(1, 3, now - 2 * day)
        assertEquals(-2.0, Progress.reserveDays(task, now), 0.0001)
    }

    @Test
    fun dailyReservesHasFourteenBucketsOldestFirst() {
        val tasks = listOf(closed(1, 3, 1.5, daysAgo = 0), closed(2, 3, -1.0, daysAgo = 1))
        val buckets = Progress.dailyReserves(tasks, now) { millis -> millis - millis % day }
        assertEquals(14, buckets.size)
        assertTrue(buckets.first().dayStart <= buckets.last().dayStart)
        assertEquals(1, buckets.last().closedCount)
        assertEquals(1, buckets[buckets.size - 2].closedCount)
        assertNull(buckets.first().averageReserveDays)
    }

    @Test
    fun emptyDayHasNullAverage() {
        val buckets = Progress.dailyReserves(emptyList(), now) { it - it % day }
        assertTrue(buckets.all { it.averageReserveDays == null && it.closedCount == 0 })
    }

    @Test
    fun metricsSharesAreComputed() {
        val tasks = listOf(
            closed(1, 3, 1.0, delayHours = 0.1),
            closed(2, 3, -1.0, delayHours = 5.0),
            closed(3, 3, 2.0, delayHours = 0.2)
        )
        val metrics = Progress.metrics(tasks, now)
        assertEquals(2.0 / 3.0, metrics.startedFastShare!!, 0.0001)
        assertEquals(2.0 / 3.0, metrics.closedWithReserveShare!!, 0.0001)
    }

    @Test
    fun metricsOnEmptyHistoryAreNull() {
        val metrics = Progress.metrics(emptyList(), now)
        assertNull(metrics.startedFastShare)
        assertNull(metrics.closedWithReserveShare)
    }

    @Test
    fun difficultyIsClampedIntoWeight() {
        val tasks = listOf(closed(1, 0, 2.0), closed(2, 99, 2.0))
        assertEquals(2.0, Progress.weightedReserveDays(tasks)!!, 0.0001)
    }
}
