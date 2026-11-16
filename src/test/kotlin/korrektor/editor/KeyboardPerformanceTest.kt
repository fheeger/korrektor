package korrektor.editor

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import korrektor.App
import korrektor.AppModel
import korrektor.format.Metadata
import korrektor.format.Project
import korrektor.importer.PdfImport
import korrektor.pdf.TestPdfs
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Holding Down through many pages of a book of 1000 pages (S06, N1). A held key repeats about 30 times a second. Timing
 * checks are flaky on shared CI machines, so they run on request only: `KORREKTOR_PERF_TESTS=1`.
 */
@OptIn(ExperimentalTestApi::class)
class KeyboardPerformanceTest {
    private fun enabled() =
        assumeTrue(System.getenv("KORREKTOR_PERF_TESTS") != null, "set KORREKTOR_PERF_TESTS=1 to run")

    private fun List<Long>.p95(): Long = sorted()[(size * 0.95).toInt().coerceAtMost(size - 1)]

    private fun withBigBook(block: ComposeUiTest.(AppModel) -> Unit) {
        val dir = Files.createTempDirectory("keyboardperf")
        try {
            val pdf = dir.resolve("big.pdf")
            TestPdfs.book(pdf, 1000, scanPixels = 1500 to 2400)
            val project = Project.create(dir.resolve("big.korrektur"), pdf, emptyList(), Metadata("Big"))
            PdfImport.run(project)
            val model = AppModel()
            runComposeUiTest {
                setContent { App(model, { null }, { null }, Modifier.size(1300.dp, 800.dp)) }
                runOnIdle { model.openProject(project.folder) }
                waitUntil(timeoutMillis = 30_000) { model.editor?.lines?.isNotEmpty() == true }
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

    /** Editor and viewer agree on the page, the page is read, and the cursor is on a line of it (or nowhere). */
    private fun ComposeUiTest.assertSettled(model: AppModel) {
        waitUntil(timeoutMillis = 30_000) { model.editor!!.page == model.viewer!!.page && model.editor!!.layer != null }
        waitForIdle()
        val editor = model.editor!!
        assertEquals(model.viewer!!.page, editor.page)
        assertEquals(null, editor.error)
        editor.activeLineId?.let { id ->
            assertTrue(editor.lines.any { it.id == id }, "the active line is on the page")
        }
    }

    @Test
    fun holdingDownAt30EventsASecondKeepsUpAndDoesNotFreezeTheUi() {
        enabled()
        withBigBook { model ->
            runOnIdle { model.editor!!.requestFocus("l1", 0) }
            waitForIdle()
            val times = mutableListOf<Long>()
            repeat(200) {
                val started = System.nanoTime()
                onRoot().performKeyInput { pressKey(Key.DirectionDown) }
                waitForIdle()
                times += (System.nanoTime() - started) / 1_000_000
                Thread.sleep(33) // the repeat rate of a held key
            }
            assertSettled(model)
            val page = model.viewer!!.page
            println(
                "200 Down events at 30 per second: now on page $page, " +
                    "an event takes median ${times.sorted()[100]} ms, 95th percentile ${times.p95()} ms, " +
                    "maximum ${times.max()} ms",
            )
            // Two lines a page: 200 Down events are 100 pages, and none of them was lost while a page was being read.
            assertEquals(101, page)
            assertEquals("l1", model.editor!!.activeLineId)
            assertTrue(times.p95() < 250, "95th percentile ${times.p95()} ms")
            assertTrue(times.max() < 1500, "maximum ${times.max()} ms")
        }
    }

    @Test
    fun aBurstOf100DownEventsLeavesEditorAndViewerOnTheSamePage() {
        enabled()
        withBigBook { model ->
            runOnIdle { model.editor!!.requestFocus("l1", 0) }
            waitForIdle()
            val started = System.nanoTime()
            repeat(100) { onRoot().performKeyInput { pressKey(Key.DirectionDown) } }
            waitForIdle()
            assertSettled(model)
            val seconds = (System.nanoTime() - started) / 1e9
            println("a burst of 100 Down events: on page ${model.viewer!!.page} after ${"%.1f".format(seconds)} s")
            assertNotNull(model.editor!!.layer)
            assertTrue(model.viewer!!.page >= 40, "the cursor moved on: page ${model.viewer!!.page}")
            assertTrue(seconds < 30, "took $seconds s")
        }
    }

    @Test
    fun holdingCtrlDownThroughManyPagesEndsOnTheExpectedPage() {
        enabled()
        withBigBook { model ->
            runOnIdle { model.editor!!.requestFocus("l1", 0) }
            waitForIdle()
            repeat(100) {
                onRoot().performKeyInput {
                    keyDown(Key.CtrlLeft)
                    pressKey(Key.DirectionDown)
                    keyUp(Key.CtrlLeft)
                }
                waitForIdle()
            }
            // Ctrl+Down changes the page in the viewer at once, whether or not the text is read yet: all 100 count.
            assertEquals(101, model.viewer!!.page)
            assertSettled(model)
        }
    }
}
