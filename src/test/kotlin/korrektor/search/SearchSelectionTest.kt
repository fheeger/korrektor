package korrektor.search

import korrektor.editor.BookEditor
import korrektor.editor.withHistoryProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The preview of a replacement: the checkboxes and what a hit becomes (T087). */
class SearchSelectionTest {
    private fun searched(
        editor: BookEditor,
        pages: Int,
        query: SearchQuery,
    ): SearchModel {
        val model = SearchModel(editor, pages, Dispatchers.Unconfined, Dispatchers.IO, pause = 0)
        model.change(query)
        runBlocking { withTimeout(10_000) { while (model.searching) delay(2) } }
        return model
    }

    @Test
    fun allHitsAreCheckedAtFirstAndTheCheckboxesCanBeChanged() =
        withHistoryProject(pages = 4) { project ->
            val model = searched(BookEditor(project::readPage, Dispatchers.Unconfined), 4, SearchQuery("Seite"))
            assertEquals(4, model.results.size)
            assertEquals(4, model.selectedCount)
            assertTrue((0..3).all { model.isSelected(it) })

            model.setSelected(1, false)
            model.setSelected(3, false)
            assertEquals(2, model.selectedCount)
            assertEquals(listOf(1, 3), model.selectedMatches().map { it.page })
            model.setSelected(1, true)
            assertEquals(listOf(1, 2, 3), model.selectedMatches().map { it.page })

            model.selectNone()
            assertEquals(0, model.selectedCount)
            assertEquals(emptyList(), model.selectedMatches())
            model.selectAll()
            assertEquals(4, model.selectedCount)
        }

    @Test
    fun aNewSearchChecksEverythingAgain() =
        withHistoryProject(pages = 4) { project ->
            val model = searched(BookEditor(project::readPage, Dispatchers.Unconfined), 4, SearchQuery("Seite"))
            model.selectNone()
            model.change(SearchQuery("unten"))
            runBlocking { withTimeout(10_000) { while (model.searching) delay(2) } }
            assertEquals(4, model.selectedCount)
        }

    @Test
    fun theCheckboxesSurviveHidingAndShowingTheReplaceField() =
        withHistoryProject(pages = 4) { project ->
            val model = searched(BookEditor(project::readPage, Dispatchers.Unconfined), 4, SearchQuery("Seite"))
            model.show(replace = true)
            model.setSelected(2, false)
            model.showReplace = false
            model.show(replace = true)
            assertFalse(model.isSelected(2))
            assertEquals(3, model.selectedCount)
        }

    @Test
    fun thePreviewShowsWhatAHitBecomesWithGroupsOfARegex() =
        withHistoryProject(pages = 3) { project ->
            val query = SearchQuery("""Seite (\d) oben""", regex = true)
            val model = searched(BookEditor(project::readPage, Dispatchers.Unconfined), 3, query)
            model.replacement = "S$1 oben"
            val preview = checkNotNull(model.preview(model.results[1])).getOrThrow()
            assertEquals("Seite 2 oben", preview.before + model.results[1].found + preview.after)
            assertEquals("S2 oben", preview.replacement)
            assertEquals("S2 oben", preview.afterText())
            assertNull(model.replacementProblem())

            // A plain search takes the replacement as it is: no groups.
            val plain = searched(BookEditor(project::readPage, Dispatchers.Unconfined), 3, SearchQuery("Seite"))
            plain.replacement = "$1 Blatt"
            assertEquals("$1 Blatt", checkNotNull(plain.preview(plain.results[0])).getOrThrow().replacement)
        }

    @Test
    fun aReplacementThatIsNotValidHasNoPreviewAndASayingWhy() =
        withHistoryProject(pages = 3) { project ->
            val query = SearchQuery("""Seite (\d)""", regex = true)
            val model = searched(BookEditor(project::readPage, Dispatchers.Unconfined), 3, query)
            model.replacement = "$5"
            assertTrue(checkNotNull(model.preview(model.results[0])).isFailure)
            assertTrue(checkNotNull(model.replacementProblem()).startsWith("The replacement is not valid"))
            model.replacement = "$1"
            assertNull(model.replacementProblem())
        }

    @Test
    fun withoutASearchThereIsNothingToPreview() =
        withHistoryProject(pages = 2) { project ->
            val model = SearchModel(BookEditor(project::readPage, Dispatchers.Unconfined), 2, Dispatchers.Unconfined)
            assertNull(model.preview(Match(1, "l1", 1, "abc", 0, 1)))
            assertNull(model.replacementProblem())
        }

    @Test
    fun theHitsAreNeverChangedByThePreview() =
        withHistoryProject(pages = 3) { project ->
            val editor = BookEditor(project::readPage, Dispatchers.Unconfined)
            val model = searched(editor, 3, SearchQuery("Seite"))
            model.replacement = "Blatt"
            model.show(replace = true)
            for (match in model.results) model.preview(match)
            model.selectNone()
            model.selectAll()
            assertTrue(editor.changed.isEmpty())
            assertTrue(editor.changedPages.isEmpty())
            assertEquals(0, editor.history.undoSteps.size)
            assertEquals(emptyList(), editor.history.pendingEntries)
        }
}
