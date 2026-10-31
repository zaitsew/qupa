package com.fabriquefutur.qupa.game

/** How a game is played: phones are the buzzers, or one remote passes between teams. */
enum class Mode { PHONES, TEAMS }

/** Where a game is. Every phase except [LOBBY] and [PODIUM] ends by itself. */
enum class Phase { LOBBY, QUESTION, REVEAL, SCOREBOARD, PODIUM }

enum class Difficulty {
    EASY, MEDIUM, HARD;

    companion object {
        fun parse(text: String?): Difficulty? = when (text?.trim()?.lowercase()) {
            "easy" -> EASY
            "medium" -> MEDIUM
            "hard" -> HARD
            else -> null
        }
    }
}

enum class QuestionType { MULTIPLE, BOOLEAN }

/** One bank question. [id] is stable across languages, so "seen" survives a translation. */
data class Question(
    val id: String,
    val categoryId: Int,
    val difficulty: Difficulty,
    val type: QuestionType,
    val text: String,
    val correct: String,
    val incorrect: List<String>,
)

/** What the host chose in the lobby (or as a default in Settings). */
data class GameSettings(
    val questions: Int = Limits.QUESTION_COUNTS[0],
    /** Empty means every category. */
    val categories: Set<Int> = emptySet(),
    /** Null means any difficulty. */
    val difficulty: Difficulty? = null,
    val answerSeconds: Int = Limits.ANSWER_SECONDS[1],
) {
    /** Snaps every field to a value the UI offers; stored data may be stale or hand-edited. */
    fun normalized(): GameSettings = copy(
        questions = Limits.QUESTION_COUNTS.minByOrNull { kotlin.math.abs(it - questions) } ?: Limits.QUESTION_COUNTS[0],
        answerSeconds = Limits.ANSWER_SECONDS.minByOrNull { kotlin.math.abs(it - answerSeconds) } ?: Limits.ANSWER_SECONDS[1],
        categories = categories.filter { Categories.isKnown(it) }.toSet(),
    )
}

object Limits {
    const val MAX_PLAYERS = 8
    const val MIN_TEAMS = 2
    const val MAX_TEAMS = 4
    const val MAX_NAME = 16
    val QUESTION_COUNTS = listOf(10, 15, 20)
    val ANSWER_SECONDS = listOf(10, 15, 20, 30)
}

/** Pauses between phases. Tests shorten them; the TV uses the defaults. */
data class Timings(
    val revealMs: Long = 5_000,
    val scoreboardMs: Long = 6_000,
    /** A phone that left the lobby this long ago loses its seat. */
    val lobbyGraceMs: Long = 15_000,
)

/** Monotonic milliseconds. Injected so the engine runs in virtual time under test. */
fun interface Clock {
    fun nowMs(): Long

    companion object {
        val System: Clock = Clock { java.lang.System.nanoTime() / 1_000_000 }
    }
}

/** The 24 Open Trivia Database categories; names are localized by the app, by id. */
data class Category(val id: Int, val englishName: String)

object Categories {
    val ALL: List<Category> = listOf(
        Category(9, "General Knowledge"),
        Category(10, "Entertainment: Books"),
        Category(11, "Entertainment: Film"),
        Category(12, "Entertainment: Music"),
        Category(13, "Entertainment: Musicals & Theatres"),
        Category(14, "Entertainment: Television"),
        Category(15, "Entertainment: Video Games"),
        Category(16, "Entertainment: Board Games"),
        Category(17, "Science & Nature"),
        Category(18, "Science: Computers"),
        Category(19, "Science: Mathematics"),
        Category(20, "Mythology"),
        Category(21, "Sports"),
        Category(22, "Geography"),
        Category(23, "History"),
        Category(24, "Politics"),
        Category(25, "Art"),
        Category(26, "Celebrities"),
        Category(27, "Animals"),
        Category(28, "Vehicles"),
        Category(29, "Entertainment: Comics"),
        Category(30, "Science: Gadgets"),
        Category(31, "Entertainment: Japanese Anime & Manga"),
        Category(32, "Entertainment: Cartoon & Animations"),
    )

    fun isKnown(id: Int): Boolean = ALL.any { it.id == id }

    fun idForName(name: String): Int? = ALL.firstOrNull { it.englishName.equals(name.trim(), ignoreCase = true) }?.id
}

/** Eight seat colours. The phone page and the TV both read them from here. */
object PlayerColors {
    val HEX = listOf(
        "#FF6B6B", "#FFA94D", "#FFE066", "#69DB7C",
        "#38D9A9", "#4DABF7", "#9775FA", "#F783AC",
    )

    fun hex(index: Int): String = HEX[Math.floorMod(index, HEX.size)]
}

/** Nicknames and team names: trimmed, one line, at most [Limits.MAX_NAME] characters. */
object Names {
    /** Returns the cleaned name, or null when nothing printable is left. */
    fun clean(raw: String?): String? {
        if (raw == null) return null
        val sb = StringBuilder()
        var lastSpace = false
        raw.codePoints().forEach { cp ->
            val isSpace = Character.isWhitespace(cp) || Character.isSpaceChar(cp)
            when {
                isSpace -> if (sb.isNotEmpty() && !lastSpace) { sb.append(' '); lastSpace = true }
                Character.isISOControl(cp) || cp == 0x200B || cp == 0x200E || cp == 0x200F || cp == 0x202E -> Unit
                else -> { sb.appendCodePoint(cp); lastSpace = false }
            }
        }
        val trimmed = sb.toString().trim()
        if (trimmed.isEmpty()) return null
        return truncate(trimmed, Limits.MAX_NAME).trim().ifEmpty { null }
    }

    /** Cuts to [max] code points, never in the middle of a surrogate pair. */
    fun truncate(text: String, max: Int): String {
        if (text.codePointCount(0, text.length) <= max) return text
        return text.substring(0, text.offsetByCodePoints(0, max))
    }
}
