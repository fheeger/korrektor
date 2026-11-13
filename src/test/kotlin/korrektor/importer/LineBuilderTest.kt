package korrektor.importer

import korrektor.format.Box
import korrektor.format.Region
import korrektor.format.RegionType
import korrektor.pdf.PdfDocument
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LineBuilderTest {
    private val samplePdf = Path.of("samples", "Abhandlung_über_den_kleinen_Krieg.pdf")

    private fun word(
        text: String,
        x: Double,
        y: Double,
        width: Double = 20.0,
        height: Double = 10.0,
    ) = PlacedWord(text, Box(x, y, width, height))

    private fun lineTexts(region: Region?) = region!!.lines.map { line -> line.words.joinToString(" ") { it.text } }

    // --- order -------------------------------------------------------------------------------------------

    @Test
    fun noWordsGiveNoRegion() {
        assertNull(LineBuilder.build(emptyList()))
    }

    @Test
    fun wordsInAnyOrderComeOutAsLinesFromTopToBottomAndWordsFromLeftToRight() {
        val words =
            listOf(
                word("e", 40.0, 30.0),
                word("a", 10.0, 10.0),
                word("d", 10.0, 30.0),
                word("c", 70.0, 10.0),
                word("b", 40.0, 10.0),
            )

        assertEquals(listOf("a b c", "d e"), lineTexts(LineBuilder.build(words)))
        assertEquals(lineTexts(LineBuilder.build(words)), lineTexts(LineBuilder.build(words.shuffled(Random(7)))))
        assertEquals(lineTexts(LineBuilder.build(words)), lineTexts(LineBuilder.build(words.reversed())))
    }

    @Test
    fun aLineWhoseBaselineDriftsAcrossThePageStaysOneLine() {
        // A skewed scan: over 240 points the line sinks by 7 points, and the next line is 13 points below it.
        fun row(
            name: String,
            top: Double,
        ) = (0 until 12).map { i -> word("$name$i", 10.0 + i * 20.0, top + i * 0.6) }
        val words = row("a", 10.0) + row("b", 23.0) + row("c", 36.0)

        val lines = LineBuilder.build(words.shuffled(Random(3)))!!.lines

        assertEquals(3, lines.size)
        assertEquals(
            listOf("a", "b", "c"),
            lines.map {
                it.words
                    .first()
                    .text
                    .take(1)
            },
        )
        assertTrue(lines.all { it.words.size == 12 })
    }

    @Test
    fun linesThatAreCloseTogetherStayApart() {
        // Boxes 12 high with only 11 between the tops: they overlap by one twelfth.
        val words =
            listOf(
                word("a", 10.0, 10.0, height = 12.0),
                word("b", 40.0, 10.0, height = 12.0),
                word("c", 10.0, 21.0, height = 12.0),
            )
        assertEquals(listOf("a b", "c"), lineTexts(LineBuilder.build(words)))
    }

    @Test
    fun wordsOfDifferentSizesOnOneLineStayTogether() {
        // `Vorrede.` (11 high) and the page number `5` (10 high, 2.7 lower), as at the top of page 6 of the sample.
        val words = listOf(word("Vorrede.", 97.7, 26.7, 83.8, 11.2), word("5", 238.6, 29.4, 9.4, 10.2))
        assertEquals(listOf("Vorrede. 5"), lineTexts(LineBuilder.build(words)))
    }

    // --- the region -------------------------------------------------------------------------------------

    @Test
    fun theRegionHoldsAllLinesWithTheirIdsAndBoxes() {
        val words =
            listOf(
                word("a", 10.0, 10.0, 20.0, 10.0),
                word("b", 40.0, 12.0, 25.0, 9.0),
                word("c", 15.0, 30.0, 30.0, 10.0),
            )

        val region = LineBuilder.build(words)!!

        assertEquals("r1", region.id)
        assertEquals(RegionType.TEXT, region.type)
        assertEquals(1, region.order)
        assertEquals(listOf("l1", "l2"), region.lines.map { it.id })
        assertEquals(Box(10.0, 10.0, 55.0, 11.0), region.lines[0].box)
        assertEquals(Box(15.0, 30.0, 30.0, 10.0), region.lines[1].box)
        assertEquals(Box(10.0, 10.0, 55.0, 30.0), region.box)
    }

    @Test
    fun wordsAndTheirBoxesAreNotChanged() {
        val words =
            listOf(
                word("ſo", 10.0, 10.0, 12.3, 9.8),
                word("uͤber", 30.0, 10.5, 40.4, 9.5),
                word("Kriegsfüh-", 10.0, 30.0, 51.0, 10.2),
            )
        val region = LineBuilder.build(words)!!
        val out = region.lines.flatMap { line -> line.words }.map { PlacedWord(it.text, it.box!!) }
        assertEquals(words.sortedWith(compareBy({ it.box.y }, { it.box.x })), out)
    }

    @Test
    fun boxesOfTheRegionAndLinesHaveOneDecimalEvenIfTheSumsDoNot() {
        // 0.1 + 0.2 is not exactly 0.3 in floating point arithmetic.
        val region = LineBuilder.build(listOf(word("a", 0.1, 0.1, 0.2, 0.2), word("b", 0.3, 0.1, 0.4, 0.2)))!!
        assertEquals(Box(0.1, 0.1, 0.6, 0.2), region.lines.single().box)
    }

    @Test
    fun aStrayWordThatSharesTooLittleWithAnyLineGetsALineOfItsOwn() {
        // Noise from a scan shadow at the left margin, between two text lines: it overlaps each by a fifth at most.
        val words =
            listOf(
                word("a", 30.0, 10.0),
                word("b", 60.0, 10.0),
                word("x", 2.0, 18.0, 8.0, 10.0),
                word("c", 30.0, 26.0),
                word("d", 60.0, 26.0),
            )
        assertEquals(listOf("a b", "x", "c d"), lineTexts(LineBuilder.build(words)))
    }

    // --- the known limitation ---------------------------------------------------------------------------

    @Test
    fun aPageWithTwoColumnsDoesNotFailAndItsRowsAreMixedAsTheStoryNames() {
        // Left column x 10..100, right column x 150..240; three rows. Until layout detection (B3, B4) the rows
        // of both columns are one line each: this is the known limitation, and this test documents it.
        val words =
            (0 until 3).flatMap { row ->
                listOf(
                    word("L${row}a", 10.0, 10.0 + row * 15.0),
                    word("L${row}b", 40.0, 10.0 + row * 15.0),
                    word(
                        "R${row}a",
                        150.0,
                        10.0 + row * 15.0,
                    ),
                    word("R${row}b", 180.0, 10.0 + row * 15.0),
                )
            }

        val lines = lineTexts(LineBuilder.build(words))

        assertEquals(listOf("L0a L0b R0a R0b", "L1a L1b R1a R1b", "L2a L2b R2a R2b"), lines)
    }

    // --- the sample -------------------------------------------------------------------------------------

    /** The words of a sample page and the region built from them. */
    private class PageResult(
        val region: Region?,
        val wordCount: Int,
    )

    private fun importPage(
        doc: PdfDocument,
        number: Int,
    ): PageResult =
        doc.page(number - 1).use { page ->
            val geometry = PageGeometry.of(page)
            val placed = WordExtractor.words(page.text()).map { PlacedWord(it.text, geometry.toFormat(it.box)) }
            PageResult(LineBuilder.build(placed), placed.size)
        }

    private fun medianOf(region: Region) =
        region.lines.map { line ->
            val centres = line.words.map { it.box!!.y + it.box!!.height / 2 }.sorted()
            centres[centres.size / 2]
        }

    @Test
    fun page6OfTheSampleHasThirtyLinesAndStartsWithVorredeAndFive() {
        assumeTrue(samplePdf.exists(), "the sample PDF is not available")
        PdfDocument.open(samplePdf).use { doc ->
            val region = assertNotNull(importPage(doc, 6).region)

            assertEquals("Vorrede. 5", lineTexts(region).first())
            assertEquals("Man erfordert von dem Soldat, daß", lineTexts(region)[1])
            // A heading, 19 lines, a gap, 9 lines and the signature line: counted on the rendered page.
            assertEquals(30, region.lines.size)
            val centres = medianOf(region)
            assertTrue(centres.zipWithNext().all { (a, b) -> a < b }, "the lines go from top to bottom: $centres")
        }
    }

    @Test
    fun everyPageOfTheSampleHasItsLinesInOrderAndKeepsAllWords() {
        assumeTrue(samplePdf.exists(), "the sample PDF is not available")
        val problems = PdfDocument.open(samplePdf).use { doc -> (1..doc.pageCount).flatMap { problemsOfPage(doc, it) } }
        assertEquals(emptyList(), problems)
    }

    private fun problemsOfPage(
        doc: PdfDocument,
        n: Int,
    ): List<String> {
        val result = importPage(doc, n)
        val region = result.region ?: return if (result.wordCount == 0) emptyList() else listOf("page $n: no region")
        val problems = mutableListOf<String>()
        if (!medianOf(region).zipWithNext().all { (a, b) -> a <= b }) problems += "page $n: lines out of order"
        if (region.lines.any { line -> line.words.zipWithNext().any { (a, b) -> a.box!!.x > b.box!!.x } }) {
            problems += "page $n: words out of order"
        }
        if (region.lines.sumOf { it.words.size } != result.wordCount) problems += "page $n: words lost or added"
        return problems
    }

    @Test
    fun theLineCountsAgreeWithAnIndependentReaderOnMostPages() {
        assumeTrue(samplePdf.exists(), "the sample PDF is not available")
        // (ours, PDFBox's) for every page
        val counts =
            PdfDocument.open(samplePdf).use { doc ->
                Loader.loadPDF(samplePdf.toFile()).use { pdfbox ->
                    (1..doc.pageCount).map { n ->
                        (importPage(doc, n).region?.lines?.size ?: 0) to
                            pdfboxLineCount(pdfbox, n)
                    }
                }
            }
        val moreThanPdfbox =
            counts
                .withIndex()
                .filter { (_, c) ->
                    c.first > c.second
                }.map { "p${it.index + 1}: ${it.value}" }
        val withinTwo = counts.count { (ours, theirs) -> abs(ours - theirs) <= 2 }

        // PDFBox groups the text into lines by position with its own, stricter method, and it splits more: at words of
        // another size, and at the stray words of the left margin that scan shadows leave in the OCR text. So it finds
        // as many lines as the builder or more on every page, and the two are within two lines on nearly all pages.
        // (PDFium's own line breaks are no help here: it breaks a line at only about one line end in three.)
        assertEquals(emptyList(), moreThanPdfbox, "pages where the builder finds more lines than PDFBox")
        assertTrue(withinTwo >= WITHIN_TWO_LINES, "pages within two lines of PDFBox: $withinTwo of ${counts.size}")
    }

    private fun pdfboxLineCount(
        pdfbox: PDDocument,
        page: Int,
    ): Int {
        val stripper = PDFTextStripper()
        stripper.sortByPosition = true
        stripper.startPage = page
        stripper.endPage = page
        return stripper.getText(pdfbox).lines().count { it.isNotBlank() }
    }

    private companion object {
        const val WITHIN_TWO_LINES = 140
    }
}
