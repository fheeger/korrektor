package korrektor

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import korrektor.editor.PageEdits
import korrektor.format.Metadata
import korrektor.format.Project
import korrektor.importer.PdfImport
import korrektor.pdf.TestPage
import korrektor.pdf.TestPdfs
import korrektor.pdf.TestText
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The three ways out of the dialog that appears when closing and saving fails (T062). */
@OptIn(ExperimentalTestApi::class)
class SaveFailedDialogTest {
    private fun withFailingModel(block: (AppModel, Path, () -> Int) -> Unit) {
        val dir = Files.createTempDirectory("savefailed")
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
            var exits = 0
            model.exit = { exits++ }
            model.openProject(project.folder)
            runBlocking { model.editor!!.show(1) }
            model.editor!!.setLineText("l1", "korrigiert")
            assertEquals(false, runBlocking { model.requestClose() })
            assertNotNull(model.closeProblem)
            failing = false
            block(model, dir, { exits })
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    @Test
    fun theDialogSaysWhatWentWrongAndOffersTheThreeWays() =
        withFailingModel { model, _, _ ->
            runComposeUiTest {
                setContent { SaveFailedContent(model, { null }) }
                onNodeWithText("Your changes are not saved").assertExists()
                onNodeWithText("disk full", substring = true).assertExists()
                onNodeWithText("Save to another location…").assertExists()
                onNodeWithText("Close without saving").assertExists()
                onNodeWithText("Keep the app open").assertExists()
            }
        }

    @Test
    fun closeWithoutSavingExitsAndKeepTheAppOpenDoesNot() =
        withFailingModel { model, _, exits ->
            runComposeUiTest {
                setContent { SaveFailedContent(model, { null }) }
                onNodeWithText("Keep the app open").performClick()
                assertNull(model.closeProblem)
                assertEquals(0, exits())
                assertEquals(setOf(1), model.editor!!.changedPages, "the changes are still there")

                model.closeProblem = "again"
                waitForIdle()
                onNodeWithText("Close without saving").performClick()
                assertEquals(1, exits())
                assertNull(model.closeProblem)
            }
        }

    @Test
    fun saveToAnotherLocationAsksForAFolderAndOpensTheCopy() =
        withFailingModel { model, dir, exits ->
            val parent = Files.createDirectory(dir.resolve("elsewhere"))
            runComposeUiTest {
                setContent { SaveFailedContent(model, { parent }) }
                onNodeWithText("Save to another location…").performClick()
                waitUntil(timeoutMillis = 10_000) { model.project?.folder == parent.resolve("b.korrektur") }
                waitForIdle()
                assertNull(model.closeProblem)
                assertEquals(0, exits(), "the app stays open")
                val text = PageEdits.readingOrder(Project.open(parent.resolve("b.korrektur")).readPage(1)).first().text
                assertEquals("korrigiert", text)
                assertTrue(model.notice!!.contains("copy"))
            }
        }

    @Test
    fun cancellingTheFolderChooserChangesNothing() =
        withFailingModel { model, _, exits ->
            runComposeUiTest {
                setContent { SaveFailedContent(model, { null }) }
                onNodeWithText("Save to another location…").performClick()
                waitForIdle()
                assertNotNull(model.closeProblem, "the dialog is still there")
                assertEquals(0, exits())
            }
        }
}
