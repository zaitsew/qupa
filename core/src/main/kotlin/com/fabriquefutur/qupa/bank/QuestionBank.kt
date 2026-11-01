package com.fabriquefutur.qupa.bank

import com.fabriquefutur.qupa.game.Categories
import com.fabriquefutur.qupa.game.Difficulty
import com.fabriquefutur.qupa.game.GameSettings
import com.fabriquefutur.qupa.game.Question
import com.fabriquefutur.qupa.game.QuestionType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement

/** One row of `questions/<lang>.json`, as `tools/fetch-opentdb.py` writes it. */
@Serializable
private data class BankRow(
    val id: String,
    val categoryId: Int = 0,
    val category: String = "",
    val difficulty: String = "medium",
    val type: String = "multiple",
    val question: String,
    val correct: String,
    val incorrect: List<String> = emptyList(),
)

data class ParsedBank(val questions: List<Question>, val skipped: Int)

object QuestionBank {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Reads a bank file. A bad row is skipped and counted, never fatal: a
     * translation with one typo must not take the whole bank down. Rows whose
     * id repeats are dropped, first one wins.
     */
    fun parse(text: String): ParsedBank {
        val array = try {
            json.parseToJsonElement(text) as? JsonArray ?: return ParsedBank(emptyList(), 0)
        } catch (_: Exception) {
            return ParsedBank(emptyList(), 0)
        }
        val seen = HashSet<String>()
        val out = ArrayList<Question>(array.size)
        var skipped = 0
        for (element in array) {
            val q = try {
                toQuestion(json.decodeFromJsonElement<BankRow>(element))
            } catch (_: Exception) {
                null
            }
            if (q == null || !seen.add(q.id)) skipped++ else out += q
        }
        return ParsedBank(out, skipped)
    }

    private fun toQuestion(row: BankRow): Question? {
        val type = when (row.type.lowercase()) {
            "multiple" -> QuestionType.MULTIPLE
            "boolean" -> QuestionType.BOOLEAN
            else -> return null
        }
        if (row.id.isBlank() || row.question.isBlank() || row.correct.isBlank()) return null
        when (type) {
            QuestionType.MULTIPLE ->
                if (row.incorrect.size != 3 || row.incorrect.any { it.isBlank() }) return null
            QuestionType.BOOLEAN ->
                if (row.incorrect.size != 1 || !row.correct.isTrueOrFalse()) return null
        }
        val category = row.categoryId.takeIf { Categories.isKnown(it) }
            ?: Categories.idForName(row.category)
            ?: 0
        return Question(
            id = row.id,
            categoryId = category,
            difficulty = Difficulty.parse(row.difficulty) ?: Difficulty.MEDIUM,
            type = type,
            text = row.question.trim(),
            correct = row.correct.trim(),
            incorrect = row.incorrect.map { it.trim() },
        )
    }

    private fun String.isTrueOrFalse() = equals("True", true) || equals("False", true)

    /**
     * Picks `questions/<language>.json` when the app ships it, otherwise the
     * English bank. A language file that parses to nothing falls back too.
     */
    fun load(language: String, exists: (String) -> Boolean, read: (String) -> String): List<Question> {
        val lang = language.lowercase().takeIf { it.matches(Regex("[a-z]{2,3}")) } ?: "en"
        for (code in listOf(lang, "en").distinct()) {
            val path = "questions/$code.json"
            if (!exists(path)) continue
            val parsed = parse(read(path))
            if (parsed.questions.isNotEmpty()) return parsed.questions
        }
        return emptyList()
    }

    /** How many questions the filters leave, for the lobby's "n questions available". */
    fun available(bank: List<Question>, settings: GameSettings): Int =
        bank.count {
            (settings.categories.isEmpty() || it.categoryId in settings.categories) &&
                (settings.difficulty == null || it.difficulty == settings.difficulty)
        }
}
