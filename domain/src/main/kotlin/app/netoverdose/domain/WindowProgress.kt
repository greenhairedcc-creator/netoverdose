package app.netoverdose.domain

import kotlin.math.max
import kotlin.math.roundToInt

object WindowProgress {

    const val COLUMNS = 32
    const val BURN_HOURS = 24
    const val BURN_PART = 0.25f

    private const val HOUR_MS = 3_600_000L

    /** Decorative column heights in percent, left to right. */
    val HEIGHTS = listOf(
        30, 55, 40, 70, 45, 80, 60, 35, 65, 90, 50, 75, 40, 60, 85, 55,
        30, 70, 45, 80, 65, 35, 55, 90, 60, 40, 70, 50, 30, 65, 45, 55
    )

    fun fraction(windowStart: Long, deadline: Long, now: Long): Float {
        val length = deadline - windowStart
        if (length <= 0L) return 1f
        return ((now - windowStart).toFloat() / length.toFloat()).coerceIn(0f, 1f)
    }

    fun filledColumns(windowStart: Long, deadline: Long, now: Long): Int =
        (fraction(windowStart, deadline, now) * COLUMNS).roundToInt().coerceIn(0, COLUMNS)

    fun isBurning(windowStart: Long, deadline: Long, now: Long): Boolean {
        val left = deadline - now
        if (left <= 0L) return true
        val length = max(1L, deadline - windowStart)
        return left < BURN_HOURS * HOUR_MS || left.toFloat() / length.toFloat() < BURN_PART
    }
}
