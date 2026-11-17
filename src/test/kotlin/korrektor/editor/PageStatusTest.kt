package korrektor.editor

import korrektor.AppModel
import korrektor.closeProject
import korrektor.format.EditEntry
import korrektor.format.HistoryCodec
import korrektor.format.PageStatus
import korrektor.format.Project
import korrektor.format.SavedEntry
import korrektor.format.StatusChange
import korrektor.format.setStatuses
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The page status in the editor: recording, undo, saving and recovery (T077). */
class PageStatusTest {
    private val corrected = PageStatus.CORRECTED
    private val unchecked = PageStatus.UNCHECKED

    private fun editorOf(project: Project) =
        BookEditor(project::readPage, Dispatchers.Unconfined, initialStatuses = project.statuses)

    @Test
    fun aChangeOfStatusIsOneStepThatUndoAndRedoTakeBackAndBringBack() =
        withHistoryProject { project ->
            val editor = editorOf(project)
            editor.setStatus(3, corrected)
            assertEquals(corrected, editor.statusOf(3))
            assertEquals(unchecked, editor.statusOf(2))
            assertEquals(1, editor.history.undoSteps.size)

            val undone = editor.undo()
            assertEquals(UndoResult.Done(3, null), undone)
            assertEquals(unchecked, editor.statusOf(3))
            assertEquals(emptyMap(), editor.statuses)

            assertEquals(UndoResult.Done(3, null), editor.redo())
            assertEquals(corrected, editor.statusOf(3))
            assertEquals(UndoResult.NothingToDo, editor.redo())
        }

    @Test
    fun theSameStatusAgainIsNoStepAndANewStepClearsTheRedoSteps() =
        withHistoryProject { project ->
            val editor = editorOf(project)
            editor.setStatus(1, unchecked)
            assertEquals(emptyList(), editor.history.undoSteps)

            editor.setStatus(1, corrected)
            editor.setStatus(1, corrected)
            assertEquals(1, editor.history.undoSteps.size)
            editor.undo()
            assertTrue(editor.history.canRedo)
            editor.setStatus(2, corrected)
            assertFalse(editor.history.canRedo)
        }

    @Test
    fun statusChangesAndCorrectionsOnOtherPagesUndoInOrder() =
        withHistoryProject { project ->
            val editor = editorOf(project)
            runBlocking { editor.show(2) }
            editor.setLineText("l1", "zwei korrigiert")
            editor.setStatus(2, corrected)
            editor.setStatus(4, corrected)

            assertEquals(UndoResult.Done(4, null), editor.undo())
            assertEquals(UndoResult.Done(2, null), editor.undo())
            assertEquals(unchecked, editor.statusOf(2))
            assertEquals("zwei korrigiert", editor.lines.first().text)
            val result = editor.undo()
            assertIs<UndoResult.Done>(result)
            assertEquals("Seite 2 oben", editor.lines.first().text)
        }

    @Test
    fun aStatusThatIsNotWhatTheStepCameFromDoesNotFit() =
        withHistoryProject { project ->
            val editor = editorOf(project)
            editor.setStatus(1, corrected)
            editor.statuses = emptyMap() // changed behind the history's back

            assertEquals(UndoResult.DoesNotFit, editor.undo())
            assertEquals(UndoResult.NothingToDo, editor.undo())
        }

    @Test
    fun aStatusChangeIsSavedAloneAndTheProjectHasItAfterwards() =
        withHistoryProject { project ->
            val editor = editorOf(project)
            val saver =
                AutoSaver(
                    editor,
                    Dispatchers.IO,
                    appendHistory = { project.historyLog.appendAll(it.map(HistoryCodec::encode)) },
                    writeStatuses = project::setStatuses,
                    write = project::writePage,
                )
            editor.setStatus(2, corrected)
            assertEquals(SaveStatus.NOT_SAVED, saver.status)
            assertTrue(editor.hasUnsaved)

            assertEquals(SaveOutcome.Done, runBlocking { saver.saveNow() })

            assertEquals(SaveStatus.SAVED, saver.status)
            assertEquals(mapOf(2 to corrected), Project.open(project.folder).statuses)
            assertEquals(
                listOf(
                    EditEntry(1, listOf(StatusChange(2, unchecked, corrected))),
                    SavedEntry(1),
                ),
                historyOf(project),
            )
            assertNull(project.readPage(2).history, "a status change does not touch the page file")
        }

    @Test
    fun ifTheProjectFileCannotBeWrittenTheSaveFailsAndIsTriedAgain() =
        withHistoryProject { project ->
            val editor = editorOf(project)
            var failing = true
            val saver =
                AutoSaver(
                    editor,
                    Dispatchers.IO,
                    appendHistory = { project.historyLog.appendAll(it.map(HistoryCodec::encode)) },
                    writeStatuses = {
                        if (failing) throw IOException("disk full")
                        project.setStatuses(it)
                    },
                    write = project::writePage,
                )
            editor.setStatus(1, corrected)

            val failed = runBlocking { saver.saveNow() }

            assertIs<SaveOutcome.Failed>(failed)
            assertEquals("disk full", failed.reason)
            assertEquals(corrected, editor.statusOf(1))
            assertEquals(SaveStatus.NOT_SAVED, saver.status)
            assertEquals(listOf("EditEntry"), historyOf(project).map { it::class.simpleName }, "no saved entry yet")

            failing = false
            assertEquals(SaveOutcome.Done, runBlocking { saver.saveNow() })
            assertEquals(mapOf(1 to corrected), Project.open(project.folder).statuses)
            assertEquals(listOf("EditEntry", "SavedEntry"), historyOf(project).map { it::class.simpleName })
        }

    @Test
    fun aStatusChangedWhileTheFileIsWrittenStaysUnsaved() =
        withHistoryProject { project ->
            val editor = editorOf(project)
            val saver =
                AutoSaver(
                    editor,
                    Dispatchers.IO,
                    appendHistory = { project.historyLog.appendAll(it.map(HistoryCodec::encode)) },
                    writeStatuses = {
                        editor.setStatus(3, corrected) // the user goes on while the file is written
                        project.setStatuses(it)
                    },
                    write = project::writePage,
                )
            editor.setStatus(1, corrected)

            runBlocking { saver.saveNow() }

            assertTrue(editor.statusesChanged)
            assertEquals(mapOf(1 to corrected), project.statuses)
        }

    @Test
    fun theProjectOpensWithItsStatusesAndTheirUndoWorksAfterReopening() =
        withHistoryProject { project ->
            val first = AppModel()
            first.openProject(project.folder)
            val editor = checkNotNull(first.editor)
            editor.setStatus(2, corrected)
            editor.setStatus(3, corrected)
            assertEquals(SaveOutcome.Done, runBlocking { checkNotNull(first.saver).saveNow() })
            first.closeProject()

            val second = AppModel()
            second.openProject(project.folder)
            val reopened = checkNotNull(second.editor)
            assertEquals(mapOf(2 to corrected, 3 to corrected), reopened.statuses)
            assertEquals(UndoResult.Done(3, null), reopened.undo())
            assertEquals(mapOf(2 to corrected), reopened.statuses)
            assertEquals(SaveOutcome.Done, runBlocking { checkNotNull(second.saver).saveNow() })
            assertEquals(mapOf(2 to corrected), Project.open(project.folder).statuses)
            second.closeProject()
        }
}
