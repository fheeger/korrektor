package korrektor.search

import korrektor.AppModel
import korrektor.closeProject
import korrektor.editor.BookEditor
import korrektor.editor.PageEdits
import korrektor.editor.SaveOutcome
import korrektor.editor.UndoResult
import korrektor.editor.redo
import korrektor.editor.setStatus
import korrektor.editor.statusOf
import korrektor.editor.undo
import korrektor.editor.withHistoryProject
import korrektor.format.PageLayer
import korrektor.format.PageStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Replacing the hits: all checked ones as one step, one by one, and what stays as it was (T088). */
class SearchReplaceTest {
    /** The original page: `aa x aa` and `Seite n`. */
    private fun original(n: Int): PageLayer = layerOf("aa x aa", "Seite $n", number = n)

    private fun editorOf(read: (Int) -> PageLayer = ::original) = BookEditor(read, Dispatchers.Unconfined)

    private fun searched(
        editor: BookEditor,
        query: SearchQuery,
        pages: Int = 12,
    ): SearchModel {
        val model = SearchModel(editor, pages, Dispatchers.Unconfined, Dispatchers.IO, pause = 0, chunk = 4)
        model.change(query)
        runBlocking { withTimeout(10_000) { while (model.searching) delay(2) } }
        return model
    }

    private fun texts(
        editor: BookEditor,
        page: Int,
    ): List<String> = PageEdits.readingOrder(editor.currentLayerForTest(page)).map { it.text }

    private fun BookEditor.currentLayerForTest(page: Int): PageLayer = changed[page] ?: original(page)

    @Test
    fun replacingOnManyPagesIsOneStepThatUndoAndRedoTakeBackAndBringBack() {
        val editor = editorOf()
        val model = searched(editor, SearchQuery("aa"))
        assertEquals(24, model.results.size)
        model.replacement = "b"
        runBlocking { model.replaceSelected() }

        assertEquals("Replaced 24 matches on 12 pages.", model.outcome)
        assertTrue(model.results.isEmpty())
        assertEquals((1..12).toSet(), editor.changedPages)
        assertEquals(1, editor.history.undoSteps.size)
        for (page in 1..12) assertEquals(listOf("b x b", "Seite $page"), texts(editor, page))

        assertIs<UndoResult.Done>(editor.undo())
        for (page in 1..12) assertEquals(listOf("aa x aa", "Seite $page"), texts(editor, page), "page $page")
        assertEquals(UndoResult.NothingToDo, editor.undo())
        assertIs<UndoResult.Done>(editor.redo())
        for (page in 1..12) assertEquals(listOf("b x b", "Seite $page"), texts(editor, page))
    }

    @Test
    fun uncheckedHitsStayAndTheOnesInChangedLinesAreMovedToWhereTheyAre() {
        val editor = editorOf()
        val model = searched(editor, SearchQuery("aa"))
        model.setSelected(0, false) // page 1, the first aa
        model.setSelected(5, false) // page 3, the second aa
        model.replacement = "b"
        runBlocking { model.replaceSelected() }

        assertEquals(listOf("aa x b", "Seite 1"), texts(editor, 1))
        assertEquals(listOf("b x aa", "Seite 3"), texts(editor, 3))
        assertEquals(listOf("b x b", "Seite 2"), texts(editor, 2))
        assertEquals(2, model.results.size)
        assertEquals(setOf(0, 1), model.excluded, "what was unchecked still is")
        val (first, second) = model.results
        assertEquals(1, first.page)
        assertEquals("aa x b", first.lineText)
        assertEquals("aa", first.found)
        assertEquals(3, second.page)
        assertEquals("b x aa", second.lineText)
        assertEquals(4, second.start)
        assertEquals("aa", second.found)
    }

    @Test
    fun severalHitsInALineAndGroupsOfARegexAreAllRight() {
        val editor = editorOf()
        val model = searched(editor, SearchQuery("""(\w+) x""", regex = true))
        assertEquals(12, model.results.size)
        model.replacement = "<$1> and"
        runBlocking { model.replaceSelected() }
        assertEquals(listOf("<aa> and aa", "Seite 4"), texts(editor, 4))

        // A replacement that is longer or shorter than the hit moves the next hit in the line.
        val again = searched(editor, SearchQuery("aa"), 12)
        assertEquals(24, again.results.size, "two hits in each of the twelve lines")
        again.replacement = "ccc"
        runBlocking { again.replaceSelected() }
        assertEquals(listOf("<ccc> and ccc", "Seite 4"), texts(editor, 4))
    }

    @Test
    fun theStatusOfAPageDoesNotChangeAndTheStatusStepIsKept() {
        val editor = editorOf()
        editor.setStatus(2, PageStatus.CORRECTED)
        val before = editor.statuses
        val model = searched(editor, SearchQuery("aa"))
        model.replacement = "b"
        runBlocking { model.replaceSelected() }
        assertEquals(PageStatus.CORRECTED, editor.statusOf(2))
        assertEquals(before, editor.statuses)
        assertEquals(2, editor.history.undoSteps.size)
        assertIs<UndoResult.Done>(editor.undo())
        assertEquals(PageStatus.CORRECTED, editor.statusOf(2), "undoing the replacement leaves the status")
    }

    @Test
    fun aHitWhoseLineChangedSinceTheSearchIsSkippedAndCounted() {
        val editor = editorOf()
        val model = searched(editor, SearchQuery("aa"))
        runBlocking { editor.show(3) }
        editor.setLineText("l1", "zz x aa")
        model.replacement = "b"
        runBlocking { model.replaceSelected() }

        assertEquals(listOf("zz x aa", "Seite 3"), texts(editor, 3), "the edit is kept")
        assertEquals(listOf("b x b", "Seite 4"), texts(editor, 4))
        val said = "Replaced 22 matches on 11 pages. 2 not replaced: their line had changed since the search."
        assertEquals(said, model.outcome)
        assertTrue(model.results.isEmpty())
    }

    @Test
    fun aPageThatTheUserEditedWhileItWasBeingReadIsLeftAlone() {
        val blockNext = AtomicBoolean(false)
        val reading = CountDownLatch(1)
        val go = CountDownLatch(1)
        // The next read of page 5 waits: that is the plan being made while the user types.
        val read = { n: Int ->
            if (n == 5 && blockNext.compareAndSet(true, false)) {
                reading.countDown()
                go.await(10, TimeUnit.SECONDS)
            }
            original(n)
        }
        val editor = editorOf(read)
        val model = searched(editor, SearchQuery("aa"), 6)
        model.replacement = "b"
        blockNext.set(true)
        runBlocking {
            val replacing = async(Dispatchers.Default) { model.replaceSelected() }
            assertTrue(reading.await(10, TimeUnit.SECONDS))
            editor.show(5)
            editor.setLineText("l1", "zz x aa")
            go.countDown()
            replacing.await()
        }
        assertEquals(listOf("zz x aa", "Seite 5"), texts(editor, 5), "what the user typed is kept")
        assertEquals(listOf("b x b", "Seite 4"), texts(editor, 4))
        assertTrue(checkNotNull(model.outcome).contains("2 not replaced"), model.outcome)
    }

    @Test
    fun replacingTheHitsOneByOneEachIsAStepOfItsOwn() {
        val editor = editorOf()
        val model = searched(editor, SearchQuery("aa"))
        model.replacement = "bbbb"
        model.current = 0
        runBlocking { model.replaceCurrent() }
        assertEquals(listOf("bbbb x aa", "Seite 1"), texts(editor, 1))
        assertEquals(23, model.results.size)
        assertEquals(0, model.current, "the next hit is the current one")
        assertEquals(7, model.results[0].start, "it moved with the longer text")
        assertEquals("aa", model.results[0].found)
        assertEquals(1, editor.history.undoSteps.size)

        runBlocking { model.replaceCurrent() }
        assertEquals(listOf("bbbb x bbbb", "Seite 1"), texts(editor, 1))
        assertEquals(2, editor.history.undoSteps.size)
        assertEquals(2, model.results[0].page)

        assertIs<UndoResult.Done>(editor.undo())
        assertEquals(listOf("bbbb x aa", "Seite 1"), texts(editor, 1))
        assertIs<UndoResult.Done>(editor.undo())
        assertEquals(listOf("aa x aa", "Seite 1"), texts(editor, 1))
    }

    @Test
    fun replaceAndGoOnOnlyGoesToTheFirstHitWhenNoneWasGoneToYet() {
        val editor = editorOf()
        val model = searched(editor, SearchQuery("aa"))
        model.replacement = "b"
        var opened: Int? = null
        var stepped: Boolean? = null
        runBlocking { model.replaceAndGoOn({ opened = it }, { stepped = it }) }
        assertEquals(true, stepped)
        assertNull(opened)
        assertEquals(24, model.results.size, "nothing was replaced")
        assertEquals(0, editor.history.undoSteps.size)

        model.current = 3
        runBlocking { model.replaceAndGoOn({ opened = it }, { stepped = null }) }
        assertEquals(3, opened, "the hit after the replaced one is shown")
        assertEquals(23, model.results.size)
    }

    @Test
    fun aReplacementThatIsNotValidChangesNothing() {
        val editor = editorOf()
        val model = searched(editor, SearchQuery("""(a)a""", regex = true))
        model.replacement = "$4"
        runBlocking { model.replaceSelected() }
        assertTrue(checkNotNull(model.outcome).startsWith("The replacement is not valid"), model.outcome)
        assertEquals(0, editor.history.undoSteps.size)
        assertTrue(editor.changed.isEmpty())
        assertEquals(24, model.results.size)
    }

    @Test
    fun nothingCheckedOrNoSearchReplacesNothing() {
        val editor = editorOf()
        val model = searched(editor, SearchQuery("aa"))
        model.selectNone()
        runBlocking { model.replaceSelected() }
        assertEquals(0, editor.history.undoSteps.size)
        val empty = searched(editor, SearchQuery(""))
        runBlocking { empty.replaceSelected() }
        assertEquals(0, editor.history.undoSteps.size)
    }

    @Test
    fun whileReplacingTheModelSaysSoAndASecondReplacementDoesNotStart() {
        val slow = { n: Int -> Thread.sleep(100).let { original(n) } }
        val editor = editorOf(slow)
        val model = searched(editor, SearchQuery("aa"), 3)
        model.replacement = "b"
        runBlocking {
            val first = async(Dispatchers.Default) { model.replaceSelected() }
            withTimeout(5_000) { while (!model.replacing) delay(1) }
            assertTrue(model.replacing)
            model.replaceSelected() // returns at once: one is running
            first.await()
        }
        assertFalse(model.replacing)
        assertEquals(1, editor.history.undoSteps.size)
    }

    @Test
    fun theReplacementIsSavedAndStillThereAfterReopeningWithOneStepOfHistory() =
        withHistoryProject(pages = 5) { project ->
            val first = AppModel()
            first.openProject(project.folder)
            val search = checkNotNull(first.search)
            search.change(SearchQuery("Seite"))
            runBlocking { withTimeout(10_000) { while (search.searching) delay(2) } }
            assertEquals(5, search.results.size)
            search.replacement = "Blatt"
            runBlocking { search.replaceSelected() }
            assertEquals(SaveOutcome.Done, runBlocking { checkNotNull(first.saver).saveNow() })
            first.closeProject()

            val second = AppModel()
            second.openProject(project.folder)
            val editor = checkNotNull(second.editor)
            for (page in 1..5) {
                val lines = PageEdits.readingOrder(project.readPage(page)).map { it.text }
                assertEquals(listOf("Blatt $page oben", "unten"), lines)
            }
            assertIs<UndoResult.Done>(editor.undo())
            assertEquals(UndoResult.NothingToDo, editor.undo(), "the whole replacement was one step")
            for (page in 1..5) {
                val lines = PageEdits.readingOrder(editor.changed.getValue(page)).map { it.text }
                assertEquals(listOf("Seite $page oben", "unten"), lines)
            }
            second.closeProject()
        }
}
