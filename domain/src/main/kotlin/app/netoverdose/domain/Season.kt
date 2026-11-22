package app.netoverdose.domain

/** One day of a season heat grid. */
data class SeasonDay(
    val dayStart: Long,
    val weight: Int,
    val closedCount: Int,
    val burned: Int
)

/** A 28-day season window and the numbers derived from it. */
data class Season(
    val index: Int,
    val start: Long,
    val end: Long,
    val daysLeft: Int,
    val closedCount: Int,
    val weight: Int,
    val reserveDays: Double?,
    val medianStartHours: Double?,
    val pulledFromFire: Int,
    val days: List<SeasonDay>
) {
    val maxWeight: Int get() = days.maxOfOrNull { it.weight }?.coerceAtLeast(1) ?: 1
}

/** Change of a metric against the previous season; null when either side has no data. */
data class SeasonDelta(val reserve: Double?, val weight: Int, val medianStart: Double?)

/**
 * Season rules from the reference: seasons are 28-day windows anchored at the earliest
 * task, so they are stable regardless of when the screen is opened.
 */
object SeasonRules {

    const val LENGTH_DAYS = 28
    private const val DAY_MS = 86_400_000L

    /** Season that contains [now], anchored at [anchor] (earliest task createdAt). */
    fun of(now: Long, anchor: Long): Int {
        if (now <= anchor) return 0
        return ((now - anchor) / (LENGTH_DAYS * DAY_MS)).toInt()
    }

    fun bounds(index: Int, anchor: Long): Pair<Long, Long> {
        val start = anchor + index.toLong() * LENGTH_DAYS * DAY_MS
        return start to start + LENGTH_DAYS * DAY_MS
    }

    /**
     * @param tasks all tasks
     * @param now current time
     * @param anchor earliest task createdAt
     * @param startOfDay converts an instant to local midnight. The grid is aligned to
     *        the season start first, then snapped to local midnight, so day buckets never
     *        drift out of the 28-day window regardless of time zone.
     */
    fun stats(
        tasks: List<ProgressTask>,
        now: Long,
        anchor: Long,
        index: Int,
        startOfDay: (Long) -> Long
    ): Season {
        val (start, end) = bounds(index, anchor)
        val closed = tasks.filter {
            it.status == TaskStatus.DONE && it.closedAt != null && it.closedAt in start until end
        }
        val weight = closed.sumOf { it.difficulty.coerceIn(1, 5) }
        val reserve = if (closed.isEmpty()) null else Progress.weightedReserveDays(closed)
        val median = Progress.weightedMedianStartHours(closed)

        val pulled = closed.count { task ->
            val started = task.startedAt ?: return@count false
            val closedAt = task.closedAt ?: return@count false
            // started while the window was already burning, yet still closed on time
            WindowProgress.isBurning(task.windowStart, task.deadline, started) && closedAt <= task.deadline
        }

        // Walk the grid from the snapped season start, keeping exactly LENGTH_DAYS buckets
        // so a DST shift can never add or drop a cell.
        val gridStart = startOfDay(start)
        val days = ArrayList<SeasonDay>(LENGTH_DAYS)
        var cursor = gridStart
        repeat(LENGTH_DAYS) {
            val dayEnd = cursor + DAY_MS
            // The first bucket starts at the snapped local midnight, which can sit up to a
            // day before the raw season start, so allow that one day of slack on the left.
            val inWindow = cursor >= gridStart && cursor < end
            val closedToday = if (inWindow) {
                closed.filter { it.closedAt != null && it.closedAt in cursor until dayEnd }
            } else emptyList()
            val burnedToday = if (inWindow) tasks.count { task ->
                task.deadline in cursor until dayEnd && task.deadline < now &&
                    (task.closedAt == null || task.closedAt > task.deadline)
            } else 0
            days += SeasonDay(
                dayStart = cursor,
                weight = closedToday.sumOf { it.difficulty.coerceIn(1, 5) },
                closedCount = closedToday.size,
                burned = burnedToday
            )
            cursor = dayEnd
        }

        val daysLeft = ((end - now) / DAY_MS).coerceAtLeast(0L).toInt()
        return Season(
            index = index,
            start = start,
            end = end,
            daysLeft = daysLeft,
            closedCount = closed.size,
            weight = weight,
            reserveDays = reserve,
            medianStartHours = median,
            pulledFromFire = pulled,
            days = days
        )
    }

    fun delta(current: Season, previous: Season): SeasonDelta {
        val reserve = if (current.reserveDays != null && previous.reserveDays != null) {
            current.reserveDays - previous.reserveDays
        } else null
        val median = if (current.medianStartHours != null && previous.medianStartHours != null) {
            current.medianStartHours - previous.medianStartHours
        } else null
        return SeasonDelta(reserve = reserve, weight = current.weight - previous.weight, medianStart = median)
    }
}
