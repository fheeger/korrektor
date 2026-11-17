package korrektor.search

import korrektor.AppModel
import korrektor.closeProject
import korrektor.editor.PageEdits
import korrektor.editor.SaveOutcome
import korrektor.editor.UndoResult
import korrektor.editor.redo
import korrektor.editor.setStatus
import korrektor.editor.statusOf
import korrektor.editor.undo
import korrektor.editor.withHistoryProject
import korrektor.format.PageStatus
import korrektor.format.Project
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Find and replace together with saving, undo, reopening and a crash, on a project made by the import (T089). */
class SearchFlowTest {
    private val pages = 30

    private fun lines(
        project: Project,
        page: Int,
    ) = PageEdits.readingOrder(project.readPage(page)).map { it.text }

    private fun AppModel.replaceAll(
        from: String,
        to: String,
    ): SearchModel {
        val search = checkNotNull(search)
        search.change(SearchQuery(from, matchCase = true))
        runBlocking { withTimeout(10_000) { while (search.searching) delay(2) } }
        search.replacement = to
        runBlocking { search.replaceSelected() }
        return search
    }

    @Test
    fun replaceOnAllPagesThenUndoRedoSaveReopenAndUndoAgain() =
        withHistoryProject(pages = pages) { project ->
            val first = AppModel()
            first.openProject(project.folder)
            val editor = checkNotNull(first.editor)
            editor.setStatus(3, PageStatus.CORRECTED)
            editor.setStatus(7, PageStatus.CORRECTED)
            val search = first.replaceAll("Seite", "Blatt")
            assertEquals("Replaced $pages matches on $pages pages.", search.outcome)
            assertEquals(2, editor.statuses.size)
            assertEquals(PageStatus.CORRECTED, editor.statusOf(3), "the status of a page does not change")

            assertIs<UndoResult.Done>(editor.undo())
            assertIs<UndoResult.Done>(editor.redo())
            assertEquals(SaveOutcome.Done, runBlocking { checkNotNull(first.saver).saveNow() })
            first.closeProject()

            for (page in 1..pages) assertEquals(listOf("Blatt $page oben", "unten"), lines(project, page))
            assertEquals(setOf(3, 7), Project.open(project.folder).statuses.keys, "the statuses are as they were")

            val second = AppModel()
            second.openProject(project.folder)
            val reopened = checkNotNull(second.editor)
            assertEquals(PageStatus.CORRECTED, reopened.statusOf(7))
            // The replacement is one step: undoing it puts all pages back, and then the two statuses are next.
            assertIs<UndoResult.Done>(reopened.undo())
            for (page in 1..pages) {
                val text = PageEdits.readingOrder(reopened.changed.getValue(page)).map { it.text }
                assertEquals(listOf("Seite $page oben", "unten"), text, "page $page")
            }
            assertEquals(PageStatus.CORRECTED, reopened.statusOf(7), "the step before it was a status")
            assertEquals(SaveOutcome.Done, runBlocking { checkNotNull(second.saver).saveNow() })
            second.closeProject()
            for (page in 1..pages) assertEquals(listOf("Seite $page oben", "unten"), lines(project, page))
        }

    @Test
    fun aCrashAfterTheReplacementLeavesItInTheHistoryAndTheNextStartRepairsThePages() =
        withHistoryProject(pages = pages) { project ->
            // The history is written but the pages cannot be: as if the app was killed in between.
            val crashing = AppModel(pageWriter = { _ -> { _, _ -> throw IOException("killed") } })
            crashing.openProject(project.folder)
            crashing.replaceAll("unten", "darunter")
            val outcome = runBlocking { checkNotNull(crashing.saver).saveNow() }
            assertNotEquals(SaveOutcome.Done, outcome)
            for (page in 1..pages) {
                assertEquals(
                    listOf("Seite $page oben", "unten"),
                    lines(project, page),
                    "not on disk",
                )
            }
            // The lock of the "killed" app: its project is closed without saving, as a crash would leave it.
            crashing.closeProject()

            val next = AppModel()
            next.openProject(project.folder)
            assertTrue(next.error == null, next.error)
            for (page in 1..pages) {
                assertEquals(
                    listOf("Seite $page oben", "darunter"),
                    lines(project, page),
                    "page $page",
                )
            }
            val editor = checkNotNull(next.editor)
            assertIs<UndoResult.Done>(editor.undo())
            assertEquals(UndoResult.NothingToDo, editor.undo(), "the replacement was one step")
            next.closeProject()
        }

    @Test
    fun severalReplacementsAndTheirUndoKeepTheOrderOfTheSteps() =
        withHistoryProject(pages = 6) { project ->
            val model = AppModel()
            model.openProject(project.folder)
            val editor = checkNotNull(model.editor)
            model.replaceAll("oben", "droben")
            model.replaceAll("droben", "hoch")
            assertEquals(2, editor.history.undoSteps.size)
            assertIs<UndoResult.Done>(editor.undo())
            assertEquals("Seite 4 droben", editor.changed.getValue(4).let { PageEdits.readingOrder(it)[0].text })
            assertIs<UndoResult.Done>(editor.undo())
            assertEquals("Seite 4 oben", editor.changed.getValue(4).let { PageEdits.readingOrder(it)[0].text })
            model.closeProject()
        }
}
