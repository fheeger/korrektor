package korrektor.export

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import korrektor.App
import korrektor.AppModel
import korrektor.ExportPicker
import korrektor.editor.setStatus
import korrektor.format.Metadata
import korrektor.format.PageStatus
import korrektor.format.Project
import korrektor.importer.PdfImport
import korrektor.pdf.TestPage
import korrektor.pdf.TestPdfs
import korrektor.pdf.TestText
import korrektor.settings.exportOptions
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The export dialog in the whole window: Ctrl+E, the menu entry's button, the choices, the file, the result. */
@OptIn(ExperimentalTestApi::class)
class ExportDialogUiTest {
    private class FakePicker(
        private val answer: () -> Path?,
    ) : ExportPicker {
        val asked = ArrayList<Pair<String, ExportFormat>>()

        override fun pick(
            suggestedName: String,
            format: ExportFormat,
        ): Path? {
            asked += suggestedName to format
            return answer()
        }
    }

    /** A book of three pages with two lines each: `Eins n` and `Zwei`. */
    private fun withOpenProject(
        picker: (Path) -> FakePicker,
        block: ComposeUiTest.(AppModel, FakePicker, Path) -> Unit,
    ) {
        val dir = Files.createTempDirectory("exportui")
        try {
            val pdf = dir.resolve("book.pdf")
            TestPdfs.write(
                pdf,
                List(3) { n ->
                    TestPage(
                        texts = listOf(TestText("Eins ${n + 1}", 30f, 250f, 14f), TestText("Zwei", 30f, 220f, 14f)),
                    )
                },
            )
            val project = Project.create(dir.resolve("b.korrektur"), pdf, emptyList(), Metadata("Mein Buch", "Ewald"))
            PdfImport.run(project)
            val model = AppModel()
            val fake = picker(dir)
            runComposeUiTest {
                model.exportPicker = fake
                setContent { App(model, { project.folder }, { null }, Modifier.size(1100.dp, 800.dp)) }
                onNodeWithText("Open project…").performClick()
                waitUntil(timeoutMillis = 10_000) { exists(node("line text l2")) }
                waitForIdle()
                block(model, fake, dir)
            }
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    private fun ComposeUiTest.node(tag: String): SemanticsNodeInteraction =
        onNode(hasTestTag(tag), useUnmergedTree = true)

    private fun exists(node: SemanticsNodeInteraction) = runCatching { node.assertExists() }.isSuccess

    private fun ComposeUiTest.ctrlE() {
        onRoot().performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.E)
            keyUp(Key.CtrlLeft)
        }
        waitForIdle()
    }

    @Test
    fun ctrlEAndTheButtonOpenTheDialogWithTheDefaultsOfPlainText() =
        withOpenProject({ FakePicker { null } }) { _, _, _ ->
            ctrlE()
            node("export dialog").assertExists()
            node("export option Page markers").assertIsOn()
            node("export option Header").assertIsOn()
            node("export option Keep the printed line breaks").assertIsOn()
            node("export from").assertTextContains("1")
            node("export to").assertTextContains("3")
            node("export cancel").performClick()
            waitForIdle()
            node("export dialog").assertDoesNotExist()
            onNodeWithText("Export…").performClick()
            waitForIdle()
            node("export dialog").assertExists()
        }

    @Test
    fun markdownStartsWithAllOptionsOffAndEachFormatKeepsItsChoices() =
        withOpenProject({ FakePicker { null } }) { _, _, _ ->
            ctrlE()
            node("export format MARKDOWN").performClick()
            waitForIdle()
            node("export option Page markers").assertIsOff()
            node("export option Header").assertIsOff()
            node("export option Keep the printed line breaks").assertIsOff()
            node("export option Header").performClick()
            node("export format TEXT").performClick()
            waitForIdle()
            node("export option Header").assertIsOn()
            node("export format MARKDOWN").performClick()
            waitForIdle()
            node("export option Header").assertIsOn()
            node("export option Page markers").assertIsOff()
        }

    @Test
    fun aWrongRangeDisablesExportAndSaysWhy() =
        withOpenProject({ FakePicker { null } }) { _, _, _ ->
            ctrlE()
            node("export start").assertIsEnabled()
            node("export to").performTextReplacement("9")
            waitForIdle()
            node("export start").assertIsNotEnabled()
            node("export range problem").assertTextContains("Enter page numbers from 1 to 3.", substring = true)
            node("export to").performTextReplacement("3")
            node("export from").performTextReplacement("4")
            waitForIdle()
            node("export range problem").assertTextContains("Enter page numbers from 1 to 3.", substring = true)
            node("export from").performTextReplacement("3")
            node("export to").performTextReplacement("2")
            waitForIdle()
            node(
                "export range problem",
            ).assertTextContains("The first page must not come after the last.", substring = true)
        }

    @Test
    fun theNoteCountsTheUncheckedPagesInTheRangeAndFollowsTheStatuses() =
        withOpenProject({ FakePicker { null } }) { model, _, _ ->
            ctrlE()
            node("export note").assertTextContains("3 of 3 pages are still unchecked", substring = true)
            node("export cancel").performClick()
            waitForIdle()
            checkNotNull(model.editor).setStatus(1, PageStatus.CORRECTED)
            ctrlE()
            node("export note").assertTextContains("2 of 3 pages are still unchecked", substring = true)
            node("export from").performTextReplacement("1")
            node("export to").performTextReplacement("1")
            waitForIdle()
            node("export note").assertDoesNotExist()
        }

    @Test
    fun exportingWritesTheFileClosesTheDialogAndSaysWhereItWent() =
        withOpenProject({ dir -> FakePicker { dir.resolve("mein.txt") } }) { model, picker, dir ->
            ctrlE()
            node("export to").performTextReplacement("2")
            node("export option Page markers").performClick()
            waitForIdle()
            node("export start").performClick()
            waitUntil(timeoutMillis = 10_000) { model.notice != null }
            waitForIdle()
            assertEquals(listOf("Mein Buch.txt" to ExportFormat.TEXT), picker.asked)
            val text = dir.resolve("mein.txt").readText()
            assertEquals("Mein Buch\nAuthor: Ewald\n\nEins 1\nZwei\n\nEins 2\nZwei\n", text)
            assertEquals("Exported 2 pages to ${dir.resolve("mein.txt")}", model.notice)
            node("export dialog").assertDoesNotExist()
            val remembered = model.settings.exportOptions(ExportFormat.TEXT)
            assertEquals(
                ExportOptions(ExportFormat.TEXT, pageMarkers = false, header = true, keepLineBreaks = true),
                remembered,
            )
        }

    @Test
    fun aCancelledSaveDialogChangesNothing() =
        withOpenProject({ FakePicker { null } }) { model, picker, _ ->
            ctrlE()
            node("export start").performClick()
            waitForIdle()
            assertEquals(1, picker.asked.size)
            node("export dialog").assertExists()
            assertNull(model.notice)
        }

    @Test
    fun aFailureStaysInTheDialog() =
        withOpenProject({ dir -> FakePicker { dir.resolve("no folder").resolve("out.txt") } }) { model, _, dir ->
            ctrlE()
            node("export start").performClick()
            waitUntil(timeoutMillis = 10_000) { exists(node("export error")) }
            node("export error").assertTextContains("The file could not be written", substring = true)
            node("export dialog").assertExists()
            assertNull(model.notice)
            assertTrue(!dir.resolve("no folder").exists())
            assertNotNull(model.project)
        }
}
