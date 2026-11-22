package app.netoverdose.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRulesSortTest {

    private val day = 86_400_000L
    private val hour = 3_600_000L
    private val now = 1_800_000_000_000L

    private fun row(
        id: Long,
        title: String = "Задача $id",
        difficulty: Int = 3,
        daysLeft: Long = 3,
        status: TaskStatus = TaskStatus.PLANNED,
        orderIndex: Int = 0
    ) = PlanRow(
        id = id,
        title = title,
        difficulty = difficulty,
        deadline = now + daysLeft * day,
        windowStart = now - day,
        status = status,
        orderIndex = orderIndex
    )

    @Test
    fun sortsByDeadline() {
        val rows = listOf(row(1, daysLeft = 5), row(2, daysLeft = 1), row(3, daysLeft = 3))
        assertEquals(listOf(2L, 3L, 1L), TaskRules.planRows(rows, now).map { it.id })
    }

    @Test
    fun sortsByDifficultyDescendingThenDeadline() {
        val rows = listOf(
            row(1, difficulty = 2, daysLeft = 1),
            row(2, difficulty = 5, daysLeft = 9),
            row(3, difficulty = 5, daysLeft = 2)
        )
        assertEquals(listOf(3L, 2L, 1L), TaskRules.planRows(rows, now, sort = TaskSort.DIFFICULTY).map { it.id })
    }

    @Test
    fun sortsByTitleCaseInsensitive() {
        val rows = listOf(row(1, "эссе"), row(2, "Лабораторная"), row(3, "доклад"))
        assertEquals(listOf(3L, 2L, 1L), TaskRules.planRows(rows, now, sort = TaskSort.TITLE).map { it.id })
    }

    @Test
    fun sortsByManualOrder() {
        val rows = listOf(
            row(1, orderIndex = 2),
            row(2, orderIndex = 0),
            row(3, orderIndex = 1)
        )
        assertEquals(listOf(2L, 3L, 1L), TaskRules.planRows(rows, now, sort = TaskSort.MANUAL).map { it.id })
    }

    @Test
    fun filtersBurningOnly() {
        // window is 1 day long, so anything left under 6h is burning
        val burning = row(1, daysLeft = 0).copy(deadline = now + 2 * hour)
        val calm = row(2, daysLeft = 5)
        assertEquals(
            listOf(1L),
            TaskRules.planRows(listOf(burning, calm), now, filter = TaskFilter.BURNING).map { it.id }
        )
    }

    @Test
    fun filtersNextWeekOnly() {
        val rows = listOf(row(1, daysLeft = 2), row(2, daysLeft = 10))
        assertEquals(
            listOf(1L),
            TaskRules.planRows(rows, now, filter = TaskFilter.WEEK).map { it.id }
        )
    }

    @Test
    fun searchMatchesTitleCaseInsensitive() {
        val rows = listOf(row(1, "Лабораторная OSPF"), row(2, "Эссе про этику"))
        assertEquals(
            listOf(1L),
            TaskRules.planRows(rows, now, query = "ospf").map { it.id }
        )
    }

    @Test
    fun closedTasksAreNeverListed() {
        val rows = listOf(row(1, status = TaskStatus.DONE), row(2))
        assertEquals(listOf(2L), TaskRules.planRows(rows, now).map { it.id })
    }

    @Test
    fun postponedTasksAreNotListed() {
        val rows = listOf(row(1, status = TaskStatus.POSTPONED), row(2))
        assertEquals(listOf(2L), TaskRules.planRows(rows, now).map { it.id })
    }

    @Test
    fun heroesReturnAllRunningTasks() {
        val tasks = listOf(
            Sample(1, TaskStatus.IN_PROGRESS, 500, 0),
            Sample(2, TaskStatus.IN_PROGRESS, 300, 0),
            Sample(3, TaskStatus.PLANNED, 100, 0)
        )
        assertEquals(listOf(2L, 1L), TaskRules.heroes(tasks).map { it.id })
    }

    @Test
    fun heroesFallBackToNearestPlanned() {
        val tasks = listOf(
            Sample(1, TaskStatus.PLANNED, 900, 0),
            Sample(2, TaskStatus.PLANNED, 200, 0)
        )
        assertEquals(listOf(2L), TaskRules.heroes(tasks).map { it.id })
    }

    @Test
    fun reorderProducesSequentialIndexes() {
        val rows = listOf(row(1, orderIndex = 0), row(2, orderIndex = 1), row(3, orderIndex = 2))
        val mapping = TaskRules.reorder(rows, listOf(3L, 1L, 2L))
        assertEquals(0, mapping[3L])
        assertEquals(1, mapping[1L])
        assertEquals(2, mapping[2L])
    }

    @Test
    fun reorderIgnoresUnknownIds() {
        val rows = listOf(row(1), row(2))
        val mapping = TaskRules.reorder(rows, listOf(2L, 99L, 1L))
        assertTrue(mapping.containsKey(2L))
        assertTrue(mapping.containsKey(1L))
        assertEquals(2, mapping.size)
    }

    private data class Sample(
        override val id: Long,
        override val status: TaskStatus,
        override val deadline: Long,
        override val windowStart: Long
    ) : OpenTask
}
