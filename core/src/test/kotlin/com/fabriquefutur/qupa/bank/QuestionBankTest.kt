package com.fabriquefutur.qupa.bank

import com.fabriquefutur.qupa.game.Difficulty
import com.fabriquefutur.qupa.game.GameSettings
import com.fabriquefutur.qupa.game.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionBankTest {
    private val good = """
        [
          {"id":"a1","categoryId":9,"category":"General Knowledge","difficulty":"easy","type":"multiple",
           "question":"Capital of France?","correct":"Paris","incorrect":["Rome","Madrid","Berlin"]},
          {"id":"b2","categoryId":17,"category":"Science & Nature","difficulty":"hard","type":"boolean",
           "question":"The Sun is a star.","correct":"True","incorrect":["False"]}
        ]
    """.trimIndent()

    @Test fun parsesBothQuestionTypes() {
        val parsed = QuestionBank.parse(good)
        assertEquals(0, parsed.skipped)
        assertEquals(2, parsed.questions.size)
        val first = parsed.questions[0]
        assertEquals("a1", first.id)
        assertEquals(9, first.categoryId)
        assertEquals(Difficulty.EASY, first.difficulty)
        assertEquals(QuestionType.MULTIPLE, first.type)
        assertEquals(listOf("Rome", "Madrid", "Berlin"), first.incorrect)
        assertEquals(QuestionType.BOOLEAN, parsed.questions[1].type)
    }

    @Test fun aBadRowIsSkippedNotFatal() {
        val text = """
            [
              {"id":"a1","categoryId":9,"difficulty":"easy","type":"multiple","question":"Q?","correct":"A","incorrect":["B","C"]},
              {"id":"a2","categoryId":9,"difficulty":"easy","type":"multiple","question":"Q2?","correct":"A","incorrect":["B","C","D"]},
              {"id":"a3","type":"boolean","question":"Q3?","correct":"Maybe","incorrect":["False"]},
              {"id":"a4","type":"essay","question":"Q4?","correct":"A","incorrect":[]},
              {"nonsense":true},
              {"id":"a2","type":"boolean","question":"dup","correct":"True","incorrect":["False"]}
            ]
        """.trimIndent()
        val parsed = QuestionBank.parse(text)
        assertEquals(listOf("a2"), parsed.questions.map { it.id })
        assertEquals(5, parsed.skipped)
    }

    @Test fun garbageIsAnEmptyBank() {
        assertTrue(QuestionBank.parse("not json").questions.isEmpty())
        assertTrue(QuestionBank.parse("{\"a\":1}").questions.isEmpty())
        assertTrue(QuestionBank.parse("").questions.isEmpty())
    }

    @Test fun categoryIsFoundByNameWhenTheIdIsMissing() {
        val text = """[{"id":"x","category":"Mythology","type":"boolean","question":"Q?","correct":"False","incorrect":["True"]}]"""
        assertEquals(20, QuestionBank.parse(text).questions.single().categoryId)
    }

    @Test fun unknownDifficultyBecomesMedium() {
        val text = """[{"id":"x","categoryId":9,"difficulty":"insane","type":"boolean","question":"Q?","correct":"True","incorrect":["False"]}]"""
        assertEquals(Difficulty.MEDIUM, QuestionBank.parse(text).questions.single().difficulty)
    }

    @Test fun loaderPrefersTheLanguageFileAndFallsBackToEnglish() {
        val files = mapOf("questions/en.json" to good, "questions/es.json" to good.replace("Paris", "Paris (es)"))
        val exists = { p: String -> p in files }
        val read = { p: String -> files.getValue(p) }
        assertEquals("Paris (es)", QuestionBank.load("es", exists, read)[0].correct)
        assertEquals("Paris", QuestionBank.load("ru", exists, read)[0].correct)
        assertEquals("Paris", QuestionBank.load("en", exists, read)[0].correct)
    }

    @Test fun anUnreadableLanguageFileFallsBackToEnglish() {
        val files = mapOf("questions/en.json" to good, "questions/es.json" to "oops")
        val loaded = QuestionBank.load("es", { it in files }, { files.getValue(it) })
        assertEquals(2, loaded.size)
    }

    @Test fun aHostileLanguageCodeIsIgnored() {
        val seen = mutableListOf<String>()
        QuestionBank.load("../../etc/passwd", { seen += it; false }, { "" })
        assertTrue(seen.all { it == "questions/en.json" })
    }

    @Test fun availableCountsTheFilter() {
        val bank = QuestionBank.parse(good).questions
        assertEquals(2, QuestionBank.available(bank, GameSettings()))
        assertEquals(1, QuestionBank.available(bank, GameSettings(difficulty = Difficulty.HARD)))
        assertEquals(1, QuestionBank.available(bank, GameSettings(categories = setOf(9))))
    }
}
