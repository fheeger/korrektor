package korrektor.search

import korrektor.editor.BookEditor
import korrektor.editor.withHistoryProject
import korrektor.format.PageLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The search model: results that arrive while it searches, cancel, the scope and the corrections of the session. */
class SearchModelTest {
    private fun waitUntilDone(model: SearchModel) =
        runBlocking {
            withTimeout(10_000) {
                while (model.searching) delay(2)
            }
        }

    private fun editorOf(
        read: (Int) -> PageLayer,
        pages: Int = 6,
    ): Pair<BookEditor, SearchModel> {
        val editor = BookEditor(read, Dispatchers.Unconfined)
        return editor to SearchModel(editor, pages, Dispatchers.Unconfined, Dispatchers.IO, pause = 0, chunk = 2)
    }

    @Test
    fun resultsFillUpInPageOrderWhileItSearches() =
        withHistoryProject(pages = 6) { project ->
            val slow = { n: Int -> Thread.sleep(30).let { project.readPage(n) } }
            val (_, model) = editorOf(slow)
            model.change(SearchQuery("Seite"))
            assertTrue(model.searching)
            val sizes = ArrayList<Int>()
            runBlocking {
                withTimeout(10_000) {
                    while (model.searching) {
                        sizes += model.results.size
                        delay(2)
                    }
                }
            }
            assertEquals(6, model.results.size)
            assertEquals((1..6).toList(), model.results.map { it.page })
            assertTrue(sizes.any { it in 1..5 }, "hits were there before the search was done: $sizes")
            assertEquals(6, model.pagesDone)
            assertEquals(6, model.pagesWithHits)
            assertNull(model.message)
        }

    @Test
    fun cancellingStopsTheSearchAndKeepsWhatWasFound() =
        withHistoryProject(pages = 6) { project ->
            val slow = { n: Int -> Thread.sleep(60).let { project.readPage(n) } }
            val (_, model) = editorOf(slow)
            model.change(SearchQuery("Seite"))
            runBlocking { withTimeout(10_000) { while (model.results.isEmpty()) delay(2) } }
            model.cancel()
            assertFalse(model.searching)
            val kept = model.results.size
            assertTrue(kept in 1..5, "found $kept")
            runBlocking { delay(400) }
            assertEquals(kept, model.results.size, "no hit arrives after the cancel")
            assertFalse(model.searching)
        }

    @Test
    fun aNewQueryReplacesTheOldOneWithNoHitLeftOfIt() =
        withHistoryProject(pages = 6) { project ->
            val slow = { n: Int -> Thread.sleep(40).let { project.readPage(n) } }
            val (_, model) = editorOf(slow)
            model.change(SearchQuery("Seite"))
            model.change(SearchQuery("unten"))
            waitUntilDone(model)
            assertEquals(6, model.results.size)
            assertTrue(model.results.all { it.found == "unten" }, model.results.toString())
            model.change(SearchQuery(""))
            assertTrue(model.results.isEmpty())
            assertFalse(model.searching)
        }

    @Test
    fun correctionsThatAreNotSavedAreFoundAndTheOldTextIsNot() =
        withHistoryProject(pages = 4) { project ->
            val (editor, model) = editorOf(project::readPage, 4)
            runBlocking { editor.show(2) }
            editor.setLineText("l1", "Zweite Seite ſo")
            assertEquals(
                emptyList(),
                project
                    .readPage(2)
                    .regions
                    .flatMap { it.lines }
                    .filter { "ſo" in it.text },
            )
            model.change(SearchQuery("ſo"))
            waitUntilDone(model)
            assertEquals(listOf(2), model.results.map { it.page })
            model.change(SearchQuery("2 oben"))
            waitUntilDone(model)
            assertTrue(model.results.isEmpty(), "the text before the correction is gone")
        }

    @Test
    fun thePageScopeSearchesTheShownPageAndFollowsIt() =
        withHistoryProject(pages = 6) { project ->
            val (editor, model) = editorOf(project::readPage)
            model.show(replace = false)
            model.changeScope(SearchScope.PAGE)
            runBlocking { editor.show(3) }
            model.change(SearchQuery("Seite"))
            waitUntilDone(model)
            assertEquals(listOf(3), model.results.map { it.page })
            runBlocking { editor.show(4) }
            model.pageChanged()
            waitUntilDone(model)
            assertEquals(listOf(4), model.results.map { it.page })
            model.changeScope(SearchScope.BOOK)
            waitUntilDone(model)
            assertEquals(6, model.results.size)
        }

    @Test
    fun switchingBetweenFindingAndReplacingKeepsEverything() =
        withHistoryProject(pages = 6) { project ->
            val (_, model) = editorOf(project::readPage)
            model.show(replace = false)
            assertFalse(model.showReplace)
            val query = SearchQuery("seite", matchCase = true, wholeWords = true)
            model.change(query)
            model.change(query.copy(matchCase = false))
            waitUntilDone(model)
            model.replacement = "Blatt"
            model.changeScope(SearchScope.BOOK)
            val found = model.results.toList()
            assertEquals(6, found.size)

            model.show(replace = true)
            assertTrue(model.showReplace)
            assertEquals(query.copy(matchCase = false), model.query)
            assertEquals(SearchScope.BOOK, model.scopeOf)
            assertEquals(found, model.results.toList())
            assertEquals("Blatt", model.replacement)
            model.showReplace = false
            assertEquals(found, model.results.toList())
            assertTrue(model.open)
            model.hide()
            assertFalse(model.open)
            assertEquals(found, model.results.toList(), "closing the panel keeps the results")
        }

    @Test
    fun anInvalidRegexIsAMessageAndNoResultsAndAnEmptyTextClearsThem() =
        withHistoryProject(pages = 3) { project ->
            val (_, model) = editorOf(project::readPage, 3)
            model.change(SearchQuery("Seite"))
            waitUntilDone(model)
            assertEquals(3, model.results.size)
            model.change(SearchQuery("(", regex = true))
            assertTrue(model.results.isEmpty())
            assertFalse(model.searching)
            assertTrue(checkNotNull(model.message).startsWith("Not a valid regular expression"))
            model.change(SearchQuery(""))
            assertNull(model.message)
            assertTrue(model.results.isEmpty())
        }

    @Test
    fun aPageThatCannotBeReadIsSaidAndTheRestIsSearched() =
        withHistoryProject(pages = 4) { project ->
            val read = { n: Int -> if (n == 2) error("broken") else project.readPage(n) }
            val (_, model) = editorOf(read, 4)
            model.change(SearchQuery("Seite"))
            waitUntilDone(model)
            assertEquals(listOf(1, 3, 4), model.results.map { it.page })
            assertTrue(checkNotNull(model.message).startsWith("1 pages could not be read"))
        }

    @Test
    fun startingASearchNeverWaitsForPageFiles() =
        withHistoryProject(pages = 6) { project ->
            val slow = { n: Int -> Thread.sleep(300).let { project.readPage(n) } }
            val (_, model) = editorOf(slow)
            val started = System.nanoTime()
            model.change(SearchQuery("Seite"))
            val millis = (System.nanoTime() - started) / 1_000_000
            assertTrue(millis < 150, "search() took $millis ms")
            model.close()
        }
}
