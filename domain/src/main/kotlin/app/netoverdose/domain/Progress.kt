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
}
