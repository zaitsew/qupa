package com.fabriquefutur.qupa.game

import com.fabriquefutur.qupa.testBank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuestionPickerTest {
    private val bank = testBank(60)

    @Test fun picksTheRequestedNumberWithoutRepeats() {
        val picked = QuestionPicker.pick(bank, GameSettings(questions = 20), emptySet(), emptySet(), Random(1))
        assertEquals(20, picked.size)
        assertEquals(20, picked.map { it.id }.toSet().size)
    }

    @Test fun categoryAndDifficultyFiltersApply() {
        val s = GameSettings(questions = 20, categories = setOf(9, 10), difficulty = Difficulty.EASY)
        val picked = QuestionPicker.pick(bank, s, emptySet(), emptySet(), Random(2))
        assertTrue(picked.isNotEmpty())
        assertTrue(picked.all { it.categoryId in setOf(9, 10) && it.difficulty == Difficulty.EASY })
    }

    @Test fun anEmptyCategorySetMeansEveryCategory() {
        val picked = QuestionPicker.pick(bank, GameSettings(questions = 20), emptySet(), emptySet(), Random(3))
        assertEquals(setOf(9, 10, 11, 12), picked.map { it.categoryId }.toSet())
    }

    @Test fun questionsAskedInThisSessionNeverReturn() {
        val asked = bank.take(55).map { it.id }.toSet()
        val picked = QuestionPicker.pick(bank, GameSettings(questions = 20), emptySet(), asked, Random(4))
        assertEquals(5, picked.size)
        assertTrue(picked.none { it.id in asked })
    }

    @Test fun unseenQuestionsComeBeforeSeenOnes() {
        val seen = bank.take(50).map { it.id }.toSet()
        repeat(20) { seed ->
            val picked = QuestionPicker.pick(bank, GameSettings(questions = 10), seen, emptySet(), Random(seed))
            assertTrue(picked.none { it.id in seen })
        }
    }

    @Test fun seenQuestionsFillInWhenUnseenRunOut() {
        val seen = bank.take(55).map { it.id }.toSet()
        val picked = QuestionPicker.pick(bank, GameSettings(questions = 10), seen, emptySet(), Random(5))
        assertEquals(10, picked.size)
        assertEquals(5, picked.count { it.id !in seen })
        // The five unseen ones lead.
        assertTrue(picked.take(5).none { it.id in seen })
    }

    @Test fun anImpossibleFilterGivesAnEmptyList() {
        val s = GameSettings(categories = setOf(32))
        assertTrue(QuestionPicker.pick(bank, s, emptySet(), emptySet(), Random(6)).isEmpty())
    }

    @Test fun duplicateIdsInTheBankAreCollapsed() {
        val doubled = bank + bank
        val picked = QuestionPicker.pick(doubled, GameSettings(questions = 20), emptySet(), emptySet(), Random(7))
        assertEquals(20, picked.map { it.id }.toSet().size)
    }
}
