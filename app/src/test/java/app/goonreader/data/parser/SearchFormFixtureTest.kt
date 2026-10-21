package app.goonreader.data.parser

import app.goonreader.Fixture
import app.goonreader.domain.model.AO3_LANGUAGES
import app.goonreader.domain.model.Category
import app.goonreader.domain.model.Rating
import app.goonreader.domain.model.SortColumn
import app.goonreader.domain.model.Warning
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the hard-coded AO3 ids against reality (fixture `13-search-form.html`, captured from
 * the live "Work Search" page). If AO3 renumbers a tag or renames an option, this fails instead
 * of a filter silently matching nothing.
 */
class SearchFormFixtureTest {

    private val doc: Document = Jsoup.parse(Fixture.load("13-search-form.html"))

    @Test
    fun `ratings keep their ids`() {
        val options = selectOptions("work_search[rating_ids]")
        Rating.entries.forEach { rating -> assertEquals(rating.label, options[rating.id]) }
    }

    @Test
    fun `warnings keep their ids`() {
        val labels = checkboxLabels("work_search[archive_warning_ids][]")
        Warning.entries.forEach { warning -> assertEquals(warning.label, labels[warning.id]) }
    }

    @Test
    fun `categories keep their ids`() {
        val labels = checkboxLabels("work_search[category_ids][]")
        Category.entries.forEach { category -> assertEquals(category.label, labels[category.id]) }
    }

    @Test
    fun `sort options keep their values`() {
        val options = selectOptions("work_search[sort_column]")
        SortColumn.entries.forEach { column ->
            val value = column.value ?: return@forEach
            assertEquals(column.label.lowercase(), options[value]?.lowercase())
        }
    }

    @Test
    fun `completion and crossover keep their radio values`() {
        assertEquals(setOf("", "T", "F"), values("work_search[complete]"))
        assertEquals(setOf("", "F", "T"), values("work_search[crossover]"))
    }

    @Test
    fun `every parameter the app builds exists on the form`() {
        val names = doc.select("input[name], select[name], textarea[name]").map { it.attr("name") }.toSet()

        listOf(
            "work_search[query]",
            "work_search[title]",
            "work_search[creators]",
            "work_search[fandom_names]",
            "work_search[relationship_names]",
            "work_search[character_names]",
            "work_search[freeform_names]",
            "work_search[word_count]",
            "work_search[language_id]",
            "work_search[revised_at]",
            "work_search[complete]",
            "work_search[crossover]",
            "work_search[single_chapter]",
            "work_search[sort_column]",
            "work_search[sort_direction]",
            "work_search[archive_warning_ids][]",
            "work_search[category_ids][]",
            "work_search[rating_ids]",
        ).forEach { name -> assertTrue("$name is no longer on AO3's form", name in names) }
    }

    @Test
    fun `curated languages are real options`() {
        val options = selectOptions("work_search[language_id]")
        AO3_LANGUAGES.forEach { language ->
            assertTrue("${language.id} (${language.label}) is no longer offered", options.containsKey(language.id))
        }
    }

    private fun selectOptions(name: String): Map<String, String> =
        doc.select("select[name=\"$name\"] option")
            .filter { it.attr("value").isNotEmpty() }
            .associate { it.attr("value") to it.text() }

    private fun checkboxLabels(name: String): Map<String, String> =
        doc.select("input[name=\"$name\"]").associate { input ->
            input.attr("value") to doc.selectFirst("label[for=${input.id()}]")?.text().orEmpty()
        }

    private fun values(name: String): Set<String> =
        doc.select("input[name=\"$name\"]").map { it.attr("value") }.toSet()
}
