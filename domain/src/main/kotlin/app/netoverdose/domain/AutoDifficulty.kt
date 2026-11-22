package app.netoverdose.domain

import java.util.Locale
import kotlin.math.round

/** A suggested difficulty plus the reasons behind it, shown as chips in the intake sheet. */
data class DifficultyGuess(
    val value: Int,
    val reasons: List<String>
)

/** Minimal projection of a past task used to learn from history. */
data class HistoryTask(val title: String, val difficulty: Int)

/**
 * Heuristic difficulty estimate, ported from the reference prototype's estimate().
 * Pure Kotlin: no Android, no resources. Reason labels are plain strings so the rule
 * set can be unit tested without an Android runtime; the UI shows them as-is.
 */
object AutoDifficulty {

    private val heavy = linkedMapOf(
        "курсач" to 5, "курсовая" to 5, "диплом" to 5, "экзамен" to 5,
        "лаб" to 4, "лаборатор" to 4, "отчёт" to 4, "отчет" to 4,
        "контрольн" to 4, "проект" to 4
    )

    private val medium = linkedMapOf(
        "презентац" to 3, "эссе" to 3, "задач" to 3,
        "доклад" to 3, "семинар" to 3, "конспект" to 2, "реферат" to 3
    )

    private val light = linkedMapOf(
        "позвонить" to 1, "отправить" to 1, "скачать" to 1,
        "распечатать" to 1, "оплатить" to 1, "справк" to 1
    )

    private val quantity = Regex("(\\d+)\\s*(задач|слайд|страниц|глав|пункт|вопрос)")
    private val ru: Locale = Locale.forLanguageTag("ru")

    private const val DAY_MS = 86_400_000L
    private const val MB = 1_048_576L

    /**
     * @param title task title typed by the user
     * @param fileSizeBytes attachment size, or null when there is no file
     * @param deadline deadline epoch millis
     * @param now current epoch millis
     * @param endOfToday end of the current local day, used to detect "due today"
     * @param history past tasks, used to match similar titles
     */
    fun estimate(
        title: String,
        fileSizeBytes: Long?,
        deadline: Long,
        now: Long,
        endOfToday: Long,
        history: List<HistoryTask>
    ): DifficultyGuess {
        val text = title.lowercase()
        var value = 3.0
        val reasons = ArrayList<String>(4)

        matchKeyword(text)?.let { (word, weight) ->
            value = weight.toDouble()
            reasons += "«$word» → $weight"
        }

        quantity.find(text)?.let { match ->
            val count = match.groupValues[1].toIntOrNull() ?: 0
            val unit = match.groupValues[2]
            val bonus = when {
                count >= 20 -> 2
                count >= 10 -> 1
                else -> 0
            }
            if (bonus > 0) {
                value += bonus
                reasons += "$count $unit → +$bonus"
            }
        }

        // Learn from tasks whose titles share a significant word.
        val words = text.replace(Regex("[^a-zа-яё\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 4 }
            .map { it.take(5) }
        val similar = history.filter { past ->
            val pastTitle = past.title.lowercase()
            words.any { word -> word.isNotEmpty() && pastTitle.contains(word) }
        }
        if (similar.size >= 2) {
            val average = similar.sumOf { it.difficulty }.toDouble() / similar.size
            value = value * 0.55 + average * 0.45
            reasons += "история ${similar.size} задач → ${formatOne(average)}"
        }

        when {
            deadline < endOfToday -> {
                value += 0.5
                reasons += "дедлайн сегодня → +0,5"
            }
            deadline > now + 6 * DAY_MS -> {
                value -= 0.25
                reasons += "срок далеко → −0,25"
            }
        }

        if (fileSizeBytes != null && fileSizeBytes > 2 * MB) {
            value += 0.5
            reasons += "файл ${formatSize(fileSizeBytes)} → +0,5"
        }

        return DifficultyGuess(
            value = round(value).toInt().coerceIn(1, 5),
            reasons = reasons.take(4)
        )
    }

    private fun matchKeyword(text: String): Pair<String, Int>? {
        heavy.forEach { (word, weight) -> if (text.contains(word)) return word to weight }
        medium.forEach { (word, weight) -> if (text.contains(word)) return word to weight }
        light.forEach { (word, weight) -> if (text.contains(word)) return word to weight }
        return null
    }

    private fun formatOne(value: Double): String = String.format(ru, "%.1f", value)

    private fun formatSize(bytes: Long): String =
        if (bytes >= MB) String.format(ru, "%.1f МБ", bytes / MB.toDouble())
        else "${bytes / 1024L} КБ"
}
