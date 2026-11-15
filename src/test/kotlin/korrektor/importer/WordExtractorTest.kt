package korrektor.importer

import korrektor.pdf.PdfChar
import korrektor.pdf.PdfDocument
import korrektor.pdf.PdfRect
import korrektor.pdf.TestPage
import korrektor.pdf.TestPdfs
import korrektor.pdf.TestText
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WordExtractorTest {
    private val samplePdf = Path.of("samples", "Abhandlung_über_den_kleinen_Krieg.pdf")

    // --- hand-made characters: the rules one by one ------------------------------------------------------

    private val size = 10.0
    private val width = 5.0
    private val baseline = 100.0

    /** The characters of [text] side by side from [x], each [width] wide. */
    private fun run(
        text: String,
        x: Double,
        hyphenMarker: Boolean = false,
    ): List<PdfChar> =
        text.codePoints().toArray().mapIndexed { i, code ->
            val left = x + i * width
            PdfChar(
                code,
                PdfRect(left, baseline, left + width, baseline + size),
                false,
                hyphenMarker && i == text.length - 1,
                size,
            )
        }

    /** A space PDFium made up between two pieces: no width, at [x]. */
    private fun generatedSpace(x: Double) = PdfChar(0x20, PdfRect(x, baseline, x, baseline), true, false, 1.0)

    private fun lineBreak(x: Double) =
        listOf(0x0D, 0x0A).map { PdfChar(it, PdfRect(x, baseline, x, baseline), true, false, 1.0) }

    private fun texts(words: List<RawWord>) = words.map { it.text }

    @Test
    fun noCharactersGiveNoWords() {
        assertEquals(emptyList(), WordExtractor.split(emptyList()))
    }

    @Test
    fun onAPageWithoutRealSpacesEveryGeneratedSpaceIsAWordBoundary() {
        // Like Abhandlung: words are separate text pieces, PDFium makes the spaces.
        val chars = run("Vorrede.", 10.0) + generatedSpace(50.0) + run("5", 60.0) + lineBreak(65.0) + run("Man", 10.0)

        val words = WordExtractor.split(chars)

        assertEquals(listOf("Vorrede.", "5", "Man"), texts(words))
        assertEquals(PdfRect(10.0, baseline, 50.0, baseline + size), words[0].box)
        assertEquals(PdfRect(60.0, baseline, 65.0, baseline + size), words[1].box)
    }

    @Test
    fun aRealSpaceAlwaysSeparatesWords() {
        val chars = run("ab", 10.0) + run(" ", 20.0) + run("cd", 25.0)
        assertEquals(listOf("ab", "cd"), texts(WordExtractor.split(chars)))
    }

    @Test
    fun onAPageWithRealSpacesAGeneratedSpaceWithASmallGapDoesNotSeparate() {
        // Letter-spaced text: pieces with a gap of 0.3 of the font size, and a real space for the real word gap.
        val chars =
            run("S", 10.0) + generatedSpace(15.0) + run("tr", 18.0) + generatedSpace(28.0) + run("a", 31.0) +
                run(" ", 40.0) + run("neu", 45.0)

        assertEquals(listOf("Stra", "neu"), texts(WordExtractor.split(chars)))
    }

    @Test
    fun onAPageWithRealSpacesAGeneratedSpaceWithALargeGapSeparates() {
        // The gap between "ab" (ends at 20) and "cd" (starts at 26) is 6 points: 0.6 of the font size of 10.
        val chars = run("ab", 10.0) + generatedSpace(20.0) + run("cd", 26.0) + run(" ", 36.0) + run("ef", 41.0)
        assertEquals(listOf("ab", "cd", "ef"), texts(WordExtractor.split(chars)))

        val nearlyLarge = run("ab", 10.0) + generatedSpace(20.0) + run("cd", 25.9) + run(" ", 35.9) + run("ef", 41.0)
        assertEquals(listOf("abcd", "ef"), texts(WordExtractor.split(nearlyLarge)))
    }

    @Test
    fun aGeneratedLineBreakAlwaysSeparates() {
        val chars = run("ab", 10.0) + run(" ", 20.0) + run("cd", 25.0) + lineBreak(35.0) + run("ef", 10.0)
        assertEquals(listOf("ab", "cd", "ef"), texts(WordExtractor.split(chars)))
    }

    @Test
    fun theHyphenMarkerBecomesAHyphenAndEndsTheWord() {
        // PDFium's U+0002 with the hyphen flag, followed directly by the first character of the next line.
        val chars = run("Erin", 10.0) + run("\u0002", 30.0, hyphenMarker = true) + run("nerung", 10.0)

        val words = WordExtractor.split(chars)

        assertEquals(listOf("Erin-", "nerung"), texts(words))
        assertEquals(PdfRect(10.0, baseline, 35.0, baseline + size), words[0].box, "the box includes the hyphen")
    }

    @Test
    fun aPlainHyphenAndADoubleHyphenStayAtTheEndOfTheirWord() {
        val chars =
            run("Truppen-", 10.0) + generatedSpace(50.0) + run("dienst", 55.0) + lineBreak(85.0) + run("Reute=", 10.0) +
                lineBreak(40.0) +
                run("rey", 10.0)
        assertEquals(listOf("Truppen-", "dienst", "Reute=", "rey"), texts(WordExtractor.split(chars)))
    }

    @Test
    fun controlCharactersAreDroppedAndAWordLeftEmptyDisappears() {
        val chars =
            run("a\u0001b\u0000c", 10.0) + generatedSpace(35.0) + run("\u0001\u0002", 40.0) + generatedSpace(50.0) +
                run("d", 55.0)
        assertEquals(listOf("abc", "d"), texts(WordExtractor.split(chars)))
    }

    @Test
    fun specialCharactersComeThroughUnchanged() {
        // long s, u with a combining small e, a rotunda r, sharp s, and a character outside the BMP as one code point
        val word = "ſuͤꝛsß😀"
        val words = WordExtractor.split(run(word, 10.0))
        assertEquals(listOf(word), texts(words))
    }

    @Test
    fun surrogateHalvesFromPdfiumJoinIntoOneCharacter() {
        // On Windows PDFium reports a character outside the BMP as two UTF-16 halves, one entry each.
        val halves =
            listOf(0xD83D, 0xDE00).mapIndexed { i, unit ->
                PdfChar(
                    unit,
                    PdfRect(10.0 + i * width, baseline, 15.0 + i * width, baseline + size),
                    false,
                    false,
                    size,
                )
            }
        assertEquals(listOf("😀"), texts(WordExtractor.split(halves)))
    }

    @Test
    fun theBoxesOfGeneratedCharactersAreNotPartOfAWord() {
        // A generated space with a box far away must not stretch the word's box.
        val far = PdfChar(0x20, PdfRect(500.0, 900.0, 600.0, 950.0), true, false, 1.0)
        val words = WordExtractor.split(run("ab", 10.0) + far + run("cd", 30.0))
        assertEquals(listOf("ab", "cd"), texts(words))
        assertEquals(PdfRect(10.0, baseline, 20.0, baseline + size), words[0].box)
    }

    @Test
    fun theBoxIsTheUnionOfTheCharacterBoxes() {
        val tall = PdfChar('B'.code, PdfRect(15.0, 98.0, 20.0, 115.0), false, false, size)
        val words = WordExtractor.split(run("A", 10.0) + tall + run("C", 20.0))
        assertEquals(listOf(RawWord("ABC", PdfRect(10.0, 98.0, 25.0, 115.0))), words)
    }

    // --- generated PDFs -----------------------------------------------------------------------------------

    private fun withPdf(
        pages: List<TestPage>,
        block: (PdfDocument) -> Unit,
    ) {
        val dir = Files.createTempDirectory("words")
        try {
            val file = dir.resolve("a.pdf")
            TestPdfs.write(file, pages)
            PdfDocument.open(file).use(block)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    private fun wordsOfFirstPage(doc: PdfDocument): List<RawWord> = doc.page(0).use { WordExtractor.words(it.text()) }

    @Test
    fun wordsOfAGeneratedPageHaveTheRightTextAndPlaces() {
        val page =
            TestPage(texts = listOf(TestText("Hello world", 50f, 200f, 12f), TestText("second line", 50f, 180f, 12f)))
        withPdf(listOf(page)) { doc ->
            val words = wordsOfFirstPage(doc)

            assertEquals(listOf("Hello", "world", "second", "line"), texts(words))
            val hello = words[0].box
            assertEquals(50.0, hello.left, 1.0)
            assertTrue(hello.width in 20.0..35.0, "Hello is about 27 points wide: $hello")
            assertTrue(
                hello.bottom <= 201.0 && hello.top >= 207.0,
                "the box spans the baseline up to the letter height: $hello",
            )
            assertTrue(words[1].box.left > hello.right, "world starts after Hello")
            assertTrue(words[2].box.top < hello.bottom, "the second line is below the first")
        }
    }

    @Test
    fun wordsThatAreSeparateTextPiecesAreSeparatedByTheGap() {
        // Like the OCR layer of Abhandlung: no space character anywhere, one piece per word.
        val page =
            TestPage(
                texts =
                    listOf(
                        TestText("Vorrede.", 20f, 400f, 11f, invisible = true),
                        TestText("5", 120f, 400f, 10f, invisible = true),
                    ),
            )
        withPdf(listOf(page)) { doc ->
            assertEquals(listOf("Vorrede.", "5"), texts(wordsOfFirstPage(doc)))
        }
    }

    @Test
    fun invisibleTextIsExtractedLikeVisibleText() {
        withPdf(listOf(TestPage(texts = listOf(TestText("Hidden words", 30f, 150f, 10f, invisible = true))))) { doc ->
            assertEquals(listOf("Hidden", "words"), texts(wordsOfFirstPage(doc)))
        }
    }

    @Test
    fun specialCharactersOfAGeneratedPageComeThroughUnchanged() {
        val mapping = mapOf(0x80 to "ſ", 0x81 to "u", 0x82 to "ͤ", 0x83 to "😀", 0x84 to "ꝛ")
        val page =
            TestPage(
                texts = listOf(TestText("", 20f, 100f, 14f, codes = listOf(0x80, 0x81, 0x82, 0x84, 0x83))),
                toUnicode = mapping,
            )
        withPdf(listOf(page)) { doc ->
            assertEquals(listOf("ſuͤꝛ😀"), texts(wordsOfFirstPage(doc)))
        }
    }

    @Test
    fun aHyphenAtTheEndOfALineKeepsTheLinesApart() {
        // The text objects end with "Erin-" and go on with "nerung" on the next line: PDFium joins them with its
        // marker, and the extractor must give two words again.
        val page =
            TestPage(
                texts =
                    listOf(
                        TestText("Das ist eine Erin-", 20f, 220f, 12f),
                        TestText("nerung an die Zeit", 20f, 206f, 12f),
                        TestText("Reute=", 20f, 192f, 12f),
                        TestText("rey und", 20f, 178f, 12f),
                    ),
            )
        withPdf(listOf(page)) { doc ->
            val words = texts(wordsOfFirstPage(doc))

            assertEquals(
                listOf("Das", "ist", "eine", "Erin-", "nerung", "an", "die", "Zeit", "Reute=", "rey", "und"),
                words,
            )
            assertTrue(words.none { '\u0002' in it })
        }
    }

    @Test
    fun aPageWithoutTextGivesNoWords() {
        withPdf(listOf(TestPage())) { doc -> assertEquals(emptyList(), wordsOfFirstPage(doc)) }
    }

    // --- the sample ---------------------------------------------------------------------------------------

    @Test
    fun page6OfTheSampleStartsWithVorredeAndFive() {
        assumeTrue(samplePdf.exists(), "the sample PDF is not available")
        PdfDocument.open(samplePdf).use { doc ->
            val words = doc.page(5).use { WordExtractor.words(it.text()) }

            assertEquals(listOf("Vorrede.", "5", "Man", "erfordert", "von", "dem", "Soldat,"), texts(words.take(7)))
            // An independent count (PDFBox splits at whitespace the same way; it glues a few words together).
            val independent = Loader.loadPDF(samplePdf.toFile()).use { pdfbox -> pdfboxWordCount(pdfbox, 6) }
            assertTrue(abs(words.size - independent) <= 5, "words ${words.size} against $independent from PDFBox")
        }
    }

    private fun pdfboxWordCount(
        pdfbox: org.apache.pdfbox.pdmodel.PDDocument,
        page: Int,
    ): Int {
        val stripper = PDFTextStripper()
        stripper.sortByPosition = false
        stripper.startPage = page
        stripper.endPage = page
        return stripper.getText(pdfbox).split(Regex("\\s+")).count { it.isNotEmpty() }
    }

    @Test
    fun everyWordOfTheSampleIsCleanAndTheHyphenMarkersAreUndone() {
        assumeTrue(samplePdf.exists(), "the sample PDF is not available")
        PdfDocument.open(samplePdf).use { doc ->
            var total = 0
            val noText = mutableListOf<Int>()
            val problems = mutableListOf<String>()
            for (p in 0 until doc.pageCount) {
                val words = doc.page(p).use { WordExtractor.words(it.text()) }
                total += words.size
                if (words.isEmpty()) noText += p + 1
                problems +=
                    words
                        .filter { w ->
                            w.text.isEmpty() || w.text.any { it.isWhitespace() || it.code < 0x20 }
                        }.map { "p${p + 1}: '${it.text}'" }
            }
            assertEquals(emptyList(), problems, "no empty word, no whitespace or control character in a word")
            assertEquals(listOf(3, 139, 161, 163, 165), noText)
            assertTrue(total in 28_000..40_000, "the book has about 30,000 words: $total")

            // Page 18: `einKriegs` U+0002 `freund,` in PDFium's text.
            val page18 = doc.page(17).use { texts(WordExtractor.words(it.text())) }
            val at = page18.indexOf("einKriegs-")
            assertTrue(at >= 0 && page18[at + 1] == "freund,", "page 18 has einKriegs- and freund, as two words")
        }
    }
}
