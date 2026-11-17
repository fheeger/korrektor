package korrektor.editor

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
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
import korrektor.format.Metadata
import korrektor.format.Project
import korrektor.importer.PdfImport
import korrektor.pdf.TestPage
import korrektor.pdf.TestPdfs
import korrektor.pdf.TestText
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/** Ctrl+Z, Ctrl+Y and Ctrl+Shift+Z in the whole window with real key events (T070). */
@OptIn(ExperimentalTestApi::class)
class UndoUiTest {
    /** A book of three pages with two lines each. */
    private fun withOpenProject(block: ComposeUiTest.(AppModel) -> Unit) {
        val dir = Files.createTempDirectory("undoui")
        try {
            val pdf = dir.resolve("book.pdf")
            TestPdfs.write(
                pdf,
                listOf(
                    TestPage(texts = listOf(TestText("Eins", 30f, 250f, 14f), TestText("Zwei", 30f, 220f, 14f))),
                    TestPage(texts = listOf(TestText("Drei", 30f, 250f, 14f), TestText("Vier", 30f, 220f, 14f))),
                    TestPage(texts = listOf(TestText("Fünf", 30f, 250f, 14f), TestText("Sechs", 30f, 220f, 14f))),
                ),
            )
            val project = Project.create(dir.resolve("b.korrektur"), pdf, emptyList(), Metadata("B"))
            PdfImport.run(project)
            val model = AppModel()
            runComposeUiTest {
                setContent { App(model, { project.folder }, { null }, Modifier.size(1100.dp, 800.dp)) }
                onNodeWithText("Open project…").performClick()
                waitUntil(timeoutMillis = 10_000) { exists(field("l2")) }
                waitForIdle()
                block(model)
            }
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    private fun ComposeUiTest.field(id: String): SemanticsNodeInteraction =
        onNode(hasTestTag("line text $id"), useUnmergedTree = true)

    private fun exists(node: SemanticsNodeInteraction) = runCatching { node.assertExists() }.isSuccess

    private fun ComposeUiTest.ctrl(
        key: Key,
        shift: Boolean = false,
    ) {
        onRoot().performKeyInput {
            keyDown(Key.CtrlLeft)
            if (shift) keyDown(Key.ShiftLeft)
            pressKey(key)
            if (shift) keyUp(Key.ShiftLeft)
            keyUp(Key.CtrlLeft)
        }
        waitForIdle()
    }

    private fun ComposeUiTest.waitForPage(
        model: AppModel,
        page: Int,
    ) {
        waitUntil(timeoutMillis = 10_000) { model.editor?.page == page && model.editor?.layer != null }
        waitForIdle()
    }

    @Test
    fun ctrlZUndoesTheTypingInOneStepAndCtrlYAndCtrlShiftZBringItBack() =
        withOpenProject { model ->
            field("l1").performTextReplacement("Eins abc")
            waitForIdle()
            field("l1").assertTextEquals("Eins abc")
            ctrl(Key.Z)
            field("l1").assertTextEquals("Eins")
            field("l1").assertIsFocused()
            ctrl(Key.Y)
            field("l1").assertTextEquals("Eins abc")
            ctrl(Key.Z)
            field("l1").assertTextEquals("Eins")
            ctrl(Key.Z, shift = true)
            field("l1").assertTextEquals("Eins abc")
            field("l1").assertIsFocused()
            // One step: another Ctrl+Z gives the text of the import, a second one has nothing left to take back.
            ctrl(Key.Z)
            ctrl(Key.Z)
            field("l1").assertTextEquals("Eins")
            assertEquals(false, model.editor!!.history.canUndo)
        }

    @Test
    fun undoAfterPageChangeOpensThePageOfTheChangeWithTheCursorThere() =
        withOpenProject { model ->
            field("l2").performTextReplacement("Zwei!")
            waitForIdle()
            ctrl(Key.DirectionDown)
            waitForPage(model, 2)
            ctrl(Key.Z)
            waitForPage(model, 1)
            waitUntil(timeoutMillis = 5_000) { model.editor!!.activeLineId == "l2" }
            assertEquals(1, model.viewer!!.page)
            field("l2").assertTextEquals("Zwei")
            field("l2").assertIsFocused()
            ctrl(Key.Y) // and redo goes there too
            field("l2").assertTextEquals("Zwei!")
        }

    @Test
    fun undoAndRedoOverThreePagesOpenThePageOfEachChangeWithItsText() =
        withOpenProject { model ->
            field("l1").performTextReplacement("Eins!")
            waitForIdle()
            ctrl(Key.DirectionDown)
            waitForPage(model, 2)
            field("l1").performTextReplacement("Drei!")
            waitForIdle()
            ctrl(Key.DirectionDown)
            waitForPage(model, 3)
            field("l1").performTextReplacement("Fünf!")
            waitForIdle()

            // Back: page 3 (where we are), then page 2, then page 1, each with the text it had.
            ctrl(Key.Z)
            field("l1").assertTextEquals("Fünf")
            ctrl(Key.Z)
            waitForPage(model, 2)
            assertEquals(2, model.viewer!!.page)
            field("l1").assertTextEquals("Drei")
            field("l1").assertIsFocused()
            ctrl(Key.Z)
            waitForPage(model, 1)
            assertEquals(1, model.viewer!!.page)
            field("l1").assertTextEquals("Eins")
            assertEquals(false, model.editor!!.history.canUndo)

            // Forward again over the same pages.
            ctrl(Key.Y)
            field("l1").assertTextEquals("Eins!")
            ctrl(Key.Y)
            waitForPage(model, 2)
            field("l1").assertTextEquals("Drei!")
            ctrl(Key.Y)
            waitForPage(model, 3)
            field("l1").assertTextEquals("Fünf!")
            assertEquals(false, model.editor!!.history.canRedo)
        }

    @Test
    fun theKeysAreNotTakenByTheTextFieldsOwnUndo() =
        withOpenProject { model ->
            // Nothing was changed in the book: Ctrl+Z must not undo the field's own typing history either.
            field("l1").performTextReplacement("Eins!")
            waitForIdle()
            runOnIdle { model.editor!!.undo() }
            waitForIdle()
            field("l1").assertTextEquals("Eins")
            ctrl(Key.Z) // nothing to take back: the text stays as the book has it
            field("l1").assertTextEquals("Eins")
        }
}
