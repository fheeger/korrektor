package korrektor.history

import korrektor.AppModel
import korrektor.closeProject
import korrektor.correctedPages
import korrektor.editor.SaveOutcome
import korrektor.editor.UndoResult
import korrektor.editor.setStatus
import korrektor.editor.undo
import korrektor.editor.withHistoryProject
import korrektor.format.Metadata
import korrektor.format.PageInfo
import korrektor.format.PageStatus
import korrektor.format.Project
import korrektor.format.setStatuses
import korrektor.progressText
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Page statuses together with the rest (T081): undo with corrections, after reopening, and in a book of 1000 pages. */
class StatusFlowTest {
    private val corrected = PageStatus.CORRECTED

    @Test
    fun statusesAndCorrectionsAreUndoneInOrderAfterSavingAndReopening() =
        withHistoryProject(pages = 6) { project ->
            val imported = (1..6).associateWith { project.readPage(it) }
            val first = AppModel()
            first.openProject(project.folder)
            val editor = checkNotNull(first.editor)
            runBlocking {
                for (page in 1..4) {
                    editor.show(page)
                    editor.setLineText("l1", "Seite $page korrigiert")
                    editor.setStatus(page, corrected)
                }
                editor.setStatus(2, PageStatus.UNCHECKED)
                assertEquals(SaveOutcome.Done, checkNotNull(first.saver).saveNow())
            }
            first.closeProject()

            val second = AppModel()
            second.openProject(project.folder)
            val reopened = checkNotNull(second.editor)
            assertEquals(setOf(1, 3, 4), reopened.statuses.keys)

            // 4 corrections + 5 status changes = 9 steps, each taken back on its own page.
            var steps = 0
            while (true) {
                val result = reopened.undo()
                if (result == UndoResult.NothingToDo) break
                assertIs<UndoResult.Done>(result)
                steps++
            }
            assertEquals(9, steps)
            assertEquals(emptyMap(), reopened.statuses)
            for (page in 1..6) {
                val now = (reopened.changed[page] ?: project.readPage(page)).copy(history = null)
                assertEquals(imported.getValue(page).copy(history = null), now, "page $page")
            }
            assertEquals(SaveOutcome.Done, runBlocking { checkNotNull(second.saver).saveNow() })
            assertEquals(emptyMap(), Project.open(project.folder).statuses)
            second.closeProject()
        }

    @Test
    fun theProgressOfABookOf1000PagesNeedsNoPageFile() {
        val dir = Files.createTempDirectory("status1000")
        try {
            val pdf = dir.resolve("book.pdf").also { it.writeBytes("%PDF-1.4 stand-in".toByteArray()) }
            val project = Project.create(dir.resolve("b.korrektur"), pdf, (1..1000).map { PageInfo(it) }, Metadata("B"))
            project.setStatuses((1..1000 step 2).associateWith { corrected })
            assertTrue(
                project.folder
                    .resolve("pages")
                    .toFile()
                    .list()
                    .isNullOrEmpty(),
                "there is no page file",
            )

            val start = System.nanoTime()
            val model = AppModel()
            model.openProject(project.folder)
            val editor = checkNotNull(model.editor)
            val text = progressText(editor.correctedPages(1000), 1000)
            val millis = (System.nanoTime() - start) / 1_000_000

            assertNull(model.error)
            assertEquals("500 of 1000 corrected (50 %)", text)
            println("1000 pages without page files: opened and counted in $millis ms")
            assertTrue(millis < 5_000, "$millis ms")
            assertTrue(!project.folder.resolve("history").exists(), "opening writes no history")
            model.closeProject()
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
