package korrektor.editor

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import korrektor.App
import korrektor.AppModel
import korrektor.format.Metadata
import korrektor.format.Project
import korrektor.importer.PdfImport
import korrektor.pdf.TestPage
import korrektor.pdf.TestPdfs
import korrektor.pdf.TestText
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/** The save triggers in the whole window: a pause in typing and a page switch (T059). */
@OptIn(ExperimentalTestApi::class)
class SaveTriggersUiTest {
    private fun withOpenProject(block: ComposeUiTest.(AppModel, Project) -> Unit) {
        val dir = Files.createTempDirectory("savetriggers")
        try {
            val pdf = dir.resolve("book.pdf")
            val texts = listOf(TestText("Erste Zeile", 30f, 250f, 14f), TestText("Zweite", 30f, 220f, 14f))
            TestPdfs.write(pdf, List(3) { TestPage(texts = texts) })
            val project = Project.create(dir.resolve("b.korrektur"), pdf, emptyList(), Metadata("B"))
            PdfImport.run(project)
            val model = AppModel()
            runComposeUiTest {
                setContent { App(model, { null }, { null }, Modifier.size(1100.dp, 800.dp)) }
                runOnIdle { model.openProject(project.folder) }
                waitUntil(timeoutMillis = 10_000) { model.editor?.lines?.isNotEmpty() == true }
                waitForIdle()
                block(model, project)
            }
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    private fun Project.firstText(page: Int) = PageEdits.readingOrder(readPage(page)).first().text

    @Test
    fun aPauseInTypingSavesThePage() =
        withOpenProject { model, project ->
            runOnIdle { model.editor!!.setLineText("l1", "Erste Zeile korrigiert") }
            assertEquals("Erste Zeile", project.firstText(1), "not written at once")
            // The timer runs on its own thread, in real time (`KeepSaving`): the test's clock does not drive it.
            val deadline = System.nanoTime() + 10_000_000_000L
            while (model.editor!!.changedPages.isNotEmpty() && System.nanoTime() < deadline) Thread.sleep(20)
            assertEquals("Erste Zeile korrigiert", project.firstText(1))
            assertEquals(emptySet(), model.editor!!.changedPages)
        }

    @Test
    fun switchingThePageSavesTheOldPageAtOnce() =
        withOpenProject { model, project ->
            runOnIdle { model.editor!!.setLineText("l1", "vor dem Umblättern") }
            runOnIdle { model.viewer!!.goTo(2) }
            // The timer would take a second; the page switch does not wait for it.
            // Real time, as the timer is (`KeepSaving`): the page is on disk well before the second is over.
            val deadline = System.nanoTime() + 800_000_000L
            while (project.firstText(1) != "vor dem Umblättern" && System.nanoTime() < deadline) {
                waitForIdle() // frames: the page change reaches `KeepSaving`
                Thread.sleep(10)
            }
            assertEquals("vor dem Umblättern", project.firstText(1))
            val later = System.nanoTime() + 10_000_000_000L
            while (model.editor!!.changedPages.isNotEmpty() && System.nanoTime() < later) Thread.sleep(10)
            assertEquals(emptySet(), model.editor!!.changedPages)
        }

    @Test
    fun nothingIsWrittenWhileNothingIsChanged() =
        withOpenProject { model, project ->
            val before =
                project.folder
                    .resolve(
                        "pages",
                    ).toFile()
                    .listFiles()!!
                    .associate { it.name to it.lastModified() }
            runOnIdle { model.viewer!!.goTo(2) }
            runOnIdle { model.viewer!!.goTo(3) }
            waitForIdle()
            Thread.sleep(1_500)
            val after =
                project.folder
                    .resolve(
                        "pages",
                    ).toFile()
                    .listFiles()!!
                    .associate { it.name to it.lastModified() }
            assertEquals(before, after, "no page file was touched")
        }
}
