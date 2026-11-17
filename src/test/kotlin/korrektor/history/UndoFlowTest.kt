package korrektor.history

import korrektor.AppModel
import korrektor.closeProject
import korrektor.editor.BookEditor
import korrektor.editor.SaveOutcome
import korrektor.editor.UndoResult
import korrektor.editor.filesOf
import korrektor.editor.redo
import korrektor.editor.undo
import korrektor.editor.withHistoryProject
import korrektor.format.PageLayer
import korrektor.format.Project
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Undo and redo over several pages, and after the project was closed and opened again (S08.5). */
class UndoFlowTest {
    private fun PageLayer.plain() = copy(history = null)

    /** Every page as the session has it now: the changed one, or the one in the project. */
    private fun pagesOf(
        project: Project,
        editor: BookEditor,
    ): Map<Int, PageLayer> =
        (1..project.pages.size).associateWith { (editor.changed[it] ?: project.readPage(it)).plain() }

    @Test
    fun undoAndRedoAcrossPagesGiveBackEveryPageAsItWasAtEachStep() =
        withHistoryProject(4) { project ->
            val model = AppModel()
            model.openProject(project.folder)
            val editor = checkNotNull(model.editor)
            val states = mutableListOf(pagesOf(project, editor))

            runBlocking {
                editor.show(1)
                editor.setLineText("l1", "Seite eins korrigiert")
                states += pagesOf(project, editor)
                editor.show(2)
                editor.splitLine("l1", 5)
                states += pagesOf(project, editor)
                editor.show(3)
                editor.deleteLine("l2")
                states += pagesOf(project, editor)
                editor.show(1) // the last change was on page 3, and page 1 is shown
            }

            // Three steps back, each on the page it was made on, and every page as it was after the step before.
            for ((i, expected) in listOf(2, 1, 0).withIndex()) {
                val result = editor.undo()
                assertIs<UndoResult.Done>(result)
                assertEquals(3 - i, result.page)
                assertEquals(states[expected], pagesOf(project, editor), "after undo ${i + 1}")
            }
            assertEquals(UndoResult.NothingToDo, editor.undo())
            // And forward again.
            for (expected in listOf(1, 2, 3)) {
                assertIs<UndoResult.Done>(editor.redo())
                assertEquals(states[expected], pagesOf(project, editor), "redo to state $expected")
            }
            assertEquals(UndoResult.NothingToDo, editor.redo())
            model.closeProject()
        }

    @Test
    fun undoAfterAReopenedProjectGoesBackToTheStartAndBackAgain() =
        withHistoryProject(4) { project ->
            val first = AppModel()
            first.openProject(project.folder)
            val editor1 = checkNotNull(first.editor)
            val initial = pagesOf(project, editor1)
            runBlocking {
                for (page in 1..3) {
                    editor1.show(page)
                    editor1.setLineText("l1", "korrigiert $page")
                    editor1.addLineAfter("l1", "neu $page")
                }
                assertEquals(SaveOutcome.Done, checkNotNull(first.saver).saveNow())
            }
            val saved = (1..4).associateWith { project.readPage(it).plain() }
            first.closeProject()

            // Another session: nothing is shown, undo takes the history from the file.
            val second = AppModel()
            second.openProject(project.folder)
            assertNull(second.error)
            val editor2 = checkNotNull(second.editor)
            var steps = 0
            while (true) {
                val result = editor2.undo()
                if (result == UndoResult.NothingToDo) break
                assertIs<UndoResult.Done>(result)
                steps++
            }
            assertEquals(6, steps)
            assertEquals(initial, pagesOf(project, editor2), "everything undone: the pages as imported")
            repeat(6) { assertIs<UndoResult.Done>(editor2.redo()) }
            assertEquals(saved, pagesOf(project, editor2), "everything redone: the pages as saved")

            // The undone and redone state can be saved and opened again.
            runBlocking { assertEquals(SaveOutcome.Done, checkNotNull(second.saver).saveNow()) }
            second.closeProject()
            val third = AppModel()
            third.openProject(project.folder)
            assertTrue(checkNotNull(third.editor).history.canUndo)
            assertEquals(saved, (1..4).associateWith { project.readPage(it).plain() })
            // Nothing but the page files and the history changed.
            assertTrue(filesOf(project).keys.any { it.startsWith("history") })
            third.closeProject()
        }
}
