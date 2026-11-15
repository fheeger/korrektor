package korrektor.editor

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import korrektor.App
import korrektor.AppModel
import korrektor.format.Box
import korrektor.format.Line
import korrektor.format.Metadata
import korrektor.format.Project
import korrektor.importer.PdfImport
import korrektor.pdf.PdfDocument
import korrektor.pdf.PixelRegion
import korrektor.viewer.PagePoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.TestInstance
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The editor on the real sample (the Abhandlung, 165 pages): where the lines are on the scan, what the text panel
 * shows, and that both panels stay on the same page. The sample is imported once for all tests; every test is skipped
 * when the file is missing, which is always the case on CI.
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EditorSampleTest {
    private val sample = Path.of("samples", "Abhandlung_über_den_kleinen_Krieg.pdf")
    private val directory: Path by lazy { Files.createTempDirectory("editorsample") }
    private val project: Project by lazy {
        val created = Project.create(directory.resolve("sample.korrektur"), sample, emptyList(), Metadata("Abhandlung"))
        PdfImport.run(created)
        created
    }

    @AfterAll
    fun cleanUp() {
        if (sample.exists()) {
            directory.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    private fun available() = assumeTrue(sample.exists(), "the sample PDF is not available")

    private fun linesOf(page: Int): List<Line> = PageEdits.readingOrder(project.readPage(page))

    // --- where the lines are --------------------------------------------------------------------------------

    @Test
    fun aClickAtTheCentreOfEachLineLandsOnThatLineExceptWhereABoxLiesInsideAnother() {
        available()
        val exceptions = mutableListOf<String>()
        var total = 0
        for (page in listOf(2, 6, 50, 100)) {
            val lines = linesOf(page)
            for (line in lines.filter { it.box != null }) {
                total++
                val box = line.box!!
                val hit = lineAt(PagePoint(box.x + box.width / 2, box.y + box.height / 2), lines)
                if (hit?.id !=
                    line.id
                ) {
                    exceptions +=
                        "page $page: ${line.id} '${line.text.take(30)}' -> ${hit?.id} '${hit?.text?.take(30)}'"
                }
            }
        }
        println(
            "Clicks at the centre of $total line boxes: ${exceptions.size} land on another line:\n  " +
                exceptions.joinToString("\n  "),
        )
        assertTrue(
            exceptions.size <= total / 20,
            "more than 5 % of the clicks land elsewhere: ${exceptions.size} of $total",
        )
        // The lines of the body text of the two pages that were looked at are all hit correctly.
        val body = listOf(6, 50).flatMap { page -> linesOf(page).filter { (it.box?.width ?: 0.0) > 150.0 } }
        val wrong =
            body.filter { line ->
                val b = line.box!!
                lineAt(PagePoint(b.x + b.width / 2, b.y + b.height / 2), linesOfPageOf(line)) ?.id !=
                    line.id
            }
        assertEquals(emptyList(), wrong.map { it.id }, "full-width lines of pages 6 and 50")
    }

    private fun linesOfPageOf(line: Line): List<Line> =
        (1..165).firstNotNullOf { p ->
            linesOf(p).takeIf { l ->
                l.any {
                    it ===
                        line ||
                        it == line
                }
            }
        }

    @Test
    fun theBoxOfALineLiesOnInkInTheRenderedScan() {
        available()
        PdfDocument.open(sample).use { pdf ->
            for (page in listOf(6, 50)) checkInk(pdf, page)
        }
    }

    private fun checkInk(
        pdf: PdfDocument,
        page: Int,
    ) {
        val layer = project.readPage(page)
        val scale = 2.0
        val size = PixelRegion(0, 0, (layer.width * scale).toInt(), (layer.height * scale).toInt())
        val image = pdf.page(page - 1).use { it.renderRegion(scale, size) }
        val body = PageEdits.readingOrder(layer).mapNotNull { it.box }.filter { it.width > 150.0 }
        val inside = body.map { inkFraction(image, it, scale) }
        // The same height at the two margins of the page (left and right of the text block, 25 points wide).
        val left = body.map { inkFraction(image, Box(2.0, it.y, 25.0, it.height), scale) }
        val right = body.map { inkFraction(image, Box(layer.width - 27.0, it.y, 25.0, it.height), scale) }
        val margin = minOf(left.median(), right.median())
        println(
            "page $page: ink in the line boxes ${"%.2f".format(inside.median())}, in the margins " +
                "${"%.2f".format(
                    left.median(),
                )} (left) and ${"%.2f".format(right.median())} (right), ${body.size} lines",
        )
        assertTrue(inside.median() > 0.10, "page $page: the boxes hold text: ${inside.median()}")
        assertTrue(
            inside.median() > 3 * margin,
            "page $page: more ink in the boxes than in the emptier margin: $margin",
        )
        assertTrue(inside.count { it > 0.05 } >= body.size * 0.95, "page $page: nearly every box has ink")
    }

    private fun List<Double>.median(): Double = sorted()[size / 2]

    /** The share of dark pixels in [box] (in points) of the rendered page. */
    private fun inkFraction(
        image: BufferedImage,
        box: Box,
        scale: Double,
    ): Double {
        val x0 = (box.x * scale).toInt().coerceIn(0, image.width - 1)
        val y0 = (box.y * scale).toInt().coerceIn(0, image.height - 1)
        val x1 = ((box.x + box.width) * scale).toInt().coerceIn(x0 + 1, image.width)
        val y1 = ((box.y + box.height) * scale).toInt().coerceIn(y0 + 1, image.height)
        var dark = 0
        for (py in y0 until y1) for (px in x0 until x1) if ((image.getRGB(px, py) and 0xFF) < 128) dark++
        return dark.toDouble() / ((x1 - x0) * (y1 - y0))
    }

    // --- what the panel shows -----------------------------------------------------------------------------

    @Test
    fun theEditorShowsEveryLineOfEveryPageExactlyAsTheCorrectedLayerSaysInOrder() {
        available()
        val editor = BookEditor(project::readPage, Dispatchers.IO)
        var lines = 0
        for (page in 1..165) {
            runBlocking { editor.show(page) }
            val expected = linesOf(page).map { it.text }
            assertEquals(expected, editor.lines.map { it.text }, "page $page")
            lines += expected.size
        }
        println("All 165 pages: $lines lines")
        assertTrue(lines > 4000, "a book of 165 pages has thousands of lines: $lines")
    }

    private fun androidx.compose.ui.test.ComposeUiTest.field(id: String): SemanticsNodeInteraction =
        onNode(hasTestTag("line text $id"), useUnmergedTree = true)

    private fun SemanticsNodeInteraction.text() = fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun openSampleInTheApp(block: androidx.compose.ui.test.ComposeUiTest.(AppModel) -> Unit) {
        val model = AppModel()
        runComposeUiTest {
            setContent { App(model, { project.folder }, { null }, Modifier.size(1100.dp, 800.dp)) }
            onNodeWithText("Open project…").performClick()
            waitUntil(
                timeoutMillis = 15_000,
            ) { runCatching { onNodeWithContentDescription("Page 1").assertExists() }.isSuccess }
            block(model)
        }
    }

    @Test
    fun thePanelShowsTheTextOfThePagesLookedAtInTheWholeWindow() {
        available()
        openSampleInTheApp { model ->
            for (page in listOf(2, 6, 50)) {
                runOnIdle { model.viewer!!.goTo(page) }
                waitUntil(timeoutMillis = 15_000) {
                    runCatching { onNodeWithContentDescription("Page $page").assertExists() }.isSuccess
                }
                waitUntil(timeoutMillis = 15_000) {
                    runCatching { field("l1").assertExists() }.isSuccess &&
                        model.editor!!.page == page
                }
                waitForIdle()
                val lines = linesOf(page)
                for (line in lines.take(8)) assertEquals(line.text, field(line.id).text(), "page $page, ${line.id}")
            }
        }
    }

    @Test
    fun bothPanelsAlwaysShowTheSamePageAfter50RandomPageChanges() {
        available()
        openSampleInTheApp { model ->
            val random = Random(7)
            repeat(50) {
                val page = random.nextInt(1, 166)
                runOnIdle { model.viewer!!.goTo(page) }
                waitUntil(timeoutMillis = 15_000) { model.editor!!.page == page && model.editor!!.layer != null }
                waitForIdle()
                val lines = linesOf(page)
                if (lines.isEmpty()) {
                    onNodeWithText("No text on this page.").assertExists()
                } else {
                    assertEquals(lines.first().text, field(lines.first().id).text(), "page $page")
                }
                assertEquals(page, model.viewer!!.page)
                assertEquals(page, model.editor!!.page)
            }
        }
    }
}
