package korrektor.editor

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
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
import kotlin.test.assertNull

/** Up and Down between lines and pages, in the whole window with real key events (T050). */
@OptIn(ExperimentalTestApi::class)
class LineNavigationUiTest {
    /** A book of three pages: two lines, two lines, no text. */
    private fun withOpenProject(block: ComposeUiTest.(AppModel) -> Unit) {
        val dir = Files.createTempDirectory("linenav")
        try {
            val pdf = dir.resolve("book.pdf")
            TestPdfs.write(
                pdf,
                listOf(
                    TestPage(texts = listOf(TestText("Eins", 30f, 250f, 14f), TestText("Zwei", 30f, 220f, 14f))),
                    TestPage(texts = listOf(TestText("Drei", 30f, 250f, 14f), TestText("Vier", 30f, 220f, 14f))),
                    TestPage(),
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

    private fun ComposeUiTest.key(key: Key) {
        onRoot().performKeyInput { pressKey(key) }
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
    fun downAndUpMoveTheCursorAndTheHighlight() =
        withOpenProject { model ->
            val editor = model.editor!!
            runOnIdle { editor.requestFocus("l1", 0) }
            waitForIdle()
            field("l1").assertIsFocused()
            key(Key.DirectionDown)
            field("l2").assertIsFocused()
            assertEquals("l2", editor.activeLineId)
            onNodeWithContentDescription("Highlighted line: l2").assertExists()
            key(Key.DirectionUp)
            field("l1").assertIsFocused()
            onNodeWithContentDescription("Highlighted line: l1").assertExists()
        }

    @Test
    fun ctrlHomeAndCtrlEndGoToTheFirstAndLastLine() =
        withOpenProject { model ->
            runOnIdle { model.editor!!.requestFocus("l1", 0) }
            waitForIdle()
            onRoot().performKeyInput {
                keyDown(Key.CtrlLeft)
                pressKey(Key.MoveEnd)
                keyUp(Key.CtrlLeft)
            }
            waitForIdle()
            field("l2").assertIsFocused()
            onRoot().performKeyInput {
                keyDown(Key.CtrlLeft)
                pressKey(Key.MoveHome)
                keyUp(Key.CtrlLeft)
            }
            waitForIdle()
            field("l1").assertIsFocused()
        }

    @Test
    fun theColumnIsKeptWhenMovingBetweenLines() =
        withOpenProject { model ->
            runOnIdle { model.editor!!.requestFocus("l1", 3) }
            waitForIdle()
            key(Key.DirectionDown)
            assertEquals(3, model.editor!!.caret)
            assertEquals(
                3,
                model.editor!!
                    .focusRequest
                    ?.focus
                    ?.index,
            )
        }

    @Test
    fun downOnTheLastLineOpensTheNextPageOnItsFirstLine() =
        withOpenProject { model ->
            runOnIdle { model.editor!!.requestFocus("l2", 0) }
            waitForIdle()
            key(Key.DirectionDown)
            waitForPage(model, 2)
            assertEquals(2, model.viewer!!.page)
            waitUntil(timeoutMillis = 5_000) { model.editor!!.activeLineId == "l1" }
            field("l1").assertIsFocused()
            assertEquals(
                "Drei",
                model.editor!!
                    .lines
                    .first()
                    .text,
            )
            // The scan draws the box a moment after the cursor has arrived (it needs the new page's size first).
            waitUntil(timeoutMillis = 5_000) {
                runCatching { onNodeWithContentDescription("Highlighted line: l1").assertExists() }.isSuccess
            }
        }

    @Test
    fun upOnTheFirstLineOpensThePreviousPageOnItsLastLine() =
        withOpenProject { model ->
            runOnIdle { model.viewer!!.goTo(2) }
            waitForPage(model, 2)
            runOnIdle { model.editor!!.requestFocus("l1", 0) }
            waitForIdle()
            key(Key.DirectionUp)
            waitForPage(model, 1)
            waitUntil(timeoutMillis = 5_000) { model.editor!!.activeLineId == "l2" }
            field("l2").assertIsFocused()
            assertEquals(1, model.viewer!!.page)
        }

    @Test
    fun theEndsOfTheBookStop() =
        withOpenProject { model ->
            runOnIdle { model.editor!!.requestFocus("l1", 0) }
            waitForIdle()
            key(Key.DirectionUp)
            assertEquals(1, model.viewer!!.page)
            assertEquals("l1", model.editor!!.activeLineId)
            field("l1").assertIsFocused()
        }

    @Test
    fun aPageWithoutLinesKeepsTheKeyboardWorking() =
        withOpenProject { model ->
            runOnIdle { model.viewer!!.goTo(3) }
            waitForPage(model, 3)
            onNode(hasTestTag("text list")).assertIsFocused()
            key(Key.DirectionDown)
            key(Key.DirectionUp)
            assertEquals(3, model.viewer!!.page)
            assertNull(model.editor!!.activeLineId)
            // The page before can still be reached from here once T051 has its shortcut; now by a click.
            onNodeWithContentDescription("Previous page").performClick()
            waitForPage(model, 2)
            runOnIdle { model.editor!!.requestFocus("l1", 0) }
            waitForIdle()
            key(Key.DirectionDown)
            field("l2").assertIsFocused()
        }
}
