package app.netoverdose.domain

data class ProgressTask(
    val id: Long,
    val difficulty: Int,
    val windowStart: Long,
    val deadline: Long,
    val status: TaskStatus,
    val startedAt: Long?,
    val closedAt: Long?
)

data class DayReserve(
    val dayStart: Long,
    val closedCount: Int,
    val averageReserveDays: Double?
)

data class ReserveSummary(
    val closedCount: Int,
    val reserveDays: Double?,
    val startDelayHours: Double?,
    val enoughData: Boolean
)

data class SuccessMetrics(
    val startedFastShare: Double?,
    val closedWithReserveShare: Double?
)

/** Cost of letting a task sit idle, shown on the hero card and in the card sheet. */
data class DelayCost(
    val idleHours: Double,
    val baseLoadPerDay: Double,
    val currentLoadPerDay: Double,
    val chance: Double
) {
    val rush: Boolean get() = currentLoadPerDay > 6.0
}

/** One bucket of the task pool: reserve and chronic-postponement rate per file type. */
data class PoolEntry(
    val key: String,
    val count: Int,
    val reserveDays: Double?,
    val chronicShare: Double
)

enum class PaceTone { AHEAD, ON_TIME, BEHIND, NO_DATA }

data class Pace(val tone: PaceTone, val days: Double)

/** Stage C progress rules. Pure Kotlin, no Android imports. */
object Progress {

    const val WINDOW_DAYS = 14
    const val MIN_CLOSED = 3
    const val EPS_DAYS = 0.25
    const val FAST_START_MINUTES = 15

    private const val DAY_MS = 86_400_000L
    private const val HOUR_MS = 3_600_000.0
    private const val HALF_DAY_MS = 43_200_000L

    private fun weight(task: ProgressTask): Double = task.difficulty.coerceIn(1, 5).toDouble()

    /** Closed task: deadline - closedAt. Open task: deadline - now. */
    fun reserveDays(task: ProgressTask, now: Long): Double {
        val end = task.closedAt ?: now
        return (task.deadline - end).toDouble() / DAY_MS.toDouble()
    }

    fun closedInWindow(tasks: List<ProgressTask>, now: Long): List<ProgressTask> {
        val from = now - WINDOW_DAYS * DAY_MS
        return tasks.filter {
            it.status == TaskStatus.DONE && it.closedAt != null && it.closedAt in from..now
        }
    }

    fun weightedReserveDays(closed: List<ProgressTask>): Double? {
        val total = closed.sumOf { weight(it) }
        if (total == 0.0) return null
        return closed.sumOf { reserveDays(it, it.closedAt ?: 0L) * weight(it) } / total
    }

    /** Weighted median of (startedAt - windowStart) in hours. */
    fun weightedMedianStartHours(closed: List<ProgressTask>): Double? {
        val pairs = closed
            .mapNotNull { task ->
                task.startedAt?.let { Pair((it - task.windowStart).toDouble() / HOUR_MS, weight(task)) }
            }
            .sortedBy { it.first }
        val total = pairs.sumOf { it.second }
        if (total == 0.0) return null
        val half = total / 2.0
        var accumulated = 0.0
        for ((value, w) in pairs) {
            accumulated += w
            if (accumulated >= half) return value
        }
        return pairs.last().first
    }

    fun summarize(tasks: List<ProgressTask>, now: Long): ReserveSummary {
        val closed = closedInWindow(tasks, now)
        val enough = closed.size >= MIN_CLOSED
        return ReserveSummary(
            closedCount = closed.size,
            reserveDays = if (enough) weightedReserveDays(closed) else null,
            startDelayHours = if (enough) weightedMedianStartHours(closed) else null,
            enoughData = enough
        )
    }

    fun pace(summary: ReserveSummary): Pace {
        val value = summary.reserveDays ?: return Pace(PaceTone.NO_DATA, 0.0)
        return when {
            value > EPS_DAYS -> Pace(PaceTone.AHEAD, value)
            value < -EPS_DAYS -> Pace(PaceTone.BEHIND, -value)
            else -> Pace(PaceTone.ON_TIME, 0.0)
        }
    }

    /**
     * 14 buckets, oldest first. startOfDay is injected so the domain stays free of java.time zone setup.
     * Half-day offset keeps buckets stable across DST shifts.
     */
    fun dailyReserves(
        tasks: List<ProgressTask>,
        now: Long,
        startOfDay: (Long) -> Long
    ): List<DayReserve> {
        val closed = closedInWindow(tasks, now)
        val todayStart = startOfDay(now)
        val days = ArrayList<DayReserve>(WINDOW_DAYS)
        for (i in WINDOW_DAYS - 1 downTo 0) {
            val start = startOfDay(todayStart - i * DAY_MS + HALF_DAY_MS)
            val end = start + DAY_MS
            val inDay = closed.filter {
                val at = it.closedAt ?: 0L
                at >= start && at < end
            }
            days.add(
                DayReserve(
                    dayStart = start,
                    closedCount = inDay.size,
                    averageReserveDays = if (inDay.isEmpty()) null
                    else inDay.sumOf { reserveDays(it, now) } / inDay.size
                )
            )
        }
        return days
    }

    /** Stage C success metrics from the journal. */
    fun metrics(tasks: List<ProgressTask>, now: Long): SuccessMetrics {
        val closed = closedInWindow(tasks, now)
        if (closed.isEmpty()) return SuccessMetrics(null, null)
        val started = closed.filter { it.startedAt != null }
        val fast = started.count {
            (it.startedAt ?: 0L) - it.windowStart <= FAST_START_MINUTES * 60_000L
        }
        val withReserve = closed.count { reserveDays(it, now) > 0.0 }
        return SuccessMetrics(
            startedFastShare = if (started.isEmpty()) null else fast.toDouble() / started.size,
            closedWithReserveShare = withReserve.toDouble() / closed.size
        )
    }

    /** Tasks started while the window was already burning but still closed on time. */
    fun pulledFromFire(tasks: List<ProgressTask>): Int = tasks.count { task ->
        val started = task.startedAt ?: return@count false
        val closedAt = task.closedAt ?: return@count false
        task.status == TaskStatus.DONE &&
            WindowProgress.isBurning(task.windowStart, task.deadline, started) &&
            closedAt <= task.deadline
    }

    /**
     * Cost of delay for an open task: how long it sat idle, the workload it implied when the
     * window opened versus what is left, and the chance of closing it in time.
     */
    fun delayCost(task: ProgressTask, now: Long, difficultyFactor: Double = 1.5): DelayCost {
        val idleMillis = now - (task.startedAt ?: task.windowStart)
        val idleHours = (idleMillis.toDouble() / HOUR_MS).coerceAtLeast(0.0)
        val estimateHours = task.difficulty.coerceIn(1, 5) * difficultyFactor
        val windowDays = ((task.deadline - task.windowStart).toDouble() / DAY_MS).coerceAtLeast(0.25)
        val leftDays = ((task.deadline - now).toDouble() / DAY_MS).coerceAtLeast(0.25)
        val base = estimateHours / windowDays
        val current = estimateHours / leftDays
        // 55% at the window start, +7.5 points per difficulty level, -2 points per idle hour.
        val chance = (0.55 + 0.075 * task.difficulty - 0.02 * idleHours).coerceIn(0.15, 0.9)
        return DelayCost(
            idleHours = idleHours,
            baseLoadPerDay = base,
            currentLoadPerDay = current,
            chance = chance
        )
    }

    /**
     * Groups already-bucketed tasks and reports reserve plus chronic-postponement share.
     * The caller supplies the grouping key (file type), so the domain stays free of naming rules.
     *
     * @param groups key -> tasks in that group
     * @param postponedCounts key -> how many times tasks in that group were postponed
     */
    fun pool(
        groups: Map<String, List<ProgressTask>>,
        now: Long,
        postponedCounts: Map<String, Int>
    ): List<PoolEntry> = groups.map { (key, list) ->
        val closed = list.filter { it.status == TaskStatus.DONE && it.closedAt != null }
        val reserve = if (closed.isEmpty()) null else weightedReserveDays(closed)
        val postponed = postponedCounts[key] ?: 0
        val lateShare = if (closed.isEmpty()) 0.0
        else closed.count { reserveDays(it, now) < 0.0 }.toDouble() / closed.size
        val chronic = (postponed.toDouble() / list.size.coerceAtLeast(1) * 0.6 + lateShare * 0.4)
            .coerceIn(0.0, 1.0)
        PoolEntry(key = key, count = list.size, reserveDays = reserve, chronicShare = chronic)
    }.sortedByDescending { it.chronicShare }
}
