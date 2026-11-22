package app.netoverdose.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DelayCostTest {

    private val day = 86_400_000L
    private val hour = 3_600_000L
    private val now = 1_800_000_000_000L

    private fun open(difficulty: Int, windowDays: Long, leftDays: Long, startedAt: Long? = null) =
        ProgressTask(
            id = 1,
            difficulty = difficulty,
            windowStart = now - (windowDays - leftDays) * day,
            deadline = now + leftDays * day,
            status = TaskStatus.PLANNED,
            startedAt = startedAt,
            closedAt = null
        )

    private fun closedLateStart() = ProgressTask(
        id = 2, difficulty = 3,
        windowStart = now - 10 * day,
        deadline = now - 5 * day,
        status = TaskStatus.DONE,
        startedAt = now - 5 * day - hour,
        closedAt = now - 6 * day
    )

    @Test
    fun idleHoursCountFromWindowStartWhenNotStarted() {
        val cost = Progress.delayCost(open(3, 4, 2), now)
        assertEquals(48.0, cost.idleHours, 0.001)
    }

    @Test
    fun idleHoursCountFromStartWhenStarted() {
        val cost = Progress.delayCost(open(3, 4, 2, startedAt = now - 3 * hour), now)
        assertEquals(3.0, cost.idleHours, 0.001)
    }

    @Test
    fun currentLoadExceedsBaseAsDeadlineApproaches() {
        val cost = Progress.delayCost(open(4, 4, 1), now)
        assertEquals(1.5, cost.baseLoadPerDay, 0.001)   // 4*1.5 / 4
        assertEquals(6.0, cost.currentLoadPerDay, 0.001) // 4*1.5 / 1
        assertTrue(cost.rush.not())                      // exactly 6 is not "more than 6"
    }

    @Test
    fun rushWhenOverSixHoursPerDay() {
        val cost = Progress.delayCost(open(5, 4, 1), now)
        assertTrue(cost.rush)
    }

    @Test
    fun chanceDropsWithIdleTime() {
        // Same task; starting it now resets the idle clock and must raise the chance.
        val notStarted = Progress.delayCost(open(3, 4, 2), now)
        val justStarted = Progress.delayCost(open(3, 4, 2, startedAt = now), now)
        assertTrue(notStarted.idleHours > justStarted.idleHours)
        assertTrue(justStarted.chance > notStarted.chance)
    }

    @Test
    fun chanceIsClamped() {
        val idle = Progress.delayCost(open(1, 10, 1, startedAt = now - 100 * hour), now)
        assertTrue(idle.chance >= 0.15)
        assertTrue(idle.chance <= 0.9)
    }

    @Test
    fun pulledFromFireCountsBurningStartClosedOnTime() {
        assertEquals(1, Progress.pulledFromFire(listOf(closedLateStart())))
    }

    @Test
    fun pulledFromFireIgnoresCalmStarts() {
        val calm = ProgressTask(
            id = 3, difficulty = 2,
            windowStart = now - 10 * day, deadline = now - day,
            status = TaskStatus.DONE, startedAt = now - 9 * day, closedAt = now - 2 * day
        )
        assertEquals(0, Progress.pulledFromFire(listOf(calm)))
    }

    @Test
    fun poolOrdersByChronicShare() {
        val onTime = ProgressTask(1, 3, now - 10 * day, now - 2 * day, TaskStatus.DONE, null, now - 5 * day)
        val late = ProgressTask(2, 3, now - 10 * day, now - 6 * day, TaskStatus.DONE, null, now - 2 * day)
        val pool = Progress.pool(
            groups = mapOf("PKT" to listOf(onTime), "DOCX" to listOf(late)),
            now = now,
            postponedCounts = emptyMap()
        )
        assertEquals("DOCX", pool.first().key)
        assertTrue(pool.first().chronicShare > pool.last().chronicShare)
    }

    @Test
    fun poolReserveIsNullWithoutClosedTasks() {
        val openTask = ProgressTask(1, 3, now - day, now + day, TaskStatus.PLANNED, null, null)
        val entry = Progress.pool(mapOf("PDF" to listOf(openTask)), now, emptyMap()).single()
        assertEquals(null, entry.reserveDays)
        assertEquals(0.0, entry.chronicShare, 0.0001)
    }

    @Test
    fun postponedCountsRaiseChronicShare() {
        val task = ProgressTask(1, 3, now - day, now + day, TaskStatus.POSTPONED, null, null)
        val plain = Progress.pool(mapOf("PDF" to listOf(task)), now, emptyMap()).single()
        val postponed = Progress.pool(mapOf("PDF" to listOf(task)), now, mapOf("PDF" to 3)).single()
        assertTrue(postponed.chronicShare > plain.chronicShare)
        assertFalse(postponed.chronicShare > 1.0)
    }
}
