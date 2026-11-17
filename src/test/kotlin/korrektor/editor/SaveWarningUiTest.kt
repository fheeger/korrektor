package korrektor.editor

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The warning about a failed save, in the whole window (T060). */
@OptIn(ExperimentalTestApi::class)
class SaveWarningUiTest {
    @Test
    fun aFailedSaveShowsTheWarningKeepsTheChangesAndSavesWhenTheCauseIsGone() {
        val dir = Files.createTempDirectory("savewarning")
        try {
            val pdf = dir.resolve("book.pdf")
            TestPdfs.write(pdf, List(2) { TestPage(texts = listOf(TestText("Erste Zeile", 30f, 250f, 14f))) })
            val project = Project.create(dir.resolve("b.korrektur"), pdf, emptyList(), Metadata("B"))
            PdfImport.run(project)
            var failing = true
            val model =
                AppModel(
                    pageWriter = { opened ->
                        { page, layer ->
                            if (failing) throw IOException("disk full")
                            opened.writePage(page, layer)
                        }
                    },
                )
            runComposeUiTest {
                setContent { App(model, { null }, { null }, Modifier.size(1100.dp, 800.dp)) }
                runOnIdle { model.openProject(project.folder) }
                waitUntil(timeoutMillis = 10_000) { model.editor?.lines?.isNotEmpty() == true }
                onNode(hasTestTag("save warning")).assertDoesNotExist()

                runOnIdle { model.editor!!.setLineText("l1", "korrigiert") }
                mainClock.advanceTimeBy(1_100)
                waitUntil(timeoutMillis = 5_000) { model.saver!!.error != null }
                waitForIdle()
                onNode(hasTestTag("save warning")).assertExists()
                onNodeWithText("disk full", substring = true).assertExists()
                assertEquals(setOf(1), model.editor!!.changedPages, "the correction is kept in memory")
                assertEquals(
                    "korrigiert",
                    model.editor!!
                        .lines
                        .first()
                        .text,
                )

                // Dismissing hides the banner; saving goes on being tried.
                onNodeWithText("Dismiss").performClick()
                onNode(hasTestTag("save warning")).assertDoesNotExist()

                failing = false
                mainClock.advanceTimeBy(5_100)
                waitUntil(timeoutMillis = 5_000) { model.editor!!.changedPages.isEmpty() }
                assertTrue(PageEdits.readingOrder(project.readPage(1)).first().text == "korrigiert")
                onNode(hasTestTag("save warning")).assertDoesNotExist()
            }
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }
}
