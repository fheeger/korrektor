package korrektor.editor

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/** The save status indicator in the whole window (T061). */
@OptIn(ExperimentalTestApi::class)
class SaveStatusUiTest {
    private fun withProject(
        writer: (Project) -> (Int, korrektor.format.PageLayer) -> Unit,
        block: androidx.compose.ui.test.ComposeUiTest.(AppModel, Project) -> Unit,
    ) {
        val dir = Files.createTempDirectory("savestatus")
        try {
            val pdf = dir.resolve("book.pdf")
            TestPdfs.write(pdf, List(2) { TestPage(texts = listOf(TestText("Erste Zeile", 30f, 250f, 14f))) })
            val project = Project.create(dir.resolve("b.korrektur"), pdf, emptyList(), Metadata("B"))
            PdfImport.run(project)
            val model = AppModel(pageWriter = writer)
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

    private fun androidx.compose.ui.test.ComposeUiTest.status() = onNode(hasTestTag("save status"))

    @Test
    fun aFreshProjectIsSavedThenNotSavedWhileSavingThenSaved() {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        withProject({ opened ->
            { page, layer ->
                entered.complete(Unit)
                runBlocking { release.await() }
                opened.writePage(page, layer)
            }
        }) { model, _ ->
            status().assertTextEquals("Saved")

            runOnIdle { model.editor!!.setLineText("l1", "neu") }
            waitForIdle()
            status().assertTextEquals("Not saved")

            mainClock.advanceTimeBy(1_100)
            waitUntil(timeoutMillis = 5_000) { entered.isCompleted }
            waitForIdle()
            status().assertTextEquals("Saving…")
            assertEquals(SaveStatus.SAVING, model.saver!!.status)

            release.complete(Unit)
            waitUntil(timeoutMillis = 5_000) { model.editor!!.changedPages.isEmpty() && !model.saver!!.saving }
            waitForIdle()
            status().assertTextEquals("Saved")
        }
    }

    @Test
    fun anEditWhileSavingEndsAsNotSavedAndThenSaved() {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var first = true
        withProject({ opened ->
            { page, layer ->
                if (first) {
                    first = false
                    entered.complete(Unit)
                    runBlocking { release.await() }
                }
                opened.writePage(page, layer)
            }
        }) { model, _ ->
            runOnIdle { model.editor!!.setLineText("l1", "a") }
            mainClock.advanceTimeBy(1_100)
            waitUntil(timeoutMillis = 5_000) { entered.isCompleted }
            runOnIdle { model.editor!!.setLineText("l1", "ab") } // typing goes on during the write
            release.complete(Unit)
            waitUntil(timeoutMillis = 5_000) { !model.saver!!.saving }
            waitForIdle()
            status().assertTextEquals("Not saved")
            mainClock.advanceTimeBy(1_100)
            waitUntil(timeoutMillis = 5_000) { model.editor!!.changedPages.isEmpty() && !model.saver!!.saving }
            waitForIdle()
            status().assertTextEquals("Saved")
        }
    }

    @Test
    fun aFailedSaveShowsNotSavedAndTheWarning() =
        withProject({ _ -> { _, _ -> throw IOException("disk full") } }) { model, _ ->
            runOnIdle { model.editor!!.setLineText("l1", "a") }
            mainClock.advanceTimeBy(1_100)
            waitUntil(timeoutMillis = 5_000) { model.saver!!.error != null }
            waitForIdle()
            status().assertTextEquals("Not saved")
            onNode(hasTestTag("save warning")).assertExists()
            assertEquals(SaveStatus.NOT_SAVED, model.saver!!.status)
        }
}
