package korrektor.editor

import korrektor.AppModel
import korrektor.closeProject
import korrektor.format.Box
import korrektor.format.EditEntry
import korrektor.format.HistoryCodec
import korrektor.format.Line
import korrektor.format.LineChange
import korrektor.format.Word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The fixes from the S08 code review (T075). */
class HistoryReviewFixesTest {
    private fun line(text: String) =
        Line("l1", Box(1.0, 2.0, 3.0, 4.0), "l1", listOf(Word(text, Box(1.0, 2.0, 3.0, 4.0))))

    @Test
    fun undoThatReadsADamagedOlderPartOfTheHistoryFailsInsteadOfCrashing() =
        withHistoryProject { project ->
            val entries =
                (1..300).map {
                    EditEntry(
                        it,
                        listOf(LineChange(1, "r1", 0, listOf(line("a$it")), listOf(line("b$it")))),
                    )
                }
            project.historyLog.appendAll(entries.map(HistoryCodec::encode))
            val file = project.folder.resolve("history/book.jsonl")
            val lines = file.readText().lines().toMutableList()
            lines[10] = "not json"
            file.writeText(lines.joinToString("\n"))
            val history = UndoHistory.load(project.historyLog)
            val editor = BookEditor(project::readPage, Dispatchers.Unconfined, history = history)
            var failed: UndoResult? = null
            // The steps that were loaded fit nothing in this book, so they are dropped one by one until older ones are read.
            repeat(400) { if (failed == null) editor.undo().takeIf { it is UndoResult.Failed }?.let { failed = it } }
            assertIs<UndoResult.Failed>(failed)
        }

    @Test
    fun editsInsideOneStepAreNotUnsavedUntilTheStepIsMade() =
        withHistoryProject { project ->
            val editor = BookEditor(project::readPage, Dispatchers.Unconfined)
            runBlocking { editor.show(1) }
            editor.oneStep {
                editor.setLineText("l1", "eins")
                assertEquals(emptyMap(), editor.unsaved(), "the half way layer is not saved without its entry")
                assertEquals(emptyList(), editor.history.pendingEntries)
                editor.splitLine("l1", 2)
            }
            assertEquals(setOf(1), editor.unsaved().keys)
            assertEquals(1, editor.history.pendingEntries.size)
            assertEquals(1, editor.layer?.history)
        }

    @Test
    fun aProjectWhoseHistoryCannotBeRepairedStillOpensWithANotice() =
        withHistoryProject { project ->
            val lines =
                project
                    .readPage(1)
                    .regions
                    .single()
                    .lines
            val changed = lines[0].copy(words = lines[0].words.map { it.copy(text = "neu") })
            project.historyLog.append(
                HistoryCodec.encode(EditEntry(1, listOf(LineChange(1, "r1", 0, listOf(lines[0]), listOf(changed))))),
            )
            Files.writeString(project.folder.resolve("pages/0001.json"), "damaged")
            val model = AppModel()

            model.openProject(project.folder)

            assertNull(model.error)
            assertNotNull(model.project)
            assertTrue(model.notice!!.contains("could not be restored"), model.notice)
            model.closeProject()
        }
}
