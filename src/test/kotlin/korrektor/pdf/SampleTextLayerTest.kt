package korrektor.pdf

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The facts about the text layers of the two sample PDFs that the import relies on, see `docs/pdf-text-layer.md`.
 * If PDFium or a sample changes and one of these fails, that document and the import rules need a look.
 * The tests are skipped when the sample is missing.
 */
class SampleTextLayerTest {
    private val abhandlung = Path.of("samples", "Abhandlung_über_den_kleinen_Krieg.pdf")
    private val clausewitz = Path.of("samples", "Clausewitz_Strategie.pdf")

    /** What a whole book contains, counted character by character. */
    private class Tally {
        var generatedSpaces = 0
        var realSpaces = 0
        var generatedLineFeeds = 0
        var generatedCarriageReturns = 0
        var otherGenerated = 0
        var hyphenMarkers = 0
        var plainHyphens = 0
        var equalsSigns = 0
        var longS = 0
        val forbidden = mutableListOf<String>()
        val emptyPages = mutableListOf<Int>()
        val hyphenMarkerProblems = mutableListOf<String>()
    }

    private fun tally(doc: PdfDocument): Tally {
        val tally = Tally()
        for (p in 0 until doc.pageCount) tallyPage(doc, p, tally)
        return tally
    }

    private fun tallyPage(
        doc: PdfDocument,
        index: Int,
        tally: Tally,
    ) {
        doc.page(index).use { page ->
            val text = page.text()
            if (text.charCount == 0) tally.emptyPages += index + 1
            for (i in 0 until text.charCount) {
                val c = text.char(i)
                if (c.generated) countGenerated(tally, c) else countReal(tally, text, i, index + 1)
                if (isForbidden(c)) tally.forbidden += "p${index + 1}:U+%04X".format(c.unicode)
            }
        }
    }

    private fun countGenerated(
        tally: Tally,
        c: PdfChar,
    ) {
        when (c.unicode) {
            SPACE -> tally.generatedSpaces++
            LF -> tally.generatedLineFeeds++
            CR -> tally.generatedCarriageReturns++
            else -> tally.otherGenerated++
        }
    }

    private fun countReal(
        tally: Tally,
        text: PdfTextPage,
        index: Int,
        pageNumber: Int,
    ) {
        val c = text.char(index)
        when {
            c.hyphen -> {
                tally.hyphenMarkers++
                checkHyphenMarker(tally, text, index, pageNumber)
            }
            c.unicode == SPACE -> tally.realSpaces++
            c.unicode == '-'.code -> tally.plainHyphens++
            c.unicode == '='.code -> tally.equalsSigns++
            c.unicode == LONG_S -> tally.longS++
        }
    }

    /** Characters the import would have to drop or that point to a broken layer. None occur in the samples. */
    private fun isForbidden(c: PdfChar): Boolean {
        val controlCharacter = c.unicode < SPACE && c.unicode !in WHITESPACE
        return !c.generated && !c.hyphen && (c.unicode == 0 || c.unicode == REPLACEMENT || controlCharacter)
    }

    /** PDFium's hyphen marker is U+0002, it stands for a hyphen, and the next character is on a lower line. */
    private fun checkHyphenMarker(
        tally: Tally,
        text: PdfTextPage,
        index: Int,
        pageNumber: Int,
    ) {
        val marker = text.char(index)
        if (marker.unicode != MARKER) {
            tally.hyphenMarkerProblems += "p$pageNumber: flagged character is U+%04X".format(marker.unicode)
        }
        val next = if (index + 1 < text.charCount) text.char(index + 1) else null
        if (next == null || next.generated || next.box.top >= marker.box.top - MIN_LINE_DROP) {
            tally.hyphenMarkerProblems += "p$pageNumber: the character after the marker is not on a lower line"
        }
    }

    // --- Abhandlung -------------------------------------------------------------------------------------

    @Test
    fun abhandlungHasTheStructureTheImportAssumes() {
        assumeTrue(abhandlung.exists(), "the sample PDF is not available")
        PdfDocument.open(abhandlung).use { doc ->
            assertEquals(165, doc.pageCount)
            val tally = tally(doc)

            assertEquals(listOf(3, 139, 161, 163, 165), tally.emptyPages, "pages without text")
            assertEquals(0, tally.realSpaces, "the layer has no space characters: PDFium generates every space")
            assertEquals(0, tally.otherGenerated, "PDFium generates only spaces and line breaks")
            assertEquals(tally.generatedCarriageReturns, tally.generatedLineFeeds, "line breaks come as CR LF pairs")
            assertTrue(tally.generatedSpaces > 30_000, "many generated spaces: ${tally.generatedSpaces}")
            assertTrue(tally.generatedCarriageReturns > 2_000, "many generated line breaks")
            assertEquals(emptyList(), tally.forbidden, "no U+0000, no replacement characters, no control characters")
            assertEquals(33, tally.equalsSigns, "the double hyphen '=' of the old print is in the text")
            assertEquals(65, tally.plainHyphens, "hyphens PDFium leaves as they are")
            assertEquals(10, tally.hyphenMarkers, "hyphens PDFium replaced by its marker")
            assertEquals(emptyList(), tally.hyphenMarkerProblems)
            assertEquals(94, tally.longS, "the long s is returned as such")
        }
    }

    @Test
    fun theFirstCharactersOfPage6AreWhatTheContentStreamSays() {
        assumeTrue(abhandlung.exists(), "the sample PDF is not available")
        PdfDocument.open(abhandlung).use { doc ->
            doc.page(5).use { page ->
                val text = page.text()
                val first = (0 until 20).map { text.char(it) }
                assertEquals("Vorrede.", first.take(8).joinToString("") { it.unicode.toChar().toString() })
                assertTrue(first[8].generated && first[8].unicode == SPACE, "a generated space between the two pieces")
                assertEquals('5'.code, first[9].unicode)
                assertTrue(
                    first[10].generated && first[10].unicode == CR && first[11].unicode == LF,
                    "then a line break",
                )
                assertEquals("Man", (12..14).joinToString("") { first[it].unicode.toChar().toString() })

                // Coordinates of the page itself: points, origin bottom-left. The content stream draws
                // the first word at (97.7, 451.7) with size 11.
                val v = first[0]
                assertEquals(97.7, v.box.left, 0.1)
                assertEquals(451.7, v.box.bottom, 0.1)
                assertEquals(11.0, v.fontSize, 0.01)
                assertTrue(v.box.top in 462.0..463.5, "the box is about the font size high: ${v.box}")
            }
        }
    }

    @Test
    fun theNoticePageHasARealTextLayerWithGeneratedSpaces() {
        assumeTrue(abhandlung.exists(), "the sample PDF is not available")
        PdfDocument.open(abhandlung).use { doc ->
            doc.page(0).use { page ->
                assertEquals(612.0, page.width, 0.01)
                assertEquals(792.0, page.height, 0.01)
                val text = page.text()
                assertTrue(text.charCount > 3_000, "the notice page has text")
                assertTrue(pageString(text).startsWith("Über dieses Buch"), "spaces come from the kerning gaps")
            }
        }
    }

    @Test
    fun pdfiumSeparatesWordsTheOtherReaderGluedTogether() {
        assumeTrue(abhandlung.exists(), "the sample PDF is not available")
        PdfDocument.open(abhandlung).use { doc ->
            doc.page(6).use { page ->
                val sentence = pageString(page.text())
                assertTrue("einem Scharmüzel" in sentence, "page 7 has the two words einem and Scharmüzel apart")
            }
        }
    }

    /** The text of a page with each generated character shown as a space or line break, as a reader sees it. */
    private fun pageString(text: PdfTextPage): String {
        val sb = StringBuilder()
        for (i in 0 until text.charCount) {
            val c = text.char(i)
            sb.appendCodePoint(if (c.generated && c.unicode == CR) SPACE else c.unicode)
        }
        return sb.toString().replace("\n", "")
    }

    @Test
    fun pdfiumsCharactersAgreeWithAnIndependentReaderOnAlmostEveryPage() {
        assumeTrue(abhandlung.exists(), "the sample PDF is not available")
        val disagree =
            PdfDocument.open(abhandlung).use { doc ->
                Loader.loadPDF(abhandlung.toFile()).use { pdfbox -> pagesWhereTheReadersDisagree(doc, pdfbox) }
            }
        // Page 156 has one letter drawn twice in the same place, which PDFBox leaves out and PDFium keeps.
        assertEquals(listOf(156), disagree, "pages where the characters differ")
    }

    /** The same characters; the order of the text pieces may differ between the two readers. */
    private fun pagesWhereTheReadersDisagree(
        doc: PdfDocument,
        pdfbox: PDDocument,
    ): List<Int> =
        (1..doc.pageCount).filter { n ->
            pdfiumCharacters(doc, n).toList().sorted() != pdfboxCharacters(pdfbox, n).toList().sorted()
        }

    /** The real characters of page [n] without whitespace, with PDFium's hyphen marker turned back into `-`. */
    private fun pdfiumCharacters(
        doc: PdfDocument,
        n: Int,
    ): String {
        val sb = StringBuilder()
        doc.page(n - 1).use { page ->
            val text = page.text()
            for (i in 0 until text.charCount) {
                val c = text.char(i)
                val unicode = if (c.hyphen) '-'.code else c.unicode
                if (!c.generated && !Character.isWhitespace(unicode)) sb.appendCodePoint(unicode)
            }
        }
        return sb.toString()
    }

    private fun pdfboxCharacters(
        pdfbox: PDDocument,
        n: Int,
    ): String {
        val stripper = PDFTextStripper()
        stripper.sortByPosition = false
        stripper.startPage = n
        stripper.endPage = n
        return stripper.getText(pdfbox).filter { !it.isWhitespace() }
    }

    // --- Clausewitz -------------------------------------------------------------------------------------

    @Test
    fun clausewitzHasRealSpacesAndPageBoxesThatDoNotStartAtZero() {
        assumeTrue(clausewitz.exists(), "the second sample PDF is not available")
        PdfDocument.open(clausewitz).use { doc ->
            assertEquals(99, doc.pageCount)
            val tally = tally(doc)
            assertEquals(listOf(1, 2, 3, 4, 8, 38, 94, 99), tally.emptyPages, "pages without text")
            assertTrue(tally.realSpaces > 25_000, "this layer has space characters: ${tally.realSpaces}")
            assertTrue(
                tally.generatedSpaces in 1 until tally.realSpaces / 10,
                "and PDFium generates few: ${tally.generatedSpaces}",
            )

            val areas = (0 until doc.pageCount).map { p -> doc.page(p).use { it.bounds } }
            assertTrue(areas.any { it.bottom != 0.0 }, "the visible area of some pages does not start at y = 0")
            assertTrue(areas.all { abs(it.left) < 0.05 && abs(it.bottom) < 0.05 }, "but only by a hair")
        }
    }

    private companion object {
        const val SPACE = 0x20
        const val LF = 0x0A
        const val CR = 0x0D
        const val TAB = 0x09
        const val MARKER = 0x02
        const val REPLACEMENT = 0xFFFD
        const val LONG_S = 0x17F
        const val MIN_LINE_DROP = 5.0
        val WHITESPACE = setOf(LF, CR, TAB)
    }
}
