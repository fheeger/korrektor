package korrektor.importer

import korrektor.format.Box
import korrektor.pdf.PdfDocument
import korrektor.pdf.PdfRect
import korrektor.pdf.TestArea
import korrektor.pdf.TestPage
import korrektor.pdf.TestPdfs
import korrektor.pdf.TestText
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PageGeometryTest {
    private val abhandlung = Path.of("samples", "Abhandlung_über_den_kleinen_Krieg.pdf")
    private val clausewitz = Path.of("samples", "Clausewitz_Strategie.pdf")

    // --- the arithmetic, with numbers worked out by hand ---------------------------------------------------

    // A visible area 300 wide and 500 high, starting at (10, 20), and a rectangle 30 x 30 inside it.
    private val area = PdfRect(10.0, 20.0, 310.0, 520.0)
    private val rect = PdfRect(50.0, 100.0, 80.0, 130.0)

    @Test
    fun anUnrotatedPageHasItsOriginAtTheTopLeft() {
        val geometry = PageGeometry(area, 0)
        assertEquals(300.0 to 500.0, geometry.width to geometry.height)
        // 40 from the left edge; 520 - 130 = 390 from the top.
        assertEquals(Box(40.0, 390.0, 30.0, 30.0), geometry.toFormat(rect))
    }

    @Test
    fun aPageRotatedBy90DegreesIsShownOnItsSideWithTheTopOfThePageAtTheRight() {
        val geometry = PageGeometry(area, 90)
        assertEquals(500.0 to 300.0, geometry.width to geometry.height)
        assertEquals(Box(80.0, 40.0, 30.0, 30.0), geometry.toFormat(rect))
    }

    @Test
    fun aPageRotatedBy180DegreesIsUpsideDown() {
        val geometry = PageGeometry(area, 180)
        assertEquals(300.0 to 500.0, geometry.width to geometry.height)
        assertEquals(Box(230.0, 80.0, 30.0, 30.0), geometry.toFormat(rect))
    }

    @Test
    fun aPageRotatedBy270DegreesIsShownOnItsOtherSide() {
        val geometry = PageGeometry(area, 270)
        assertEquals(500.0 to 300.0, geometry.width to geometry.height)
        assertEquals(Box(390.0, 230.0, 30.0, 30.0), geometry.toFormat(rect))
    }

    @Test
    fun everyValueHasAtMostOneDecimal() {
        val geometry = PageGeometry(PdfRect(0.0, 0.01, 305.5199, 489.6100), 0)
        val box = geometry.toFormat(PdfRect(97.7123, 451.7456, 109.2987, 462.9321))
        for (value in listOf(box.x, box.y, box.width, box.height, geometry.width, geometry.height)) {
            assertEquals(value, (value * 10).roundToInt() / 10.0, "one decimal: $value")
        }
        assertEquals(305.5 to 489.6, geometry.width to geometry.height)
    }

    @Test
    fun aBoxIsNotClampedToThePage() {
        val geometry = PageGeometry(area, 0)
        val box = geometry.toFormat(PdfRect(-5.0, 500.0, 20.0, 540.0))
        assertEquals(Box(-15.0, -20.0, 25.0, 40.0), box)
    }

    @Test
    fun aRotationThatIsNotAQuarterTurnIsRefused() {
        assertFailsWith<IllegalArgumentException> { PageGeometry(area, 45) }
        assertFailsWith<IllegalArgumentException> { PageGeometry(PdfRect(0.0, 0.0, 0.0, 10.0), 0) }
    }

    // --- against a rendering of the page ------------------------------------------------------------------

    private val pixelsPerPoint = 4.0

    private fun withPdf(
        page: TestPage,
        block: (PdfDocument) -> Unit,
    ) {
        val dir = Files.createTempDirectory("geometry")
        try {
            val file = dir.resolve("a.pdf")
            TestPdfs.write(file, listOf(page))
            PdfDocument.open(file).use(block)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    private fun isDark(rgb: Int) = ((rgb shr 16 and 0xFF) + (rgb shr 8 and 0xFF) + (rgb and 0xFF)) / 3 < 128

    /** The share of dark pixels inside [box], which is in points. */
    private fun inkInside(
        image: BufferedImage,
        box: Box,
    ): Double {
        val left = (box.x * pixelsPerPoint).roundToInt().coerceIn(0, image.width)
        val top = (box.y * pixelsPerPoint).roundToInt().coerceIn(0, image.height)
        val right = ((box.x + box.width) * pixelsPerPoint).roundToInt().coerceIn(0, image.width)
        val bottom = ((box.y + box.height) * pixelsPerPoint).roundToInt().coerceIn(0, image.height)
        var dark = 0
        for (y in top until bottom) for (x in left until right) if (isDark(image.getRGB(x, y))) dark++
        val area = (right - left) * (bottom - top)
        return if (area == 0) 0.0 else dark.toDouble() / area
    }

    private fun inkOutside(
        image: BufferedImage,
        boxes: List<Box>,
        marginPoints: Double,
    ): Double {
        var dark = 0
        var outside = 0
        val margin = marginPoints * pixelsPerPoint
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (!isDark(image.getRGB(x, y))) continue
                dark++
                val covered =
                    boxes.any { b ->
                        x >= b.x * pixelsPerPoint - margin &&
                            x <= (b.x + b.width) * pixelsPerPoint + margin &&
                            y >= b.y * pixelsPerPoint - margin &&
                            y <= (b.y + b.height) * pixelsPerPoint + margin
                    }
                if (!covered) outside++
            }
        }
        return if (dark == 0) 1.0 else outside.toDouble() / dark
    }

    /** Whether every box holds ink and nearly all ink of the page lies in the boxes. */
    private fun boxesMatchInk(
        image: BufferedImage,
        boxes: List<Box>,
    ): Boolean = boxes.all { inkInside(image, it) >= 0.05 } && inkOutside(image, boxes, 3.0) <= 0.02

    private fun checkAgainstRendering(
        page: TestPage,
        expectedWords: List<String>,
    ) {
        withPdf(page) { doc ->
            doc.page(0).use { pdfPage ->
                val geometry = PageGeometry.of(pdfPage)
                assertEquals(pdfPage.width, geometry.width, 0.1, "the same width as PDFium's")
                assertEquals(pdfPage.height, geometry.height, 0.1, "the same height as PDFium's")

                val words = WordExtractor.words(pdfPage.text())
                assertEquals(expectedWords, words.map { it.text })
                val boxes = words.map { geometry.toFormat(it.box) }

                val image = pdfPage.render((geometry.width * pixelsPerPoint).roundToInt())
                assertTrue(
                    abs(image.height - geometry.height * pixelsPerPoint) <= 1.5,
                    "the image has the shape of the page",
                )
                assertTrue(boxesMatchInk(image, boxes), "the word boxes sit on the ink: $boxes")

                // The check itself must be able to fail: boxes mirrored top to bottom, or left to right, do not match.
                val mirroredVertically =
                    boxes.map {
                        Box(
                            it.x,
                            geometry.height - it.y - it.height,
                            it.width,
                            it.height,
                        )
                    }
                val mirroredHorizontally =
                    boxes.map {
                        Box(
                            geometry.width - it.x - it.width,
                            it.y,
                            it.width,
                            it.height,
                        )
                    }
                assertFalse(boxesMatchInk(image, mirroredVertically), "mirrored top to bottom must not match")
                assertFalse(boxesMatchInk(image, mirroredHorizontally), "mirrored left to right must not match")
            }
        }
    }

    // Two words in places that are not symmetric on the page, so a mirrored result cannot match by chance.
    private val words =
        listOf(TestText("MMMM", 20f, 230f, 40f), TestText("II", 110f, 80f, 30f))

    @Test
    fun boxesSitOnTheInkOnAPlainPage() =
        checkAgainstRendering(TestPage(200f, 300f, texts = words), listOf("MMMM", "II"))

    @Test
    fun boxesSitOnTheInkWhenTheMediaBoxDoesNotStartAtZero() {
        // Media box [10 20 210 320]: the same words, 10 and 20 further right and up.
        val moved = words.map { TestText(it.text, it.x + 10f, it.y + 20f, it.size) }
        checkAgainstRendering(TestPage(200f, 300f, texts = moved, area = TestArea(10f, 20f)), listOf("MMMM", "II"))
    }

    @Test
    fun boxesSitOnTheInkWhenTheCropBoxIsSmallerThanTheMediaBox() {
        // Media box 300 x 400; the visible area is [50 60 250 340], 200 x 280, and both words are inside it.
        val moved = listOf(TestText("MMMM", 70f, 270f, 40f), TestText("II", 160f, 100f, 30f))
        val page = TestPage(300f, 400f, texts = moved, area = TestArea(cropBox = listOf(50f, 60f, 250f, 340f)))
        checkAgainstRendering(page, listOf("MMMM", "II"))
    }

    @Test
    fun boxesSitOnTheInkOnRotatedPages() {
        for (rotation in listOf(90, 180, 270)) {
            checkAgainstRendering(TestPage(200f, 300f, rotation = rotation, texts = words), listOf("MMMM", "II"))
        }
    }

    @Test
    fun boxesSitOnTheInkOnARotatedPageWithAnOffsetMediaBoxAndACropBox() {
        val moved =
            listOf(TestText("MMMM", 70f, 270f, 40f), TestText("II", 160f, 100f, 30f)).map {
                TestText(it.text, it.x + 10f, it.y + 20f, it.size)
            }
        val page =
            TestPage(
                300f,
                400f,
                rotation = 90,
                texts = moved,
                area = TestArea(10f, 20f, listOf(60f, 80f, 260f, 360f)),
            )
        checkAgainstRendering(page, listOf("MMMM", "II"))
    }

    // --- the samples --------------------------------------------------------------------------------------

    @Test
    fun everyPageOfTheSampleHasTheSizeOfItsCropBox() {
        assumeTrue(abhandlung.exists(), "the sample PDF is not available")
        val problems =
            PdfDocument.open(abhandlung).use { doc ->
                Loader.loadPDF(abhandlung.toFile()).use { pdfbox -> pagesWithADifferentSize(doc, pdfbox) }
            }
        assertEquals(emptyList(), problems)
    }

    private fun pagesWithADifferentSize(
        doc: PdfDocument,
        pdfbox: PDDocument,
    ): List<String> =
        (0 until doc.pageCount).mapNotNull { p ->
            val geometry = doc.page(p).use { PageGeometry.of(it) }
            val crop = pdfbox.getPage(p).cropBox
            val differs = abs(geometry.width - crop.width) > 0.1 || abs(geometry.height - crop.height) > 0.1
            val found = "${geometry.width} x ${geometry.height}"
            if (differs) "page ${p + 1}: $found against ${crop.width} x ${crop.height}" else null
        }

    @Test
    fun theFirstWordOfPage6IsWhereTheContentStreamPutsIt() {
        assumeTrue(abhandlung.exists(), "the sample PDF is not available")
        PdfDocument.open(abhandlung).use { doc ->
            doc.page(5).use { page ->
                val geometry = PageGeometry.of(page)
                val first = WordExtractor.words(page.text()).first()
                val box = geometry.toFormat(first.box)
                // Drawn at (97.7, 451.7) with size 11 on a page 489.6 high: 489.6 - 463.0 from the top.
                assertEquals("Vorrede.", first.text)
                assertEquals(97.7, box.x, 0.3)
                assertEquals(26.6, box.y, 0.6)
                assertEquals(11.2, box.height, 1.0)
            }
        }
    }

    @Test
    fun theWordBoxesOfARealPageSitOnTheirPrintedWords() {
        assumeTrue(abhandlung.exists(), "the sample PDF is not available")
        PdfDocument.open(abhandlung).use { doc ->
            doc.page(5).use { page ->
                val geometry = PageGeometry.of(page)
                val boxes = WordExtractor.words(page.text()).map { geometry.toFormat(it.box) }
                val image = page.render((geometry.width * pixelsPerPoint).roundToInt())

                val withInk = boxes.count { inkInside(image, it) >= 0.05 }
                assertTrue(
                    withInk >= boxes.size * 0.9,
                    "$withInk of ${boxes.size} word boxes on page 6 contain printed ink",
                )
            }
        }
    }

    @Test
    fun theVisibleAreaOfTheSecondSampleStartsAHairOffZero() {
        assumeTrue(clausewitz.exists(), "the second sample PDF is not available")
        PdfDocument.open(clausewitz).use { doc ->
            doc.page(10).use { page ->
                val geometry = PageGeometry.of(page)
                assertEquals(page.width, geometry.width, 0.1)
                assertEquals(page.height, geometry.height, 0.1)
            }
        }
    }
}
