package korrektor.editor

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import korrektor.App
import korrektor.AppModel
import korrektor.format.Box
import korrektor.format.Metadata
import korrektor.format.Project
import korrektor.importer.PdfImport
import korrektor.pdf.TestPage
import korrektor.pdf.TestPdfs
import korrektor.pdf.TestText
import korrektor.viewer.PageSize
import korrektor.viewer.ViewerState
import korrektor.viewer.pagePointAt
import korrektor.viewer.reveal
import korrektor.viewer.toScreen
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AutoPanTest {
    /** A page of 200 x 300 points at 200 %: 400 x 600 pixels in a viewport of 100 x 100 (a margin of 20). */
    private fun state(
        offsetX: Double = 0.0,
        offsetY: Double = 0.0,
    ) = ViewerState().also {
        it.setViewport(100, 100)
        it.setPage(PageSize(200.0, 300.0), null)
        it.zoomTo(2.0, 0.0, 0.0)
        it.scrollBy(offsetX, offsetY)
    }

    // --- the arithmetic -------------------------------------------------------------------------------------------

    @Test
    fun aBoxThatIsInViewChangesNothing() {
        val state = state(offsetY = 150.0) // shows y 150..250 pixels
        state.reveal(Box(10.0, 80.0, 20.0, 10.0)) // pixels 160..180 in y, 20..60 in x

        assertEquals(0.0 to 150.0, state.offsetX to state.offsetY)
    }

    @Test
    fun aBoxBelowTheViewportComesIntoViewWithAMarginAtTheBottom() {
        val state = state()
        state.reveal(Box(10.0, 100.0, 20.0, 10.0)) // pixels y 200..220

        // The bottom edge of the box (220) goes to 80, so that 20 pixels of margin are left below it.
        assertEquals(140.0, state.offsetY, 1e-9)
        assertEquals(0.0, state.offsetX, 1e-9)
        val rect = state.toScreen(Box(10.0, 100.0, 20.0, 10.0))
        assertEquals(60.0 to 80.0, rect.y to rect.y + rect.height)
    }

    @Test
    fun aBoxAboveTheViewportComesIntoViewWithAMarginAtTheTop() {
        val state = state(offsetY = 300.0)
        state.reveal(Box(10.0, 100.0, 20.0, 10.0)) // pixels y 200..220, now above the view that starts at 300

        assertEquals(180.0, state.offsetY, 1e-9) // the top edge (200) goes to 20
    }

    @Test
    fun aBoxToTheRightAndToTheLeftComesIntoViewSideways() {
        val state = state()
        state.reveal(Box(150.0, 5.0, 15.0, 10.0)) // pixels x 300..330
        assertEquals(250.0, state.offsetX, 1e-9) // 330 goes to 80: 20 pixels of margin on the right

        state.reveal(Box(5.0, 5.0, 15.0, 10.0)) // pixels x 10..40, now left of the view that starts at 250
        assertEquals(0.0, state.offsetX, 1e-9, "the left edge cannot go beyond the page: 10 - 250 - 20 would be -10")
    }

    @Test
    fun aBoxThatIsPartlyOutIsMovedIn() {
        val state = state(offsetY = 100.0) // y 100..200
        state.reveal(Box(10.0, 95.0, 20.0, 10.0)) // pixels 190..210: the lower part is out

        assertEquals(130.0, state.offsetY, 1e-9) // 210 goes to 80: 210 - 100 + 20 = 130 more
    }

    @Test
    fun theScrollStopsAtTheEdgesOfThePage() {
        val state = state()
        state.reveal(Box(10.0, 295.0, 20.0, 5.0)) // the very bottom: pixels 590..600

        assertEquals(500.0, state.offsetY, 1e-9) // the page's own last position, not 590 - 80
    }

    @Test
    fun aBoxLargerThanTheViewportIsAlignedAtItsTopLeft() {
        val state = state()
        state.reveal(Box(60.0, 80.0, 100.0, 80.0)) // pixels x 120..320, y 160..320: larger than 100 x 100

        assertEquals(120.0 to 160.0, state.offsetX to state.offsetY)
    }

    @Test
    fun aPageSmallerThanTheViewportInOneDirectionDoesNotMoveThere() {
        val state = ViewerState()
        state.setViewport(300, 100)
        state.setPage(PageSize(100.0, 300.0), null)
        state.zoomTo(1.0, 0.0, 0.0) // 100 x 300 pixels: centred sideways, taller than the viewport

        state.reveal(Box(80.0, 250.0, 15.0, 10.0))

        assertEquals(0.0, state.offsetX, 1e-9)
        assertTrue(state.offsetY > 0.0, "up and down it moves")
    }

    @Test
    fun withoutAViewportOrAPageNothingHappens() {
        ViewerState().reveal(Box(1.0, 1.0, 1.0, 1.0)) // no crash
        val noViewport = ViewerState().also { it.setPage(PageSize(100.0, 100.0), null) }
        noViewport.reveal(Box(1.0, 1.0, 1.0, 1.0))
        assertEquals(0.0, noViewport.offsetY)
    }

    // --- in the whole app -------------------------------------------------------------------------------------

    private fun withOpenProject(block: ComposeUiTest.(AppModel) -> Unit) {
        val dir = Files.createTempDirectory("autopan")
        try {
            val pdf = dir.resolve("book.pdf")
            val texts = (1..12).map { TestText("Zeile Nummer $it hier", 20f, 280f - 20f * it, 10f) }
            TestPdfs.write(pdf, listOf(TestPage(texts = texts)))
            val project = Project.create(dir.resolve("b.korrektur"), pdf, emptyList(), Metadata("B"))
            PdfImport.run(project)
            val model = AppModel()
            runComposeUiTest {
                setContent { App(model, { project.folder }, { null }, Modifier.size(1100.dp, 700.dp)) }
                onNodeWithText("Open project…").performClick()
                waitUntil(
                    timeoutMillis = 10_000,
                ) { runCatching { onNodeWithContentDescription("Page 1").assertExists() }.isSuccess }
                waitUntil(timeoutMillis = 10_000) {
                    runCatching { onNode(hasTestTag("line text l12"), useUnmergedTree = true).assertExists() }.isSuccess
                }
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

    /** Whether the box is in view up and down, and its left edge is visible (a line can be wider than the panel). */
    private fun ViewerState.isInView(box: Box): Boolean {
        val r = toScreen(box)
        return r.y >= 0 && r.y + r.height <= viewportHeight && r.x >= 0 && r.x < viewportWidth
    }

    @Test
    fun movingTheCursorToALineOutOfViewScrollsTheScanToIt() =
        withOpenProject { model ->
            val editor = assertNotNull(model.editor)
            val viewer = assertNotNull(model.viewer)
            runOnIdle { viewer.zoomTo(viewer.scale * 3, 0.0, 0.0) } // far more than fits
            waitForIdle()
            val last = editor.lines.last().box!!
            assertTrue(!viewer.isInView(last), "the last line is out of view at this zoom")

            runOnIdle { editor.requestFocus(editor.lines.last().id, 0) } // the cursor moves down the text
            waitForIdle()

            assertTrue(viewer.isInView(last), "the scan followed the cursor: ${viewer.toScreen(last)}")

            runOnIdle { editor.requestFocus(editor.lines.first().id, 0) } // and back up
            waitForIdle()
            assertTrue(viewer.isInView(editor.lines.first().box!!))
        }

    @Test
    fun aLineWithoutABoxDoesNotMoveTheScan() =
        withOpenProject { model ->
            val editor = assertNotNull(model.editor)
            val viewer = assertNotNull(model.viewer)
            runOnIdle { viewer.zoomTo(viewer.scale * 3, 0.0, 0.0) }
            waitForIdle()
            runOnIdle { editor.addLineAfter(editor.lines.last().id) } // a new line has no box
            waitForIdle()
            val before = viewer.offsetX to viewer.offsetY

            runOnIdle { editor.requestFocus(editor.lines.last().id, 0) }
            waitForIdle()

            assertEquals(before, viewer.offsetX to viewer.offsetY)
        }

    @Test
    fun typingInALineDoesNotMoveTheScanEvenIfTheUserMovedItAway() =
        withOpenProject { model ->
            val editor = assertNotNull(model.editor)
            val viewer = assertNotNull(model.viewer)
            runOnIdle { viewer.zoomTo(viewer.scale * 3, 0.0, 0.0) }
            runOnIdle { editor.requestFocus("l1", 0) }
            waitForIdle()
            runOnIdle { viewer.scrollBy(0.0, 10_000.0) } // the user looks at another part of the page
            waitForIdle()
            val before = viewer.offsetX to viewer.offsetY

            onNode(hasTestTag("line text l1"), useUnmergedTree = true).performTextReplacement("Zeile geändert")
            waitForIdle()

            assertEquals(before, viewer.offsetX to viewer.offsetY, "the scan is not pulled back while typing")
        }

    @Test
    fun clickingALineOnTheScanDoesNotJumpTheScan() =
        withOpenProject { model ->
            val editor = assertNotNull(model.editor)
            val viewer = assertNotNull(model.viewer)
            val box = editor.lines[0].box!!
            val rect = viewer.toScreen(Box(box.x + box.width / 2, box.y + box.height / 2, 0.0, 0.0))
            val before = viewer.offsetX to viewer.offsetY

            onNodeWithContentDescription(
                "Page 1",
            ).performMouseInput { click(Offset(rect.x.toFloat(), rect.y.toFloat())) }
            waitForIdle()

            assertEquals(editor.lines[0].id, editor.activeLineId)
            assertEquals(before, viewer.offsetX to viewer.offsetY, "the clicked line was in view already")
        }

    @Test
    fun aBoxThatIsPartlyInViewSidewaysIsLeftWhereItIs() {
        val state = state(offsetX = 50.0) // shows x 50..150 pixels
        state.reveal(Box(10.0, 5.0, 100.0, 10.0)) // pixels x 20..220 and y 10..30: wider than the view, partly in it

        assertEquals(50.0, state.offsetX, 1e-9, "a click on the visible part must not make the scan jump")
    }

    @Test
    fun clickingTheVisiblePartOfAWideLineDoesNotMoveTheScanSideways() =
        withOpenProject { model ->
            val editor = assertNotNull(model.editor)
            val viewer = assertNotNull(model.viewer)
            runOnIdle {
                viewer.zoomTo(viewer.scale * 3, 0.0, 0.0)
                viewer.scrollBy(-10_000.0, -10_000.0)
                viewer.scrollBy(300.0, 0.0) // the line is wider than the panel: its left part is out of view
            }
            waitForIdle()
            val box = editor.lines[0].box!!
            val inView = viewer.pagePointAt(300.0, 40.0).let { Box(it.x, box.y + box.height / 2, 0.0, 0.0) }
            val rect = viewer.toScreen(inView)
            val before = viewer.offsetX to viewer.offsetY

            onNodeWithContentDescription(
                "Page 1",
            ).performMouseInput { click(Offset(rect.x.toFloat(), rect.y.toFloat())) }
            waitForIdle()

            assertEquals(editor.lines[0].id, editor.activeLineId)
            assertEquals(before.first, viewer.offsetX, 1e-9, "the scan did not jump sideways")
        }
}
