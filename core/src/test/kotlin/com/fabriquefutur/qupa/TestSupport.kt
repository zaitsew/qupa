package com.fabriquefutur.qupa

import com.fabriquefutur.qupa.game.Clock
import com.fabriquefutur.qupa.game.Difficulty
import com.fabriquefutur.qupa.game.Question
import com.fabriquefutur.qupa.game.QuestionType

class FakeClock(var now: Long = 1_000) : Clock {
    override fun nowMs(): Long = now
    fun advance(ms: Long) { now += ms }
}

/** [n] questions: categories 9..12 in turn, every fifth is true/false, difficulties cycle. */
fun testBank(n: Int = 60): List<Question> = (0 until n).map { i ->
    val boolean = i % 5 == 4
    Question(
        id = "q$i",
        categoryId = 9 + i % 4,
        difficulty = Difficulty.entries[i % 3],
        type = if (boolean) QuestionType.BOOLEAN else QuestionType.MULTIPLE,
        text = "Question $i?",
        correct = if (boolean) "True" else "right$i",
        incorrect = if (boolean) listOf("False") else listOf("wrong${i}a", "wrong${i}b", "wrong${i}c"),
    )
}
