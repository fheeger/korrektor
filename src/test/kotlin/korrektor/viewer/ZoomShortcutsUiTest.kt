package korrektor.viewer

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
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
import kotlin.test.assertTrue

/** The zoom shortcuts, in the whole window with real key events (T053). */
@OptIn(ExperimentalTestApi::class)
class ZoomShortcutsUiTest {
    /** A book of three pages: two lines, two lines, no text. */
    private fun withOpenProject(block: ComposeUiTest.(AppModel) -> Unit) {
        val dir = Files.createTempDirectory("zoomkeys")
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

    private fun ComposeUiTest.startInLine(model: AppModel) {
        runOnIdle { model.editor!!.requestFocus("l1", 0) }
        waitForIdle()
    }

    private fun ComposeUiTest.fitWidthAgain(model: AppModel) {
        ctrl(Key.One)
        assertEquals(ZoomMode.FIT_WIDTH, model.viewer!!.mode)
    }

    @Test
    fun ctrlPlusAndCtrlMinusDoWhatTheButtonsDo() =
        withOpenProject { model ->
            val viewer = model.viewer!!
            startInLine(model)
            val start = viewer.scale
            ctrl(Key.Plus)
            val zoomedIn = viewer.scale
            assertTrue(zoomedIn > start, "zoomed in")
            assertEquals(ZoomMode.FIXED, viewer.mode)
            ctrl(Key.Minus)
            assertTrue(viewer.scale < zoomedIn, "zoomed out")
            // The same steps as the buttons.
            fitWidthAgain(model)
            onNodeWithContentDescription("Zoom in").performClick()
            waitForIdle()
            assertEquals(zoomedIn, viewer.scale)
            ctrl(Key.Minus)
            val byKey = viewer.scale
            fitWidthAgain(model)
            onNodeWithContentDescription("Zoom in").performClick()
            onNodeWithContentDescription("Zoom out").performClick()
            waitForIdle()
            assertEquals(byKey, viewer.scale)
        }

    @Test
    fun theOtherPlusAndMinusKeysAlsoZoom() =
        withOpenProject { model ->
            val viewer = model.viewer!!
            startInLine(model)
            val start = viewer.scale
            ctrl(Key.Plus)
            val step = viewer.scale
            for ((key, shift) in listOf(Key.Equals to false, Key.Equals to true, Key.NumPadAdd to false)) {
                fitWidthAgain(model)
                ctrl(key, shift)
                assertEquals(step, viewer.scale, "$key shift=$shift")
            }
            ctrl(Key.NumPadSubtract)
            assertTrue(viewer.scale < step, "NumPad minus zooms out")
            assertTrue(start > 0)
        }

    @Test
    fun ctrlZeroFitsThePageAndCtrlOneFitsTheWidth() =
        withOpenProject { model ->
            val viewer = model.viewer!!
            startInLine(model)
            ctrl(Key.Zero)
            assertEquals(ZoomMode.FIT_PAGE, viewer.mode)
            ctrl(Key.Plus)
            assertEquals(ZoomMode.FIXED, viewer.mode)
            ctrl(Key.One)
            assertEquals(ZoomMode.FIT_WIDTH, viewer.mode)
            ctrl(Key.NumPad0)
            assertEquals(ZoomMode.FIT_PAGE, viewer.mode)
            ctrl(Key.NumPad1)
            assertEquals(ZoomMode.FIT_WIDTH, viewer.mode)
        }

    @Test
    fun noCharacterIsTypedIntoTheLine() =
        withOpenProject { model ->
            val editor = model.editor!!
            startInLine(model)
            val before = editor.lines.map { it.text }
            for (key in listOf(Key.Plus, Key.Minus, Key.Equals, Key.Zero, Key.One, Key.NumPadAdd)) ctrl(key)
            assertEquals(before, editor.lines.map { it.text })
            assertEquals(emptySet(), editor.changedPages)
        }

    @Test
    fun atTheLimitsNothingHappensAndNothingThrows() =
        withOpenProject { model ->
            val viewer = model.viewer!!
            startInLine(model)
            repeat(40) { ctrl(Key.Plus) }
            val most = viewer.scale
            ctrl(Key.Plus)
            assertEquals(most, viewer.scale)
            repeat(60) { ctrl(Key.Minus) }
            val least = viewer.scale
            ctrl(Key.Minus)
            assertEquals(least, viewer.scale)
            assertTrue(least < most)
        }

    @Test
    fun zoomingFromThePageFieldWorksToo() =
        withOpenProject { model ->
            val viewer = model.viewer!!
            onNodeWithContentDescription("Go to page").performClick()
            val start = viewer.scale
            ctrl(Key.Plus)
            assertTrue(viewer.scale > start)
        }
}
