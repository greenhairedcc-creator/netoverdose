package app.netoverdose.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoDifficultyTest {

    private val day = 86_400_000L
    private val now = 1_800_000_000_000L
    private val endOfToday = now + 3_600_000L

    private fun estimate(
        title: String,
        size: Long? = null,
        deadline: Long = now + 2 * day,
        history: List<HistoryTask> = emptyList()
    ) = AutoDifficulty.estimate(title, size, deadline, now, endOfToday, history)

    @Test
    fun emptyTitleFallsBackToMiddle() {
        assertEquals(3, estimate("").value)
    }

    @Test
    fun heavyKeywordRaisesDifficulty() {
        val guess = estimate("Курсовая по базам данных")
        assertEquals(5, guess.value)
        assertTrue(guess.reasons.first().contains("курсовая"))
    }

    @Test
    fun labKeywordIsFour() {
        assertEquals(4, estimate("Лабораторная №6 — OSPF").value)
    }

    @Test
    fun lightKeywordIsOne() {
        assertEquals(1, estimate("Позвонить в деканат").value)
    }

    @Test
    fun heavyBeatsMedium() {
        // contains both "лаборатор" (4) and "задач" (3); the heavy table wins
        assertEquals(4, estimate("Задачи к лабораторной").value)
    }

    @Test
    fun quantityBonusApplies() {
        val twelve = estimate("Презентация: 12 слайдов")
        // "презентац" -> 3, then +1 for 12 slides
        assertEquals(4, twelve.value)
        assertTrue(twelve.reasons.any { it.contains("12") })
    }

    @Test
    fun twentyItemsAddTwo() {
        val guess = estimate("Решить 25 задач")
        // "задач" -> 3, +2 for the count
        assertEquals(5, guess.value)
    }

    @Test
    fun historyPullsTowardsAverage() {
        val history = listOf(
            HistoryTask("Эссе про этику", 1),
            HistoryTask("Эссе про сети", 2)
        )
        val guess = estimate("Эссе — цифровая этика", history = history)
        // base 3 blended 55/45 with average 1.5 -> 2.325 -> 2
        assertEquals(2, guess.value)
        assertTrue(guess.reasons.any { it.startsWith("история") })
    }

    @Test
    fun singleSimilarTaskIsIgnored() {
        val history = listOf(HistoryTask("Эссе про этику", 1))
        val guess = estimate("Эссе — цифровая этика", history = history)
        assertEquals(3, guess.value)
    }

    @Test
    fun dueTodayAddsHalf() {
        val guess = estimate("Реферат", deadline = now + 60_000L)
        assertTrue(guess.reasons.any { it.contains("сегодня") })
    }

    @Test
    fun distantDeadlineSubtracts() {
        val guess = estimate("Реферат", deadline = now + 10 * day)
        assertTrue(guess.reasons.any { it.contains("далеко") })
    }

    @Test
    fun bigFileAddsHalf() {
        val guess = estimate("Отчёт", size = 3 * 1_048_576L)
        assertTrue(guess.reasons.any { it.contains("МБ") })
    }

    @Test
    fun resultIsClampedToOneToFive() {
        val low = estimate("Позвонить", deadline = now + 30 * day)
        val high = estimate("Курсовая 99 вопросов", size = 9 * 1_048_576L)
        assertTrue(low.value in 1..5)
        assertTrue(high.value in 1..5)
    }

    @Test
    fun reasonsAreCappedAtFour() {
        val history = listOf(HistoryTask("Курсовая про сети", 5), HistoryTask("Курсовая два", 4))
        val guess = estimate(
            "Курсовая: 30 вопросов",
            size = 5 * 1_048_576L,
            deadline = now + 60_000L,
            history = history
        )
        assertTrue(guess.reasons.size <= 4)
    }
}
