package app.netoverdose.domain

enum class TaskStatus { PLANNED, IN_PROGRESS, DONE, POSTPONED }

enum class StepSource { USER, LLM }

enum class EventType { CREATED, STARTED, STEP_DONE, CLOSED, POSTPONED, REOPENED }

/** Minimal projection used by pure selection rules. */
interface OpenTask {
    val id: Long
    val status: TaskStatus
    val deadline: Long
    val windowStart: Long
}

object TaskRules {

    /** "Now at work": earliest IN_PROGRESS, otherwise earliest PLANNED. */
    fun current(tasks: List<OpenTask>): OpenTask? {
        val running = tasks.filter { it.status == TaskStatus.IN_PROGRESS }.minByOrNull { it.deadline }
        if (running != null) return running
        return tasks.filter { it.status == TaskStatus.PLANNED }.minByOrNull { it.deadline }
    }

    /** "Up next": remaining open tasks by ascending deadline. */
    fun plan(tasks: List<OpenTask>, currentId: Long?): List<OpenTask> =
        tasks.filter { it.status == TaskStatus.PLANNED || it.status == TaskStatus.IN_PROGRESS }
            .filter { it.id != currentId }
            .sortedBy { it.deadline }
}
