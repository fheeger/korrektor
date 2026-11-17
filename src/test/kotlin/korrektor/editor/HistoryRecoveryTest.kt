package korrektor.editor

import korrektor.AppModel
import korrektor.closeProject
import korrektor.format.EditEntry
import korrektor.format.HistoryCodec
import korrektor.format.HistoryEntry
import korrektor.format.LineChange
import korrektor.format.PageLayer
import korrektor.format.Project
import korrektor.format.SavedEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.io.path.appendBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * After a crash the history and the pages agree again (T072). A save appends its entries, writes the pages, appends
 * `saved`; each test stops that sequence at another point and opens the project.
 */
class HistoryRecoveryTest {
    /** The edits of a session: page 1 and page 2 typed in. Returns what a save would write, not writing anything. */
    private class Crashed(
        val layers: Map<Int, PageLayer>,
        val entries: List<HistoryEntry>,
    )

    private fun session(project: Project): Crashed {
        val editor = BookEditor(project::readPage, Dispatchers.Unconfined)
        runBlocking {
            editor.show(1)
            editor.setLineText("l1", "eins korrigiert")
            editor.show(2)
            editor.setLineText("l1", "zwei korrigiert")
        }
        val taken = editor.snapshot()
        return Crashed(taken.layers, taken.entries)
    }

    private fun Project.appendEntries(entries: List<HistoryEntry>) =
        historyLog.appendAll(entries.map(HistoryCodec::encode))

    private fun Project.firstLine(page: Int) = texts(page).first()

    private fun kinds(project: Project) = historyOf(project).map { it::class.simpleName }

    @Test
    fun crashAfterTheEntriesBeforeAnyPage() =
        withHistoryProject { project ->
            val crashed = session(project)
            project.appendEntries(crashed.entries)

            val result = HistoryRecovery.recover(project)

            assertEquals(setOf(1, 2), result.repairedPages)
            assertEquals(2, result.lastSeq)
            assertEquals("eins korrigiert", project.firstLine(1))
            assertEquals("zwei korrigiert", project.firstLine(2))
            assertEquals(crashed.layers.getValue(1), project.readPage(1))
            assertEquals(crashed.layers.getValue(2), project.readPage(2))
            assertEquals(SavedEntry(2), historyOf(project).last())
            assertEquals(listOf("EditEntry", "EditEntry", "SavedEntry"), kinds(project))
        }

    @Test
    fun crashAfterTheFirstOfTwoPages() =
        withHistoryProject { project ->
            val crashed = session(project)
            project.appendEntries(crashed.entries)
            project.writePage(1, crashed.layers.getValue(1))
            val page1 = filesOf(project).getValue("pages\\0001.json".replace('\\', java.io.File.separatorChar))

            val result = HistoryRecovery.recover(project)

            assertEquals(setOf(2), result.repairedPages)
            assertEquals("zwei korrigiert", project.firstLine(2))
            // Page 1 was complete: it is not touched, and nothing is applied twice.
            assertContentEquals(
                page1,
                filesOf(project).getValue("pages\\0001.json".replace('\\', java.io.File.separatorChar)),
            )
            assertEquals(SavedEntry(2), historyOf(project).last())
        }

    @Test
    fun crashAfterTheLastPageBeforeSaved() =
        withHistoryProject { project ->
            val crashed = session(project)
            project.appendEntries(crashed.entries)
            crashed.layers.forEach { (page, layer) -> project.writePage(page, layer) }
            val pages = filesOf(project).filterKeys { it.startsWith("pages") }

            val result = HistoryRecovery.recover(project)

            assertEquals(emptySet(), result.repairedPages)
            assertEquals(pages.keys, filesOf(project).filterKeys { it.startsWith("pages") }.keys)
            for ((name, bytes) in pages) assertContentEquals(bytes, filesOf(project).getValue(name), name)
            assertEquals(listOf("EditEntry", "EditEntry", "SavedEntry"), kinds(project))
        }

    @Test
    fun crashInTheMiddleOfTheAppendLeavesATornLineThatIsIgnored() =
        withHistoryProject { project ->
            val crashed = session(project)
            project.appendEntries(crashed.entries.take(1))
            project.historyLog.let {
                project.folder.resolve("history/book.jsonl").appendBytes("{\"changes\": [{\"ind".toByteArray())
            }

            val result = HistoryRecovery.recover(project)

            assertEquals(setOf(1), result.repairedPages)
            assertEquals(1, result.lastSeq)
            assertEquals("eins korrigiert", project.firstLine(1))
            assertEquals("Seite 2 oben", project.firstLine(2), "the entry that was not written is not applied")
            // The torn line is gone, and the next entry is readable.
            assertEquals(listOf("EditEntry", "SavedEntry"), kinds(project))
        }

    @Test
    fun aProjectWhoseLastEntryIsSavedOpensWithoutAnyChange() =
        withHistoryProject { project ->
            val crashed = session(project)
            project.appendEntries(crashed.entries + SavedEntry(2))
            crashed.layers.forEach { (page, layer) -> project.writePage(page, layer) }
            val before = filesOf(project)

            val result = HistoryRecovery.recover(project)

            assertEquals(emptySet(), result.repairedPages)
            assertEquals(2, result.lastSeq)
            assertEquals(before.keys, filesOf(project).keys)
            for ((name, bytes) in before) assertContentEquals(bytes, filesOf(project).getValue(name), name)
        }

    @Test
    fun aProjectWithoutAHistoryChangesNothingAndMakesNoFile() =
        withHistoryProject { project ->
            val before = filesOf(project)
            val result = HistoryRecovery.recover(project)
            assertEquals(0, result.lastSeq)
            assertEquals(emptySet(), result.repairedPages)
            assertEquals(before.keys, filesOf(project).keys)
            assertFalse(
                project.folder
                    .resolve("history")
                    .toFile()
                    .exists(),
            )
        }

    @Test
    fun aStepOfSeveralChangesOnOnePageIsMadeCompletelyOrNotAtAll() =
        withHistoryProject { project ->
            val page = project.readPage(1)
            val lines = page.regions.single().lines
            val first = lines[0].copy(words = lines[0].words.map { it.copy(text = it.text.uppercase()) })
            val second = lines[1].copy(words = lines[1].words.map { it.copy(text = it.text.uppercase()) })
            val changes =
                listOf(
                    LineChange(1, "r1", 0, listOf(lines[0]), listOf(first)),
                    LineChange(1, "r1", 1, listOf(lines[1]), listOf(second)),
                )
            project.appendEntries(listOf(EditEntry(1, changes)))

            HistoryRecovery.recover(project)

            assertEquals(listOf(first.text, second.text), project.texts(1))
            assertEquals(1, project.readPage(1).history)
        }

    @Test
    fun aChangeThatDoesNotFitThePageIsSkipped() =
        withHistoryProject { project ->
            val lines =
                project
                    .readPage(1)
                    .regions
                    .single()
                    .lines
            val other = lines[0].copy(words = lines[0].words.map { it.copy(text = "ganz anders") })
            val never = lines[0].copy(words = lines[0].words.map { it.copy(text = "nie") })
            project.appendEntries(listOf(EditEntry(1, listOf(LineChange(1, "r1", 0, listOf(other), listOf(never))))))
            val before = project.readPage(1)

            HistoryRecovery.recover(project)

            assertEquals(before, project.readPage(1))
            assertEquals(SavedEntry(1), historyOf(project).last())
        }

    @Test
    fun openingACrashedProjectRepairsItAndTheNextEntryContinuesTheNumbers() =
        withHistoryProject { project ->
            val crashed = session(project)
            project.appendEntries(crashed.entries)
            project.writePage(1, crashed.layers.getValue(1))
            val model = AppModel()

            model.openProject(project.folder)

            assertNull(model.error)
            assertEquals("zwei korrigiert", project.firstLine(2))
            val editor = model.editor!!
            runBlocking { editor.show(3) }
            editor.setLineText("l1", "drei korrigiert")
            assertEquals(listOf(3), editor.history.pendingEntries.map { (it as EditEntry).seq })
            assertEquals(3, editor.layer?.history)
            model.closeProject()
        }
}
