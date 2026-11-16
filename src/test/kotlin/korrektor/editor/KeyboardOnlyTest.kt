package korrektor.editor

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
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
import korrektor.viewer.ZoomMode
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Several pages corrected with the keyboard alone (S06): nothing is clicked, the project is opened through the model,
 * and the text is typed through the input method (as a keyboard does). The same flow runs on a generated book in CI and
 * on the sample (when it is there). After each step the corrected layer, the active line and the page are checked.
 */
@OptIn(ExperimentalTestApi::class)
class KeyboardOnlyTest {
    private val sample = Path.of("samples", "Abhandlung_über_den_kleinen_Krieg.pdf")

    private fun withProject(
        pdf: Path,
        block: ComposeUiTest.(AppModel, Project) -> Unit,
    ) {
        val dir = Files.createTempDirectory("keyboardonly")
        try {
            val project = Project.create(dir.resolve("b.korrektur"), pdf, emptyList(), Metadata("B"))
            PdfImport.run(project)
            val model = AppModel()
            runComposeUiTest {
                setContent { App(model, { null }, { null }, Modifier.size(1300.dp, 800.dp)) }
                runOnIdle { model.openProject(project.folder) }
                waitUntil(timeoutMillis = 20_000) {
                    model.editor?.layer != null &&
                        model.editor?.lines?.isNotEmpty() == true
                }
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

    private fun ComposeUiTest.field(id: String): SemanticsNodeInteraction =
        onNode(hasTestTag("line text $id"), useUnmergedTree = true)

    private fun ComposeUiTest.key(
        key: Key,
        ctrl: Boolean = false,
    ) {
        onRoot().performKeyInput {
            if (ctrl) keyDown(Key.CtrlLeft)
            pressKey(key)
            if (ctrl) keyUp(Key.CtrlLeft)
        }
        waitForIdle()
    }

    /** The page is shown with its lines, and the cursor is where [line] says (the first or last line of the page). */
    private fun ComposeUiTest.waitForArrival(
        model: AppModel,
        page: Int,
        last: Boolean = false,
    ) {
        val editor = model.editor!!
        try {
            waitUntil(timeoutMillis = 20_000) {
                val lines = editor.lines
                editor.page == page &&
                    lines.isNotEmpty() &&
                    editor.activeLineId == (if (last) lines.last() else lines.first()).id
            }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            // Say where the cursor was left, which tells more than the timeout.
            throw IllegalStateException(
                "page $page not reached: the editor is on page ${editor.page} with ${editor.lines.size} lines, " +
                    "active ${editor.activeLineId}, the viewer on page ${model.viewer!!.page}, error ${editor.error}",
                e,
            )
        }
        waitForIdle()
    }

    /** Ctrl+G, the page number, Enter: the page is open and the cursor is in its first line. */
    private fun ComposeUiTest.goToPage(
        model: AppModel,
        page: Int,
    ) {
        key(Key.G, ctrl = true)
        onNodeWithContentDescription("Go to page").performTextInput(page.toString())
        key(Key.Enter)
        waitForArrival(model, page)
        assertEquals(page, model.viewer!!.page)
        field(
            model.editor!!
                .lines
                .first()
                .id,
        )
    }

    private fun ComposeUiTest.correctPage(
        model: AppModel,
        page: Int,
    ) {
        val editor = model.editor!!
        goToPage(model, page)
        val original = editor.lines.map { it.text }
        assertTrue(original.size >= 3, "page $page has ${original.size} lines")
        assertEquals(editor.lines.first().id, editor.activeLineId)

        // Type into the first line.
        field(editor.lines[0].id).performTextInput("Z")
        waitForIdle()
        assertEquals("Z" + original[0], editor.lines[0].text, "typed in the first line")

        // Down: the second line. Split it three characters in with Enter, join it again with Backspace.
        key(Key.DirectionDown)
        assertEquals(editor.lines[1].id, editor.activeLineId)
        repeat(3) { key(Key.DirectionRight) }
        key(Key.Enter)
        assertEquals(original.size + 1, editor.lines.size, "split")
        assertEquals(editor.lines[2].id, editor.activeLineId, "the cursor is in the second half")
        key(Key.Backspace)
        assertEquals(original.size, editor.lines.size, "joined again")
        assertEquals(original[1].replace(" ", ""), editor.lines[1].text.replace(" ", ""))
        assertEquals(editor.lines[1].id, editor.activeLineId)

        // Enter at the end adds an empty line; Backspace on it deletes it.
        key(Key.MoveEnd)
        key(Key.Enter)
        assertEquals(original.size + 1, editor.lines.size, "line added")
        assertEquals("", editor.lines[2].text)
        assertEquals(editor.lines[2].id, editor.activeLineId)
        key(Key.Backspace)
        assertEquals(original.size, editor.lines.size, "empty line deleted")
        assertEquals(editor.lines[1].id, editor.activeLineId)

        // Delete at the end joins the next line into this one.
        key(Key.MoveEnd)
        key(Key.Delete)
        assertEquals(original.size - 1, editor.lines.size, "joined with the next line")
        assertEquals((original[1] + original[2]).replace(" ", ""), editor.lines[1].text.replace(" ", ""))

        // The last line, then Down: the first line of the next page; Ctrl+Up: back, on the first line.
        key(Key.MoveEnd, ctrl = true)
        assertEquals(editor.lines.last().id, editor.activeLineId)
        key(Key.DirectionDown)
        waitForArrival(model, page + 1)
        assertEquals(page + 1, model.viewer!!.page)
        key(Key.DirectionUp, ctrl = true)
        waitForArrival(model, page)
        assertEquals(page, model.viewer!!.page)
        assertEquals("Z" + original[0], editor.lines[0].text, "the correction is still there")
        assertTrue(page in editor.changedPages)
    }

    private fun ComposeUiTest.zoomWithTheKeyboard(model: AppModel) {
        val viewer = model.viewer!!
        key(Key.One, ctrl = true)
        val fitted = viewer.scale
        key(Key.Plus, ctrl = true)
        key(Key.Plus, ctrl = true)
        assertTrue(viewer.scale > fitted)
        key(Key.Minus, ctrl = true)
        key(Key.Zero, ctrl = true)
        assertEquals(ZoomMode.FIT_PAGE, viewer.mode)
        key(Key.One, ctrl = true)
        assertEquals(ZoomMode.FIT_WIDTH, viewer.mode)
    }

    @Test
    fun fivePagesOfAGeneratedBookAreCorrectedWithTheKeyboardAlone() {
        val dir = Files.createTempDirectory("keyboardbook")
        try {
            val pdf = dir.resolve("book.pdf")
            val lines =
                listOf("Erste Zeile des Buches", "zweite Zeile mit Text", "dritte Zeile hier", "vierte und letzte")
            val page = TestPage(texts = lines.mapIndexed { i, t -> TestText(t, 30f, 250f - 30f * i, 14f) })
            TestPdfs.write(pdf, List(8) { page })
            withProject(pdf) { model, _ ->
                for (n in 2..6) correctPage(model, n)
                zoomWithTheKeyboard(model)
                assertEquals(setOf(2, 3, 4, 5, 6), model.editor!!.changedPages)
            }
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    @Test
    fun fivePagesOfTheSampleAreCorrectedWithTheKeyboardAlone() {
        assumeTrue(sample.exists(), "the sample PDF is not available")
        withProject(sample) { model, project ->
            // Pages that have at least three lines and are followed by a page with lines: the sample has blank pages
            // (3, for one), and Down from the last line of a page into a page without lines is a stop (T050).
            fun lines(page: Int) = PageEdits.readingOrder(project.readPage(page)).size
            val pages =
                listOf(2, 6, 40, 100, 150).map { start ->
                    (start..160).first {
                        lines(it) >= 3 &&
                            lines(it + 1) > 0
                    }
                }
            assertEquals(5, pages.distinct().size, "five different pages: $pages")
            for (n in pages) correctPage(model, n)
            zoomWithTheKeyboard(model)
            assertEquals(pages.toSet(), model.editor!!.changedPages)
        }
    }

    @Test
    fun thisTestDoesNotUseTheMouse() {
        val source = Path.of("src", "test", "kotlin", "korrektor", "editor", "KeyboardOnlyTest.kt").readText()
        // The names are put together, so that this check does not find itself.
        for (call in listOf("perform" + "Click", "perform" + "MouseInput", "perform" + "TouchInput")) {
            assertFalse(source.contains(call), "$call is a mouse or touch event")
        }
    }
}
