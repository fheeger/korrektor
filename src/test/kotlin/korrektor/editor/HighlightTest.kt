package korrektor.editor

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import korrektor.App
import korrektor.AppModel
import korrektor.format.Box
import korrektor.format.Line
import korrektor.format.Metadata
import korrektor.format.PageLayer
import korrektor.format.Project
import korrektor.format.Region
import korrektor.format.RegionType
import korrektor.format.Word
import korrektor.importer.PdfImport
import korrektor.pdf.TestPage
import korrektor.pdf.TestPdfs
import korrektor.pdf.TestText
import korrektor.viewer.FakePageSource
import korrektor.viewer.LineHighlight
import korrektor.viewer.PageViewer
import korrektor.viewer.ViewerState
import korrektor.viewer.toScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class HighlightTest {
    private val withBox = Line("l1", Box(10.0, 20.0, 100.0, 12.0), "l1", listOf(Word("mit"), Word("Box")))
    private val withoutBox = Line("l2", null, null, listOf(Word("ohne")))
    private val layer =
        PageLayer(1, 200.0, 300.0, listOf(Region("r1", RegionType.TEXT, 1, null, listOf(withBox, withoutBox))))

    private fun editor(): BookEditor = BookEditor({ layer }, Dispatchers.IO)

    // --- which line is marked -------------------------------------------------------------------------------

    @Test
    fun theBoxOfTheActiveLineIsMarkedAndNothingElse() {
        val editor = editor()
        runBlocking { editor.show(1) }

        assertNull(editor.highlightFor(1), "no active line")

        editor.activate("l1")
        assertEquals(LineHighlight("l1", withBox.box!!), editor.highlightFor(1))
    }

    @Test
    fun aLineWithoutABoxIsNotMarked() {
        val editor = editor()
        runBlocking { editor.show(1) }

        editor.activate("l2")

        assertNull(editor.highlightFor(1))
    }

    @Test
    fun nothingIsMarkedForAnotherPageThanTheEditorIsOn() {
        val editor = editor()
        runBlocking { editor.show(1) }
        editor.activate("l1")

        assertNull(editor.highlightFor(2), "the viewer has moved on, the editor has not yet")
    }

    @Test
    fun afterASplitTheFirstHalfIsMarkedByItsBox() {
        val editor = editor()
        runBlocking { editor.show(1) }
        editor.splitLine("l1", 3) // "mit" | "Box": the cursor is in the new second line, which has no box ...

        editor.activate("l1") // ... and the first half keeps the line's id and its box

        val mark = editor.highlightFor(1)!!
        assertEquals("l1", mark.lineId)
        assertEquals(withBox.box, mark.box)
        editor.activate(editor.lines[1].id)
        assertNull(editor.highlightFor(1), "the second half has no place on the scan")
    }

    // --- on the screen ----------------------------------------------------------------------------------------

    private fun ComposeUiTest.waitForPage() =
        waitUntil {
            runCatching { onNodeWithContentDescription("Page 1").assertExists() }.isSuccess
        }

    @Test
    fun theHighlightIsThereWhenAskedForAndGoneWhenNot() =
        runComposeUiTest {
            val source = FakePageSource()
            var mark: LineHighlight? by mutableStateOf<LineHighlight?>(null)
            setContent { PageViewer(source, highlight = mark) }
            waitForPage()
            onNodeWithContentDescription("Highlighted line: l1").assertDoesNotExist()

            mark = LineHighlight("l1", withBox.box!!)
            waitForIdle()
            onNodeWithContentDescription("Highlighted line: l1").assertExists()

            mark = null
            waitForIdle()
            onNodeWithContentDescription("Highlighted line: l1").assertDoesNotExist()
        }

    /** The colour of the pixel at ([x], [y]) of the window. */
    private fun ComposeUiTest.pixel(
        x: Double,
        y: Double,
    ): Int = onRoot().captureToImage().toAwtImage().getRGB(x.toInt(), y.toInt())

    @Test
    fun theHighlightIsDrawnWhereTheTransformPutsItAndFollowsZoomAndMovement() =
        runComposeUiTest {
            val source = FakePageSource(korrektor.viewer.PageSize(200.0, 300.0))
            val state = ViewerState()
            setContent {
                PageViewer(
                    source,
                    Modifier.size(600.dp, 700.dp),
                    state,
                    highlight = LineHighlight("l1", withBox.box!!),
                )
            }
            waitForPage()
            waitForIdle()
            val origin = onNodeWithContentDescription("Page 1").fetchSemanticsNode().positionInRoot

            for (zoom in listOf(1.0, 2.0)) {
                runOnIdle {
                    state.zoomTo(state.scale * zoom, 0.0, 0.0)
                    state.scrollBy(-10_000.0, -10_000.0)
                    state.scrollBy(20.0, 10.0)
                }
                waitForIdle()
                val rect = state.toScreen(withBox.box!!)
                val inside = pixel(origin.x + rect.x + rect.width / 2, origin.y + rect.y + rect.height / 2)
                val outside = pixel(origin.x + rect.x + rect.width / 2, origin.y + rect.y - 6)
                assertNotEquals(outside, inside, "the highlight tints the line's box at zoom $zoom: $rect")
            }
        }

    // --- in the whole app ---------------------------------------------------------------------------------------

    @Test
    fun theLineWithTheCursorIsMarkedOnTheScanAndMarkingChangesWithTheCursorAndThePage() {
        val dir = Files.createTempDirectory("highlight")
        try {
            val pdf = dir.resolve("book.pdf")
            TestPdfs.write(
                pdf,
                listOf(
                    TestPage(texts = listOf(TestText("Erste Zeile", 30f, 250f), TestText("zweite Zeile", 30f, 230f))),
                    TestPage(texts = listOf(TestText("Seite zwei", 30f, 250f))),
                ),
            )
            val project = Project.create(dir.resolve("b.korrektur"), pdf, emptyList(), Metadata("B"))
            PdfImport.run(project)
            val model = AppModel()
            runComposeUiTest {
                setContent { App(model, { project.folder }, { null }) }
                onNodeWithText("Open project…").performClick()
                waitUntil(timeoutMillis = 5_000) {
                    runCatching { onNode(hasTestTag("line text l1"), useUnmergedTree = true).assertExists() }.isSuccess
                }
                onNodeWithContentDescription("Highlighted line: l1").assertDoesNotExist()

                onNode(hasTestTag("line text l1"), useUnmergedTree = true).performClick()
                waitUntil(timeoutMillis = 5_000) {
                    runCatching { onNodeWithContentDescription("Highlighted line: l1").assertExists() }.isSuccess
                }

                onNode(hasTestTag("line text l2"), useUnmergedTree = true).performClick()
                waitUntil(timeoutMillis = 5_000) {
                    runCatching { onNodeWithContentDescription("Highlighted line: l2").assertExists() }.isSuccess
                }
                onNodeWithContentDescription("Highlighted line: l1").assertDoesNotExist()

                runOnIdle { model.viewer!!.goTo(2) }
                waitForIdle()
                onNodeWithContentDescription("Highlighted line: l2").assertDoesNotExist()
                onNodeWithContentDescription("Highlighted line: l1").assertDoesNotExist()
            }
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    @Test
    fun anAddedLineIsNotMarkedAndTheLineBeforeItStillIs() =
        runComposeUiTest {
            val editor = editor()
            runBlocking { editor.show(1) }
            val source = FakePageSource()
            val state = ViewerState()
            setContent { PageViewer(source, state = state, highlight = editor.highlightFor(1)) }
            waitForPage()

            editor.activate("l1")
            waitForIdle()
            onNodeWithContentDescription("Highlighted line: l1").assertExists()

            editor.addLineAfter("l1") // the cursor is in the new line, which has no box
            waitForIdle()
            onNodeWithContentDescription("Highlighted line: l1").assertDoesNotExist()
            assertEquals(null, editor.highlightFor(1))
        }
}
