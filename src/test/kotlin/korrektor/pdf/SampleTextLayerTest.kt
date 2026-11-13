package korrektor.pdf

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The facts about the text layers of the two sample PDFs that the import relies on, see `docs/pdf-text-layer.md`.
 * If PDFium or a sample changes and one of these fails, that document and the import rules need a look.
 * Both tests are skipped when the sample is missing.
 */
class SampleTextLayerTest {
    private val abhandlung = Path.of("samples", "Abhandlung_über_den_kleinen_Krieg.pdf")
    private val clausewitz = Path.of("samples", "Clausewitz_Strategie.pdf")

    private class Tally {
        var chars = 0
        var generatedSpaces = 0
        var realSpaces = 0
        var generatedLineFeeds = 0
        var generatedCarriageReturns = 0
        var otherGenerated = 0
        var hyphens = 0
        var plainHyphens = 0
        var equalsSigns = 0
        var longS = 0
        var forbidden = mutableListOf<String>()
        val emptyPages = mutableListOf<Int>()
        val hyphenMarkerProblems = mutableListOf<String>()
    }

    private fun tally(doc: PdfDocument): Tally {
        val tally = Tally()
        for (p in 0 until doc.pageCount) {
            doc.page(p).use { page ->
                val text = page.text()
                if (text.charCount == 0) tally.emptyPages += p + 1
                for (i in 0 until text.charCount) {
                    val c = text.char(i)
                    tally.chars++
                    when {
                        c.generated && c.unicode == SPACE -> tally.generatedSpaces++
                        c.generated && c.unicode == LF -> tally.generatedLineFeeds++
                        c.generated && c.unicode == CR -> tally.generatedCarriageReturns++
                        c.generated -> tally.otherGenerated++
                        c.hyphen -> {
                            tally.hyphens++
                            checkHyphenMarker(tally, text, i, p + 1)
                        }
                        c.unicode == SPACE -> tally.realSpaces++
                        c.unicode == '-'.code -> tally.plainHyphens++
                        c.unicode == '='.code -> tally.equalsSigns++
                        c.unicode == LONG_S -> tally.longS++
                    }
                    val isControl = c.unicode < SPACE && c.unicode != LF && c.unicode != CR && c.unicode != TAB
                    if (!c.generated && (c.unicode == 0 || c.unicode == REPLACEMENT || (isControl && !c.hyphen))) {
                        tally.forbidden += "p${p + 1}:U+%04X".format(c.unicode)
                    }
                }
            }
        }
        return tally
    }

    /** PDFium's hyphen marker is U+0002, it stands for a hyphen, and the next character is on a lower line. */
    private fun checkHyphenMarker(
        tally: Tally,
        text: PdfTextPage,
        index: Int,
        pageNumber: Int,
    ) {
        val marker = text.char(index)
        if (marker.unicode !=
            MARKER
        ) {
            tally.hyphenMarkerProblems += "p$pageNumber: flagged character is U+%04X".format(marker.unicode)
        }
        if (index + 1 >= text.charCount) {
            tally.hyphenMarkerProblems += "p$pageNumber: the marker is the last character"
        } else {
            val next = text.char(index + 1)
            if (next.generated || next.box.top >= marker.box.top - MIN_LINE_DROP) {
                tally.hyphenMarkerProblems += "p$pageNumber: the character after the marker is not on a lower line"
            }
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
            assertTrue(
                tally.generatedSpaces > 30_000 && tally.generatedCarriageReturns > 2_000,
                "many spaces and line breaks",
            )
            assertEquals(emptyList(), tally.forbidden, "no U+0000, no replacement characters, no control characters")
            assertEquals(33, tally.equalsSigns, "the double hyphen '=' of the old print is in the text")
            assertEquals(65, tally.plainHyphens, "hyphens PDFium leaves as they are")
            assertEquals(10, tally.hyphens, "hyphens PDFium replaced by its marker")
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

                // Coordinates of the page itself: points, origin bottom-left. The content stream draws the first word at
                // (97.7, 451.7) with size 11.
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
                val words = StringBuilder()
                for (i in 0 until 40) {
                    words.appendCodePoint(
                        if (text.char(i).generated) ' '.code else text.char(i).unicode,
                    )
                }
                assertTrue(words.startsWith("Über dieses Buch"), "spaces come from the kerning gaps: $words")
            }
        }
    }

    @Test
    fun pdfiumSeparatesWordsTheOtherReaderGluedTogether() {
        assumeTrue(abhandlung.exists(), "the sample PDF is not available")
        PdfDocument.open(abhandlung).use { doc ->
            doc.page(6).use { page ->
                val text = page.text()
                val sb = StringBuilder()
                for (i in 0 until text.charCount) {
                    sb.appendCodePoint(
                        if (text.char(i).generated &&
                            text.char(i).unicode == SPACE
                        ) {
                            ' '.code
                        } else {
                            text.char(i).unicode
                        },
                    )
                }
                assertTrue(
                    "einem Scharmüzel" in sb,
                    "page 7 has the two words einem and Scharmüzel apart",
                )
            }
        }
    }

    @Test
    fun pdfiumsCharactersAgreeWithAnIndependentReaderOnAlmostEveryPage() {
        assumeTrue(abhandlung.exists(), "the sample PDF is not available")
        var agree = 0
        val disagree = mutableListOf<Int>()
        PdfDocument.open(abhandlung).use { doc ->
            Loader.loadPDF(abhandlung.toFile()).use { pdfbox ->
                for (n in 1..doc.pageCount) {
                    val mine = StringBuilder()
                    doc.page(n - 1).use { page ->
                        val text = page.text()
                        for (i in 0 until text.charCount) {
                            val c = text.char(i)
                            if (c.generated) continue
                            val unicode = if (c.hyphen) '-'.code else c.unicode
                            if (!Character.isWhitespace(unicode)) mine.appendCodePoint(unicode)
                        }
                    }
                    val stripper = PDFTextStripper()
                    stripper.sortByPosition = false
                    stripper.startPage = n
                    stripper.endPage = n
                    val other = stripper.getText(pdfbox).filter { !it.isWhitespace() }
                    // The same characters; the order of the text pieces may differ between the two readers.
                    if (mine.toString().toList().sorted() == other.toList().sorted()) agree++ else disagree += n
                }
            }
        }
        // Page 156 has one letter drawn twice in the same place, which PDFBox leaves out and PDFium keeps.
        assertEquals(listOf(156), disagree, "pages where the characters differ")
        assertEquals(164, agree)
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

            val offsets = (0 until doc.pageCount).map { p -> doc.page(p).use { it.bounds } }
            assertTrue(offsets.any { it.bottom != 0.0 }, "the visible area of some pages does not start at y = 0")
            assertTrue(offsets.all { kotlin.math.abs(it.left) < 0.05 && kotlin.math.abs(it.bottom) < 0.05 })
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
    }
}
