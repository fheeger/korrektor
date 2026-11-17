package korrektor.autosave

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import korrektor.App
import korrektor.AppModel
import korrektor.closeProject
import korrektor.editor.PageEdits
import korrektor.editor.SaveOutcome
import korrektor.format.Metadata
import korrektor.format.Project
import korrektor.importer.PdfImport
import korrektor.pdf.TestPage
import korrektor.pdf.TestPdfs
import korrektor.pdf.TestText
import korrektor.requestClose
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readBytes
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Whole flows of S07: a failing disk that comes back, and a session from typing to closing and reopening (T065). */
@OptIn(ExperimentalTestApi::class)
class AutosaveFlowTest {
    private fun withProject(block: (Path, Path) -> Unit) {
        val dir = Files.createTempDirectory("autosaveflow")
        try {
            val pdf = dir.resolve("book.pdf")
            val texts = listOf(TestText("Erste Zeile", 30f, 250f, 14f), TestText("Zweite Zeile", 30f, 220f, 14f))
            TestPdfs.write(pdf, List(3) { TestPage(texts = texts) })
            val project = Project.create(dir.resolve("b.korrektur"), pdf, emptyList(), Metadata("B"))
            PdfImport.run(project)
            block(dir, project.folder)
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    private fun Project.text(
        page: Int,
        line: Int = 0,
    ) = PageEdits.readingOrder(readPage(page))[line].text

    @Test
    fun aFolderThatGoesAwayAndComesBackLosesNothing() =
        withProject { dir, folder ->
            val exits = intArrayOf(0)
            val model = AppModel()
            model.exit = { exits[0]++ }
            model.openProject(folder)
            runBlocking { model.editor!!.show(1) }
            model.editor!!.setLineText("l1", "gerettet")
            val pages = folder.resolve("pages")
            val away = dir.resolve("pages-away")

            Files.move(pages, away) // the folder goes away, as if the disk were removed
            val outcome = runBlocking { model.saver!!.saveNow() }
            assertIs<SaveOutcome.Failed>(outcome)
            assertNotNull(model.saver!!.warning, "the warning is shown")
            assertEquals(setOf(1), model.editor!!.changedPages, "kept in memory")

            // Closing now is not possible: the dialog is offered instead.
            assertEquals(false, runBlocking { model.requestClose() })
            assertEquals(0, exits[0])
            assertNotNull(model.closeProblem)

            Files.move(away, pages) // it comes back
            assertEquals(SaveOutcome.Done, runBlocking { model.saver!!.saveNow() })
            assertNull(model.saver!!.warning)
            assertEquals("gerettet", Project.open(folder).text(1))

            model.closeProblem = null
            assertTrue(runBlocking { model.requestClose() })
            assertEquals(1, exits[0])
        }

    private fun originals(folder: Path) =
        folder
            .walk()
            .filter { Files.isRegularFile(it) }
            .filter { it.fileName.toString() != ".lock" }
            .filter { !Regex("""\d+\.json(\.tmp)?""").matches(it.fileName.toString()) } // the corrected layers
            .associate { folder.relativize(it).toString() to it.readBytes() }

    @Test
    fun aSessionFromTypingToClosingKeepsTheCorrectionsAndNeverTouchesTheOriginals() =
        withProject { _, folder ->
            val before = originals(folder)
            val model = AppModel()
            var exits = 0
            model.exit = { exits++ }
            runComposeUiTest {
                setContent { App(model, { null }, { null }, Modifier.size(1100.dp, 800.dp)) }
                runOnIdle { model.openProject(folder) }
                waitUntil(timeoutMillis = 10_000) { model.editor?.lines?.isNotEmpty() == true }

                // Type on page 1, go to page 2, type there, then close.
                onNode(hasTestTag("line text l1"), useUnmergedTree = true).performTextInput("Z")
                waitForIdle()
                runOnIdle { model.viewer!!.goTo(2) }
                waitUntil(timeoutMillis = 10_000) { model.editor!!.page == 2 && model.editor!!.lines.isNotEmpty() }
                onNode(hasTestTag("line text l2"), useUnmergedTree = true).performTextInput("Y")
                waitForIdle()
                val closing = runOnIdle { runBlocking { model.requestClose() } }
                assertTrue(closing)
            }
            assertEquals(1, exits)
            model.closeProject()

            // Reopened: the corrections are there, the originals are as they were.
            val reopened = AppModel()
            reopened.openProject(folder)
            val project = reopened.project!!
            assertTrue(project.text(1, 0).startsWith("Z") || project.text(1, 0).contains("Z"), project.text(1, 0))
            assertTrue(project.text(2, 1).contains("Y"), project.text(2, 1))
            assertEquals(
                "Erste Zeile",
                project
                    .readOriginalPage(1)
                    .regions
                    .first()
                    .lines
                    .first()
                    .text,
            )
            val after = originals(folder)
            assertEquals(before.keys, after.keys)
            for ((name, bytes) in before) assertContentEquals(bytes, after.getValue(name), name)
        }
}
