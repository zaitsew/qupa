package com.fabriquefutur.qupa

import com.fabriquefutur.qupa.game.Categories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The three languages must stay complete and agree on their format placeholders. */
class StringsTest {
    private fun load(dir: String): Map<String, String> {
        val file = File("src/main/res/$dir/strings.xml")
        assertTrue("missing ${file.path}", file.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val n = nodes.item(i)
            n.attributes.getNamedItem("name").nodeValue to n.textContent
        }
    }

    private val en = load("values")
    private val es = load("values-es")
    private val ru = load("values-ru")

    @Test
    fun everyLanguageHasEveryKey() {
        assertEquals(en.keys, es.keys)
        assertEquals(en.keys, ru.keys)
    }

    @Test
    fun noStringIsEmpty() {
        for ((lang, map) in listOf("en" to en, "es" to es, "ru" to ru)) {
            map.forEach { (key, value) -> assertTrue("$lang/$key is empty", value.isNotBlank()) }
        }
    }

    @Test
    fun everyCategoryHasAName() {
        for (category in Categories.ALL) {
            assertTrue("cat_${category.id}", "cat_${category.id}" in en.keys)
        }
        // And no category string is left over for an id the engine does not know.
        val ids = en.keys.filter { it.startsWith("cat_") }.map { it.removePrefix("cat_").toInt() }.toSet()
        assertEquals(Categories.ALL.map { it.id }.toSet(), ids)
    }

    @Test
    fun placeholdersMatchAcrossLanguages() {
        val spec = Regex("%(\\d\\$)?[sd]")
        for ((key, value) in en) {
            val expected = spec.findAll(value).map { it.value.replace(Regex("^%\\d\\$"), "%") }.sorted().toList()
            for ((lang, map) in listOf("es" to es, "ru" to ru)) {
                val got = spec.findAll(map.getValue(key)).map { it.value.replace(Regex("^%\\d\\$"), "%") }.sorted().toList()
                assertEquals("$lang/$key placeholders", expected, got)
            }
        }
    }

    @Test
    fun attributionIsOnTheAboutScreenInEveryLanguage() {
        for (map in listOf(en, es, ru)) {
            val text = map.getValue("about_attribution")
            assertTrue(text.contains("opentdb.com") && text.contains("CC BY-SA 4.0"))
        }
    }
}
