package app.netoverdose.domain

enum class TaskStatus { PLANNED, IN_PROGRESS, DONE, POSTPONED }

enum class StepSource { USER, LLM }

enum class EventType { CREATED, STARTED, STEP_DONE, CLOSED, POSTPONED, REOPENED, NOTE, EDITED }

/** Minimal projection used by pure selection rules. */
interface OpenTask {
    val id: Long
    val status: TaskStatus
    val deadline: Long
    val windowStart: Long
}

/** Sort orders offered by the plan list selector. */
enum class TaskSort { DEADLINE, DIFFICULTY, TITLE, MANUAL }

/** Built-in filters offered as view chips above the plan list. */
enum class TaskFilter { ALL, BURNING, WEEK }

/** Row used by the plan list, carrying the fields sort and filter need. */
data class PlanRow(
    val id: Long,
    val title: String,
    val difficulty: Int,
    val deadline: Long,
    val windowStart: Long,
    val status: TaskStatus,
    val orderIndex: Int
)

private const val DAY_MS = 86_400_000L

object TaskRules {

    /** "Now at work": earliest IN_PROGRESS, otherwise earliest PLANNED. */
    fun current(tasks: List<OpenTask>): OpenTask? {
        val running = tasks.filter { it.status == TaskStatus.IN_PROGRESS }.minByOrNull { it.deadline }
        if (running != null) return running
        return tasks.filter { it.status == TaskStatus.PLANNED }.minByOrNull { it.deadline }
    }

    /** Every task currently in progress, earliest deadline first (hero pager). */
    fun running(tasks: List<OpenTask>): List<OpenTask> =
        tasks.filter { it.status == TaskStatus.IN_PROGRESS }.sortedBy { it.deadline }

    /**
     * Tasks shown in the hero block: all in-progress ones, otherwise the single nearest
     * planned task, so the hero never goes empty while there is work left.
     */
    fun heroes(tasks: List<OpenTask>): List<OpenTask> {
        val active = running(tasks)
        if (active.isNotEmpty()) return active
        return tasks.filter { it.status == TaskStatus.PLANNED }
            .sortedBy { it.deadline }
            .take(1)
    }

    /** "Up next": remaining open tasks by ascending deadline. */
    fun plan(tasks: List<OpenTask>, currentId: Long?): List<OpenTask> =
        tasks.filter { it.status == TaskStatus.PLANNED || it.status == TaskStatus.IN_PROGRESS }
            .filter { it.id != currentId }
            .sortedBy { it.deadline }

    /** Filters and sorts the plan list; hero tasks are already excluded by the caller. */
    fun planRows(
        rows: List<PlanRow>,
        now: Long,
        sort: TaskSort = TaskSort.DEADLINE,
        filter: TaskFilter = TaskFilter.ALL,
        query: String = ""
    ): List<PlanRow> {
        val open = rows.filter { it.status == TaskStatus.PLANNED || it.status == TaskStatus.IN_PROGRESS }
        val filtered = open.filter { row ->
            when (filter) {
                TaskFilter.ALL -> true
                TaskFilter.BURNING -> WindowProgress.isBurning(row.windowStart, row.deadline, now)
                TaskFilter.WEEK -> row.deadline <= now + 7 * DAY_MS
            }
        }.filter { row ->
            query.isBlank() || row.title.contains(query, ignoreCase = true)
        }
        return when (sort) {
            TaskSort.DEADLINE -> filtered.sortedBy { it.deadline }
            TaskSort.DIFFICULTY -> filtered.sortedWith(
                compareByDescending<PlanRow> { it.difficulty }.thenBy { it.deadline }
            )
            TaskSort.TITLE -> filtered.sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            )
            TaskSort.MANUAL -> filtered.sortedBy { it.orderIndex }
        }
    }

    /** Re-numbers open tasks to match the given drag-and-drop order. */
    fun reorder(rows: List<PlanRow>, orderedIds: List<Long>): Map<Long, Int> {
        val byId = rows.associateBy { it.id }
        return orderedIds.mapIndexedNotNull { index, id ->
            if (byId.containsKey(id)) id to index else null
        }.toMap()
    }
}
