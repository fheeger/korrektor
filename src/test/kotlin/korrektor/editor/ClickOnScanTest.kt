package korrektor.editor

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import korrektor.App
import korrektor.AppModel
import korrektor.format.Box
import korrektor.format.Line
import korrektor.format.Metadata
import korrektor.format.Project
import korrektor.format.Word
import korrektor.importer.PdfImport
import korrektor.pdf.TestPage
import korrektor.pdf.TestPdfs
import korrektor.pdf.TestText
import korrektor.viewer.FakePageSource
import korrektor.viewer.PagePoint
import korrektor.viewer.PageSize
import korrektor.viewer.PageViewer
import korrektor.viewer.ScanLinks
import korrektor.viewer.ViewerState
import korrektor.viewer.pagePointAt
import korrektor.viewer.toScreen
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ClickOnScanTest {
    private fun line(
        id: String,
        box: Box?,
        text: String = "Zeile $id",
    ) = Line(id, box, null, text.split(' ').map { Word(it) })

    // --- the inverse transform ---------------------------------------------------------------------------

    @Test
    fun aScreenPositionBecomesTheBoxPositionAndBack() {
        val state = ViewerState()
        state.setViewport(300, 200)
        state.setPage(PageSize(200.0, 300.0), null)
        val box = Box(50.0, 100.0, 20.0, 10.0)
        for (scale in listOf(0.5, 1.0, 1.7, 3.0, 8.0)) {
            state.zoomTo(scale, 0.0, 0.0)
            for ((dx, dy) in listOf(0.0 to 0.0, 25.0 to 40.0, 1000.0 to 1000.0)) {
                state.scrollBy(-10_000.0, -10_000.0)
                state.scrollBy(dx, dy)
                val rect = state.toScreen(box)
                val back = state.pagePointAt(rect.x, rect.y)
                assertEquals(box.x, back.x, 1e-9, "x at $scale, $dx, $dy")
                assertEquals(box.y, back.y, 1e-9, "y at $scale, $dx, $dy")
                val far = state.pagePointAt(rect.x + rect.width, rect.y + rect.height)
                assertEquals(box.x + box.width, far.x, 1e-9)
                assertEquals(box.y + box.height, far.y, 1e-9)
            }
        }
    }

    @Test
    fun aCentredPageIsInvertedToo() {
        val state = ViewerState()
        state.setViewport(400, 400)
        state.setPage(PageSize(100.0, 100.0), null)
        state.zoomTo(1.0)

        assertEquals(PagePoint(10.0, 20.0), state.pagePointAt(160.0, 170.0), "the page starts at 150, 150")
    }

    // --- which line ---------------------------------------------------------------------------------------

    @Test
    fun aPointInOneBoxGivesThatLine() {
        val lines = listOf(line("l1", Box(10.0, 10.0, 100.0, 10.0)), line("l2", Box(10.0, 24.0, 100.0, 10.0)))
        assertEquals("l1", lineAt(PagePoint(50.0, 15.0), lines)?.id)
        assertEquals("l2", lineAt(PagePoint(50.0, 30.0), lines)?.id)
    }

    @Test
    fun theEdgesOfABoxBelongToIt() {
        val lines = listOf(line("l1", Box(10.0, 10.0, 100.0, 10.0)))
        assertEquals("l1", lineAt(PagePoint(10.0, 10.0), lines)?.id)
        assertEquals("l1", lineAt(PagePoint(110.0, 20.0), lines)?.id)
        assertNull(lineAt(PagePoint(110.1, 15.0), lines))
    }

    @Test
    fun whereBoxesOverlapTheSmallestWinsAndTheFirstOfEqualOnes() {
        val big = line("big", Box(0.0, 0.0, 200.0, 40.0))
        val small = line("small", Box(10.0, 10.0, 30.0, 10.0)) // a stray word inside a line's box
        val twin1 = line("twin1", Box(100.0, 100.0, 20.0, 10.0))
        val twin2 = line("twin2", Box(100.0, 100.0, 20.0, 10.0))

        assertEquals("small", lineAt(PagePoint(20.0, 15.0), listOf(big, small))?.id)
        assertEquals(
            "small",
            lineAt(PagePoint(20.0, 15.0), listOf(small, big))?.id,
            "the order does not matter for size",
        )
        assertEquals("big", lineAt(PagePoint(150.0, 30.0), listOf(big, small))?.id)
        assertEquals("twin1", lineAt(PagePoint(110.0, 105.0), listOf(twin1, twin2))?.id)
    }

    @Test
    fun outsideEveryBoxAndOnLinesWithoutABoxNothingIsHit() {
        val lines = listOf(line("l1", Box(10.0, 10.0, 100.0, 10.0)), line("added", null))
        assertNull(lineAt(PagePoint(300.0, 300.0), lines))
        assertNull(lineAt(PagePoint(50.0, 50.0), lines))
        assertNull(lineAt(PagePoint(0.0, 0.0), listOf(line("added", null))))
    }

    @Test
    fun theCursorGoesToTheCharacterUnderTheClick() {
        val l = line("l1", Box(100.0, 10.0, 100.0, 10.0), "abcdefghij") // ten letters, 10 points each
        assertEquals(0, cursorIndexAt(l, 100.0))
        assertEquals(5, cursorIndexAt(l, 150.0))
        assertEquals(10, cursorIndexAt(l, 200.0))
        assertEquals(3, cursorIndexAt(l, 128.0), "to the nearest boundary")
        assertEquals(0, cursorIndexAt(l, 50.0), "left of the box")
        assertEquals(10, cursorIndexAt(l, 500.0), "right of the box")
        assertEquals(0, cursorIndexAt(line("l2", null, "abc"), 5.0), "no box")
        assertEquals(0, cursorIndexAt(line("l3", Box(0.0, 0.0, 10.0, 10.0), ""), 5.0), "no text")
    }

    // --- click or drag ------------------------------------------------------------------------------------

    @Test
    fun aClickGivesThePageUnderThePointerAndADragDoesNot() =
        runComposeUiTest {
            val clicks = mutableListOf<PagePoint>()
            val state = ViewerState()
            setContent {
                PageViewer(
                    FakePageSource(PageSize(200.0, 300.0)),
                    Modifier.size(600.dp, 600.dp),
                    state,
                    links =
                        ScanLinks(onClick = {
                            clicks +=
                                it
                        }),
                )
            }
            waitUntil { runCatching { onNodeWithContentDescription("Page 1").assertExists() }.isSuccess }
            waitForIdle()

            onNodeWithContentDescription("Page 1").performMouseInput { click(Offset(200f, 300f)) }
            waitForIdle()

            assertEquals(1, clicks.size)
            val expected = state.pagePointAt(200.0, 300.0)
            assertEquals(expected.x, clicks[0].x, 1e-6)
            assertEquals(expected.y, clicks[0].y, 1e-6)

            onNodeWithContentDescription("Page 1").performMouseInput {
                moveTo(Offset(200f, 300f))
                press()
                moveBy(Offset(0f, -120f)) // far beyond the touch slop: a drag
                release()
            }
            waitForIdle()

            assertEquals(1, clicks.size, "a drag pans the page and is no click")
            assertTrue(state.offsetY > 0.0)
        }

    // --- in the whole app ---------------------------------------------------------------------------------

    /** Clicks on the scan at the page point ([x], [y]), wherever the page is at the moment. */
    private fun androidx.compose.ui.test.ComposeUiTest.clickScan(
        viewer: ViewerState,
        x: Double,
        y: Double,
    ) {
        val rect = viewer.toScreen(Box(x, y, 0.0, 0.0))
        onNodeWithContentDescription("Page 1").performMouseInput { click(Offset(rect.x.toFloat(), rect.y.toFloat())) }
        waitForIdle()
    }

    /** A window with the app on a project of one generated page with two lines, opened and shown. */
    private fun withOpenProject(block: androidx.compose.ui.test.ComposeUiTest.(AppModel) -> Unit) {
        val dir = Files.createTempDirectory("clickscan")
        try {
            val pdf = dir.resolve("book.pdf")
            val lines =
                listOf(TestText("Erste Zeile hier", 30f, 250f, 14f), TestText("zweite Zeile da", 30f, 200f, 14f))
            TestPdfs.write(pdf, listOf(TestPage(texts = lines)))
            val project = Project.create(dir.resolve("b.korrektur"), pdf, emptyList(), Metadata("B"))
            PdfImport.run(project)
            val model = AppModel()
            runComposeUiTest {
                setContent { App(model, { project.folder }, { null }, Modifier.size(1100.dp, 800.dp)) }
                onNodeWithText("Open project…").performClick()
                val shown = { runCatching { onNodeWithContentDescription("Page 1").assertExists() }.isSuccess }
                waitUntil(timeoutMillis = 10_000) { shown() }
                waitUntil(timeoutMillis = 10_000) {
                    runCatching { onNode(hasTestTag("line text l2"), useUnmergedTree = true).assertExists() }.isSuccess
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

    private fun androidx.compose.ui.test.ComposeUiTest.cursorOf(id: String) =
        onNode(hasTestTag("line text $id"), useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsProperties.TextSelectionRange]
            .start

    @Test
    fun clickingALineOnTheScanPutsTheCursorInItsRowAtTheClickedPlace() =
        withOpenProject { model ->
            val editor = assertNotNull(model.editor)
            val second = editor.lines.first { it.id == "l2" }
            val box = second.box!!

            clickScan(assertNotNull(model.viewer), box.x + box.width / 4, box.y + box.height / 2) // a quarter in

            assertEquals("l2", editor.activeLineId)
            onNode(hasTestTag("line text l2"), useUnmergedTree = true).assertIsFocused()
            assertEquals(cursorIndexAt(second, box.x + box.width / 4), cursorOf("l2"))
            assertTrue(cursorOf("l2") in 1..second.text.length - 1, "a quarter into the line: ${cursorOf("l2")}")
        }

    @Test
    fun aClickOnEmptyPaperChangesNothing() =
        withOpenProject { model ->
            val editor = assertNotNull(model.editor)
            val viewer = assertNotNull(model.viewer)
            runOnIdle { editor.activate("l2") }

            clickScan(viewer, 5.0, 5.0)

            assertEquals("l2", editor.activeLineId, "the cursor is not lost by a stray click")
            assertNull(editor.focusRequest)
        }

    @Test
    fun aClickStillLandsOnTheLineWhenThePageIsZoomedInAndMoved() =
        withOpenProject { model ->
            val editor = assertNotNull(model.editor)
            val viewer = assertNotNull(model.viewer)
            val first = editor.lines.first { it.id == "l1" }.box!!
            runOnIdle {
                viewer.zoomTo(viewer.scale * 2, 0.0, 0.0)
                viewer.scrollBy(-10_000.0, -10_000.0)
                viewer.scrollBy(first.x * viewer.scale - 40, first.y * viewer.scale - 40)
            }
            waitForIdle()

            clickScan(viewer, first.x + first.width / 2, first.y + first.height / 2)

            assertEquals("l1", editor.activeLineId)
            onNode(hasTestTag("line text l1"), useUnmergedTree = true).assertIsFocused()
        }

    @Test
    fun aClickWhileTheEditorIsStillOnAnotherPageDoesNothing() {
        val layer =
            korrektor.format.PageLayer(
                1,
                100.0,
                100.0,
                listOf(
                    korrektor.format.Region(
                        "r1",
                        korrektor.format.RegionType.TEXT,
                        1,
                        null,
                        listOf(line("l1", Box(0.0, 0.0, 50.0, 50.0))),
                    ),
                ),
            )
        val editor = BookEditor({ layer }, kotlinx.coroutines.Dispatchers.IO)
        kotlinx.coroutines.runBlocking { editor.show(1) }

        editor.clickOnScan(2, PagePoint(10.0, 10.0)) // the viewer is on page 2

        assertNull(editor.activeLineId)
        assertNull(editor.focusRequest)
    }
}
