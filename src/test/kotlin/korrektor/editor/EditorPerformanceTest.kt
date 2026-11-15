package korrektor.editor

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
import korrektor.pdf.TestPdfs
import korrektor.viewer.PageCache
import korrektor.viewer.PdfPageSource
import korrektor.viewer.ViewerState
import korrektor.viewer.nextRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Typing and page switching with both panels, in a book of 1000 pages (N1, S05). Timing checks are flaky on shared CI
 * machines, so they run on request only: `KORREKTOR_PERF_TESTS=1`.
 */
@OptIn(ExperimentalTestApi::class)
class EditorPerformanceTest {
    private val sample = Path.of("samples", "Abhandlung_über_den_kleinen_Krieg.pdf")

    private fun enabled() =
        assumeTrue(System.getenv("KORREKTOR_PERF_TESTS") != null, "set KORREKTOR_PERF_TESTS=1 to run")

    private fun <T : Comparable<T>> List<T>.median(): T = sorted()[size / 2]

    private fun List<Long>.p95(): Long = sorted()[(size * 0.95).toInt().coerceAtMost(size - 1)]

    private fun stats(
        label: String,
        values: List<Long>,
    ): String =
        "$label: median ${values.median()} ms, 95th percentile ${values.p95()} ms, " +
            "maximum ${values.max()} ms (${values.size})"

    /** A book of 1000 pages with scans and two lines of text each, imported into a project in [dir]. */
    private fun bigProject(dir: Path): Project {
        val pdf = dir.resolve("big.pdf")
        TestPdfs.book(pdf, 1000, scanPixels = 1500 to 2400)
        val project = Project.create(dir.resolve("big.korrektur"), pdf, emptyList(), Metadata("Big"))
        PdfImport.run(project)
        return project
    }

    private fun withBigProject(block: (Project) -> Unit) {
        val dir = Files.createTempDirectory("editorperf")
        try {
            block(bigProject(dir))
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    // --- the model ---------------------------------------------------------------------------------------------

    @Test
    fun aThousandEditsOfALineOnAPageOf40LinesTakeUnAMillisecondEach() {
        enabled()

        fun line(i: Int) =
            Line(
                "l$i",
                Box(10.0, 10.0 + 12 * i, 200.0, 10.0),
                "l$i",
                (1..9).map {
                    Word(
                        "wort$it",
                        Box(
                            10.0 + 20 * it,
                            10.0 + 12 * i,
                            18.0,
                            10.0,
                        ),
                    )
                },
            )
        val layer = PageLayer(500, 300.0, 480.0, listOf(Region("r1", RegionType.TEXT, 1, null, (1..40).map(::line))))
        val editor = BookEditor({ layer }, Dispatchers.IO)
        runBlocking { editor.show(500) }
        repeat(200) { editor.setLineText("l20", "warm up $it") } // the JVM warms up

        val started = System.nanoTime()
        repeat(1000) { editor.setLineText("l20", "Zeile mit Text $it und mehr") }
        val average = (System.nanoTime() - started) / 1e6 / 1000

        println("1000 setLineText on a page of 40 lines: ${"%.3f".format(average)} ms each")
        assertTrue(average < 1.0, "an edit takes $average ms")
    }

    // --- typing in the window ---------------------------------------------------------------------------------

    @Test
    fun typing500CharactersIntoALineOfPage500IsFast() {
        enabled()
        withBigProject { project ->
            val model = AppModel()
            runComposeUiTest {
                setContent { App(model, { project.folder }, { null }, Modifier.size(1300.dp, 800.dp)) }
                onNodeWithText("Open project…").performClick()
                waitUntil(
                    timeoutMillis = 15_000,
                ) { runCatching { onNodeWithContentDescription("Page 1").assertExists() }.isSuccess }
                runOnIdle { model.viewer!!.goTo(500) }
                waitUntil(timeoutMillis = 15_000) { model.editor!!.page == 500 && model.editor!!.layer != null }
                waitForIdle()
                val field = onNode(hasTestTag("line text l1"), useUnmergedTree = true)
                repeat(30) { field.performTextInput("a") } // warm up

                val times =
                    List(500) {
                        val started = System.nanoTime()
                        field.performTextInput("x")
                        (System.nanoTime() - started) / 1_000_000
                    }

                println(
                    "Typing into the window, both panels showing page 500 of 1000: " + stats("per character", times),
                )
                val typed = field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text
                // The cursor starts at the beginning of the line, so the typed characters are there, in order.
                assertTrue(typed.startsWith("a".repeat(30) + "x".repeat(500)), "every character arrived")
                assertTrue(times.median() < MEDIAN_KEY_MS, "median ${times.median()} ms per character")
                assertTrue(times.max() < MAX_KEY_MS, "slowest ${times.max()} ms")
            }
        }
    }

    // --- page switching with both panels ----------------------------------------------------------------------------

    /** The time until the scan has its picture and the editor has the text, one after the other (the app does both
     *  at once, so this is an upper bound). */
    private fun switchTimes(
        pdf: Path,
        project: Project,
        pages: Int,
    ): List<Long> {
        val source = PageCache(PdfPageSource(pdf))
        val state = ViewerState(pages).also { it.setViewport(900, 800) }
        val editor = BookEditor(project::readPage, Dispatchers.IO)
        try {
            return runBlocking {
                suspend fun show(page: Int): Long {
                    val started = System.nanoTime()
                    state.goTo(page)
                    state.setPage(source.size(page), source.nativeScale(page))
                    val request = checkNotNull(state.nextRequest(null))
                    source.render(page, request.scale, request.region)
                    editor.show(page)
                    return (System.nanoTime() - started) / 1_000_000
                }
                show(1) // opens the PDF
                val random = Random(42)
                List(50) { show(random.nextInt(2, pages)) }
            }
        } finally {
            source.close()
        }
    }

    @Test
    fun switchingPagesWithBothPanelsInABookOf1000PagesIsFastEnough() {
        enabled()
        withBigProject { project ->
            val times = switchTimes(project.sourcePdf, project, 1000)
            println("Page switch with scan and text, 1000 pages: " + stats("picture and text", times))
            assertTrue(times.median() < 300, "median ${times.median()} ms")
            assertTrue(times.max() < 500, "slowest ${times.max()} ms")
        }
    }

    @Test
    fun switchingPagesWithBothPanelsInTheSampleIsFastEnough() {
        enabled()
        assumeTrue(sample.exists(), "the sample PDF is not available")
        val dir = Files.createTempDirectory("editorperf")
        try {
            val project = Project.create(dir.resolve("s.korrektur"), sample, emptyList(), Metadata("S"))
            PdfImport.run(project)
            val times = switchTimes(project.sourcePdf, project, 165)
            println("Page switch with scan and text, the sample: " + stats("picture and text", times))
            assertTrue(times.median() < 300, "median ${times.median()} ms")
            assertTrue(times.max() < 500, "slowest ${times.max()} ms")
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    private companion object {
        const val MEDIAN_KEY_MS = 16L
        const val MAX_KEY_MS = 100L
    }
}
