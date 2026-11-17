package korrektor.editor

import korrektor.format.EditEntry
import korrektor.format.HistoryCodec
import korrektor.format.HistoryEntry
import korrektor.format.Project
import korrektor.format.RedoEntry
import korrektor.format.SavedEntry
import korrektor.format.UndoEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The history is saved together with the pages (T072). */
class HistorySaveTest {
    private fun editorAndSaver(
        project: Project,
        appendHistory: (List<HistoryEntry>) -> Unit = { project.historyLog.appendAll(it.map(HistoryCodec::encode)) },
        write: (Int, korrektor.format.PageLayer) -> Unit = project::writePage,
    ): Pair<BookEditor, AutoSaver> {
        val editor = BookEditor(project::readPage, Dispatchers.IO)
        return editor to AutoSaver(editor, Dispatchers.IO, appendHistory = appendHistory, write = write)
    }

    private fun entryKinds(project: Project) = historyOf(project).map { it::class.simpleName }

    @Test
    fun afterASaveTheHistoryEndsWithTheEntriesAndSavedAndThePagesCarryTheirNumbers() =
        withHistoryProject { project ->
            val (editor, saver) = editorAndSaver(project)
            runBlocking {
                editor.show(1)
                editor.setLineText("l1", "Seite eins korrigiert")
                editor.show(3)
                editor.deleteLine("l2")
                assertEquals(SaveOutcome.Done, saver.saveNow())
            }
            assertEquals(listOf("EditEntry", "EditEntry", "SavedEntry"), entryKinds(project))
            assertEquals(SavedEntry(2), historyOf(project).last())
            assertEquals(1, project.readPage(1).history)
            assertEquals(2, project.readPage(3).history)
            assertNull(project.readPage(2).history)
            assertNull(project.readPage(4).history)
            assertEquals(emptySet(), editor.changedPages)
            assertEquals(emptyList(), editor.history.pendingEntries)
        }

    @Test
    fun aSaveWithNothingToSaveWritesNothingNotEvenASavedEntry() =
        withHistoryProject { project ->
            val (editor, saver) = editorAndSaver(project)
            runBlocking {
                assertEquals(SaveOutcome.Done, saver.saveNow())
                assertEquals(emptyMap(), filesOf(project).filterKeys { it.startsWith("history") })
                editor.show(1)
                editor.setLineText("l1", "x")
                saver.saveNow()
                val before = filesOf(project)
                assertEquals(SaveOutcome.Done, saver.saveNow())
                assertEquals(before.keys, filesOf(project).keys)
                for ((name, bytes) in before) assertContentEquals(bytes, filesOf(project).getValue(name), name)
            }
        }

    @Test
    fun undoAndRedoAreSavedAsEntriesToo() =
        withHistoryProject { project ->
            val (editor, saver) = editorAndSaver(project)
            runBlocking {
                editor.show(1)
                editor.setLineText("l1", "Seite eins korrigiert")
                editor.undo()
                editor.redo()
                assertEquals(SaveOutcome.Done, saver.saveNow())
            }
            val entries = historyOf(project)
            assertIs<EditEntry>(entries[0])
            assertIs<UndoEntry>(entries[1])
            assertIs<RedoEntry>(entries[2])
            assertEquals(SavedEntry(3), entries[3])
            assertEquals(3, project.readPage(1).history)
            assertEquals("Seite eins korrigiert", project.texts(1).first())
        }

    @Test
    fun aHistoryThatCannotBeWrittenKeepsTheEntriesAndNothingElseIsWritten() =
        withHistoryProject { project ->
            val failing = AtomicBoolean(true)
            val (editor, saver) =
                editorAndSaver(project, appendHistory = {
                    if (failing.get()) throw IOException("disk full")
                    project.historyLog.appendAll(it.map(HistoryCodec::encode))
                })
            runBlocking {
                editor.show(1)
                editor.setLineText("l1", "Seite eins korrigiert")
                val before = filesOf(project)
                val outcome = saver.saveNow()
                assertIs<SaveOutcome.Failed>(outcome)
                assertTrue("disk full" in outcome.reason, outcome.reason)
                assertEquals(before.keys, filesOf(project).keys, "no page and no history file was written")
                assertEquals(setOf(1), editor.changedPages)
                assertEquals(1, editor.history.pendingEntries.size, "the entry waits for the retry")

                failing.set(false)
                assertEquals(SaveOutcome.Done, saver.saveNow())
            }
            // Written once.
            assertEquals(listOf("EditEntry", "SavedEntry"), entryKinds(project))
            assertEquals(1, project.readPage(1).history)
        }

    @Test
    fun aPageThatCannotBeWrittenLeavesItsEntryWithoutSavedUntilARetryWritesIt() =
        withHistoryProject { project ->
            val failing = AtomicBoolean(true)
            val (editor, saver) =
                editorAndSaver(project, write = { page, layer ->
                    if (page == 2 && failing.get()) throw IOException("read-only")
                    project.writePage(page, layer)
                })
            runBlocking {
                editor.show(1)
                editor.setLineText("l1", "eins")
                editor.show(2)
                editor.setLineText("l1", "zwei")
                assertIs<SaveOutcome.Failed>(saver.saveNow())
                // The entries are in the file, no `saved` follows them, and page 1 is written.
                assertEquals(listOf("EditEntry", "EditEntry"), entryKinds(project))
                assertEquals(setOf(2), editor.changedPages)
                assertEquals("eins", project.texts(1).first())

                failing.set(false)
                assertEquals(SaveOutcome.Done, saver.saveNow())
            }
            assertEquals(listOf("EditEntry", "EditEntry", "SavedEntry"), entryKinds(project))
            assertEquals(SavedEntry(2), historyOf(project).last())
            assertEquals("zwei", project.texts(2).first())
        }

    @Test
    fun aSavedEntryThatCannotBeWrittenIsWrittenByTheNextSave() =
        withHistoryProject { project ->
            val failSaved = AtomicBoolean(true)
            val (editor, saver) =
                editorAndSaver(project, appendHistory = { entries ->
                    if (failSaved.get() && entries.any { it is SavedEntry }) throw IOException("busy")
                    project.historyLog.appendAll(entries.map(HistoryCodec::encode))
                })
            runBlocking {
                editor.show(1)
                editor.setLineText("l1", "eins")
                assertIs<SaveOutcome.Failed>(saver.saveNow())
                assertEquals(emptySet(), editor.changedPages, "the page is written")
                assertEquals(listOf("EditEntry"), entryKinds(project))
                failSaved.set(false)
                assertEquals(SaveOutcome.Done, saver.saveNow())
            }
            assertEquals(listOf("EditEntry", "SavedEntry"), entryKinds(project))
        }

    @Test
    fun typingDuringTheAppendIsNotDelayedAndItsEntryIsNotInTheFileBeforeItsPage() =
        withHistoryProject { project ->
            val gate = CompletableDeferred<Unit>()
            val appending = CompletableDeferred<Unit>()
            val first = AtomicBoolean(true)
            val (editor, saver) =
                editorAndSaver(project, appendHistory = { entries ->
                    if (first.getAndSet(false)) {
                        appending.complete(Unit)
                        runBlocking { gate.await() }
                    }
                    project.historyLog.appendAll(entries.map(HistoryCodec::encode))
                })
            runBlocking {
                editor.show(1)
                editor.setLineText("l1", "eins")
                val save = async(Dispatchers.IO) { saver.saveNow() }
                appending.await()
                // The save is blocked in the append: typing does not wait for it.
                editor.setLineText("l2", "unten korrigiert")
                assertEquals(1, editor.history.pendingEntries.size)
                gate.complete(Unit)
                assertEquals(SaveOutcome.Done, save.await())

                // The entry of the typing is not in the file, and its page is still unsaved.
                assertEquals(listOf("EditEntry", "SavedEntry"), entryKinds(project))
                assertEquals(setOf(1), editor.changedPages)
                assertEquals(SaveOutcome.Done, saver.saveNow())
            }
            assertEquals(listOf("EditEntry", "SavedEntry", "EditEntry", "SavedEntry"), entryKinds(project))
            assertEquals(SavedEntry(2), historyOf(project).last())
            assertEquals(listOf("eins", "unten korrigiert"), project.texts(1))
        }
}
