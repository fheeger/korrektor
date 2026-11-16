package korrektor.editor

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
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

/** Ctrl+Down/Up and Ctrl+G with the page field, in the whole window with real key events (T051). */
@OptIn(ExperimentalTestApi::class)
class PageShortcutsUiTest {
    /** A book of three pages: two lines, two lines, no text. */
    private fun withOpenProject(block: ComposeUiTest.(AppModel) -> Unit) {
        val dir = Files.createTempDirectory("pageshort")
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

    private fun ComposeUiTest.ctrl(key: Key) {
        onRoot().performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(key)
            keyUp(Key.CtrlLeft)
        }
        waitForIdle()
    }

    private fun ComposeUiTest.pageField() = onNodeWithContentDescription("Go to page")

    private fun ComposeUiTest.startOnFirstLine(model: AppModel) {
        runOnIdle { model.editor!!.requestFocus("l1", 0) }
        waitForIdle()
    }

    @Test
    fun ctrlDownAndCtrlUpChangeThePageAndPutTheCursorOnTheFirstLine() =
        withOpenProject { model ->
            runOnIdle { model.editor!!.requestFocus("l2", 2) }
            waitForIdle()
            ctrl(Key.DirectionDown)
            waitForPage(model, 2)
            waitUntil(timeoutMillis = 5_000) { model.editor!!.activeLineId == "l1" }
            field("l1").assertIsFocused()
            assertEquals(
                "Drei",
                model.editor!!
                    .lines
                    .first()
                    .text,
            )
            runOnIdle { model.editor!!.requestFocus("l2", 0) }
            waitForIdle()
            ctrl(Key.DirectionUp)
            waitForPage(model, 1)
            waitUntil(timeoutMillis = 5_000) { model.editor!!.activeLineId == "l1" }
            field("l1").assertIsFocused()
            assertEquals(1, model.viewer!!.page)
        }

    @Test
    fun onTheEndsOfTheBookNothingHappens() =
        withOpenProject { model ->
            startOnFirstLine(model)
            ctrl(Key.DirectionUp)
            assertEquals(1, model.viewer!!.page)
            field("l1").assertIsFocused()
            runOnIdle { model.viewer!!.goTo(3) }
            waitForPage(model, 3)
            ctrl(Key.DirectionDown)
            assertEquals(3, model.viewer!!.page)
        }

    @Test
    fun ctrlUpLeavesAPageWithoutLines() =
        withOpenProject { model ->
            runOnIdle { model.viewer!!.goTo(3) }
            waitForPage(model, 3)
            ctrl(Key.DirectionUp)
            waitForPage(model, 2)
            waitUntil(timeoutMillis = 5_000) { model.editor!!.activeLineId == "l1" }
            field("l1").assertIsFocused()
        }

    @Test
    fun ctrlGFocusesThePageFieldAndSelectsItsContent() =
        withOpenProject { model ->
            startOnFirstLine(model)
            ctrl(Key.G)
            pageField().assertIsFocused()
            val selection = pageField().fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]
            assertEquals(0, selection.start)
            assertEquals(1, selection.end)
            assertEquals(1, model.viewer!!.page)
        }

    @Test
    fun enterInThePageFieldOpensThePageWithTheCursorOnItsFirstLine() =
        withOpenProject { model ->
            startOnFirstLine(model)
            ctrl(Key.G)
            pageField().performTextReplacement("2")
            key(Key.Enter)
            waitForPage(model, 2)
            waitUntil(timeoutMillis = 5_000) { model.editor!!.activeLineId == "l1" }
            field("l1").assertIsFocused()
            assertEquals(2, model.viewer!!.page)
            pageField().assertTextEquals("2")
        }

    @Test
    fun aWrongNumberIsUndoneAndTheFieldKeepsTheFocus() =
        withOpenProject { model ->
            startOnFirstLine(model)
            ctrl(Key.G)
            for (wrong in listOf("99", "0", "abc", "")) {
                pageField().performTextReplacement(wrong)
                key(Key.Enter)
                pageField().assertTextEquals("1")
                pageField().assertIsFocused()
                assertEquals(1, model.viewer!!.page, "'$wrong'")
            }
        }

    @Test
    fun enterWithTheCurrentPageNumberPutsTheCursorOnTheFirstLine() =
        withOpenProject { model ->
            runOnIdle { model.editor!!.requestFocus("l2", 0) }
            waitForIdle()
            ctrl(Key.G)
            key(Key.Enter)
            field("l1").assertIsFocused()
            assertEquals(1, model.viewer!!.page)
        }

    @Test
    fun escapeLeavesThePageFieldForTheTextWithoutChangingThePage() =
        withOpenProject { model ->
            runOnIdle { model.editor!!.requestFocus("l2", 0) }
            waitForIdle()
            ctrl(Key.G)
            pageField().performTextReplacement("2")
            key(Key.Escape)
            field("l2").assertIsFocused()
            assertEquals(1, model.viewer!!.page)
            pageField().assertTextEquals("1")
        }

    @Test
    fun thePageShortcutsWorkFromThePageFieldToo() =
        withOpenProject { model ->
            startOnFirstLine(model)
            ctrl(Key.G)
            ctrl(Key.DirectionDown)
            waitForPage(model, 2)
            waitUntil(timeoutMillis = 5_000) { model.editor!!.activeLineId == "l1" }
            field("l1").assertIsFocused()
        }
}
