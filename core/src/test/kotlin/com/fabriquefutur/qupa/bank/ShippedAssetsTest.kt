package com.fabriquefutur.qupa.bank

import com.fabriquefutur.qupa.game.Categories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Checks the files the APK actually ships, read straight from the app module. */
class ShippedAssetsTest {
    private val assets = File("../app/src/main/assets")

    @Test fun theEnglishBankParsesCleanlyAndIsBigEnough() {
        val parsed = QuestionBank.parse(File(assets, "questions/en.json").readText())
        assertEquals("no row of the shipped bank may be skipped", 0, parsed.skipped)
        assertTrue("expected a bank of about 4,000+ questions, got ${parsed.questions.size}", parsed.questions.size >= 4_000)
        assertTrue(parsed.questions.all { Categories.isKnown(it.categoryId) })
        assertEquals(parsed.questions.size, parsed.questions.map { it.id }.toSet().size)
    }

    @Test fun everyCategoryHasQuestions() {
        val bank = QuestionBank.parse(File(assets, "questions/en.json").readText()).questions
        val missing = Categories.ALL.filter { c -> bank.none { it.categoryId == c.id } }
        assertTrue("categories with no questions: $missing", missing.isEmpty())
    }

    @Test fun thePhonePageIsSelfContained() {
        val html = File(assets, "web/index.html").readText()
        assertTrue(html.contains("<html"))
        // No CDN, no font host, no analytics: the page must work with no internet.
        val external = Regex("""(src|href)\s*=\s*["']https?://""").findAll(html).toList()
        assertTrue("external references: ${external.map { it.value }}", external.isEmpty())
        assertTrue(!html.contains("innerHTML"))
    }
}
