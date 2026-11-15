package korrektor.importer

import korrektor.format.CanonicalJson
import korrektor.format.Metadata
import korrektor.format.PageCodec
import korrektor.format.PageLayer
import korrektor.format.Project
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The whole import of the sample book, checked against PDFBox as an independent reader of the same text layer
 * (S03). Every test is skipped when the sample PDF is missing, which is always the case on CI. The import runs
 * once and all tests read the same project.
 */
class ImportSampleTest {
    private val samplePdf = Path.of("samples", "Abhandlung_über_den_kleinen_Krieg.pdf")
    private val pagesWithoutText = listOf(3, 139, 161, 163, 165)

    private class Imported(
        val project: Project,
        val result: ImportResult,
    ) {
        fun original(page: Int): PageLayer = project.readOriginalPage(page)
    }

    private fun withImport(block: (Imported) -> Unit) {
        assumeTrue(samplePdf.exists(), "the sample PDF is not available")
        val dir = Files.createTempDirectory("importsample")
        try {
            val project =
                Project.create(
                    dir.resolve("sample.korrektur"),
                    samplePdf,
                    emptyList(),
                    Metadata("Abhandlung"),
                )
            val result = PdfImport.run(project)
            block(Imported(project, result))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    private fun words(layer: PageLayer) = layer.regions.flatMap { it.lines }.flatMap { it.words }

    // --- pages and sizes ----------------------------------------------------------------------------------

    @Test
    fun all165PagesAreImportedWithTheirOwnSizes() =
        withImport { imported ->
            assertEquals(165, imported.result.pageCount)
            assertEquals(165, imported.project.pages.size)
            Loader.loadPDF(samplePdf.toFile()).use { pdfbox ->
                for (n in 1..165) {
                    val (width, height) = visibleSize(pdfbox, n)
                    val layer = imported.original(n)
                    assertEquals(n, layer.pdfPage)
                    assertEquals(width, layer.width, 0.1, "width of page $n")
                    assertEquals(height, layer.height, 0.1, "height of page $n")
                }
            }
        }

    /** The visible page as PDFBox sees it: the crop box, turned if the page is rotated. */
    private fun visibleSize(
        pdfbox: PDDocument,
        n: Int,
    ): Pair<Double, Double> {
        val page = pdfbox.getPage(n - 1)
        val box = page.cropBox
        val turned = page.rotation % HALF_TURN != 0
        val (w, h) = box.width.toDouble() to box.height.toDouble()
        return CanonicalJson.round1(if (turned) h else w) to CanonicalJson.round1(if (turned) w else h)
    }

    @Test
    fun onlyThePagesWithoutTextAreEmptyAndTheyHaveTheNote() =
        withImport { imported ->
            val empty = (1..165).filter { imported.original(it).regions.isEmpty() }

            assertEquals(pagesWithoutText, empty)
            assertEquals(pagesWithoutText, imported.result.notes.map { it.page })
            for (n in 1..165) {
                val layer = imported.original(n)
                if (n in pagesWithoutText) {
                    assertEquals(PdfImport.NO_TEXT, layer.note, "page $n")
                } else {
                    assertEquals(null, layer.note, "page $n")
                    assertTrue(
                        layer.regions
                            .single()
                            .lines
                            .isNotEmpty(),
                        "page $n has lines",
                    )
                }
            }
        }

    // --- nothing is changed -------------------------------------------------------------------------------

    @Test
    fun theTextIsImportedAsItIsWithoutNormalizing() =
        withImport { imported ->
            val all = (1..165).flatMap { words(imported.original(it)) }.map { it.text }

            assertTrue(all.any { 'ſ' in it }, "the long s is kept")
            assertTrue(all.any { 'ß' in it }, "ß is kept")
            assertTrue(all.any { 'ä' in it }, "ä is kept")
            assertTrue(all.any { it.length > 1 && it.endsWith("-") }, "a hyphen at the end of a line is kept")
            assertTrue(all.any { it.length > 1 && it.endsWith("=") }, "the old double hyphen = is kept")
            assertTrue(all.none { it.isEmpty() || it.any(Char::isWhitespace) }, "words are never empty or spaced")
        }

    @Test
    fun theNoticePageIsImportedLikeAnyOtherPage() =
        withImport { imported ->
            val notice = imported.original(1)
            assertEquals(612.0 to 792.0, notice.width to notice.height)
            val lines = notice.regions.single().lines
            assertTrue(lines.size > 10, "the notice has many lines: ${lines.size}")
            assertEquals("Über dieses Buch", lines.first().text)
            assertEquals(null, notice.note)
        }

    @Test
    fun theCorrectedLayerStartsAsACopyOfTheOriginalOnEveryPage() =
        withImport { imported ->
            for (n in 1..165) {
                assertEquals(PdfImport.startingPoint(imported.original(n)), imported.project.readPage(n), "page $n")
            }
        }

    /**
     * Checked by eye against the rendered pages with the boxes drawn over them (T024): the first word of each of the
     * first twelve lines, top to bottom. A speck the OCR read as a word, like the `"` on page 50, starts its line when
     * it lies to the left of it; the text stays as the OCR gave it (V1).
     */
    @Test
    fun theLinesFollowThePrintedOrderOnPagesLookedAt() =
        withImport { imported ->
            val expected =
                mapOf(
                    6 to "Vorrede. Man er man mit ficier im Lorbeeren dat Freyheit zahlen auf",
                    50 to "49 Fünfter Von wie eines Reuterey halten er \" gegen befindenden ternehmungen",
                    100 to "Von Man einem Reuterey einen welche kommt; eine Feind cier das antworten",
                )
            for ((page, firstWords) in expected) {
                assertEquals(firstWords, firstWordsOf(imported.original(page)).joinToString(" "), "page $page")
            }
        }

    private fun firstWordsOf(layer: PageLayer) =
        layer.regions
            .flatMap { it.lines }
            .take(LINES_LOOKED_AT)
            .map { it.words.first().text }

    // --- the independent reader ---------------------------------------------------------------------------

    @Test
    fun wordTextsAndBoxesAgreeWithPdfBoxOnManyPages() =
        withImport { imported ->
            val checked = listOf(1, 2, 6, 7, 24, 36, 50, 100, 120, 160)
            Loader.loadPDF(samplePdf.toFile()).use { pdfbox ->
                for (n in checked) {
                    val characters = pdfboxCharacters(pdfbox, n)
                    val tolerance = if (n == NOTICE_PAGE) NOTICE_TOLERANCE else TOLERANCE
                    val layer = imported.original(n)
                    val problems = disagreements(words(layer), layer, characters, tolerance)
                    assertEquals(emptyList(), problems.take(MAX_REPORTED), "page $n: ${problems.size} problems")
                }
            }
        }

    /** One character of PDFBox: where it is on the displayed page, y downwards. */
    private class Glyph(
        val text: String,
        val left: Double,
        val right: Double,
        val baseline: Double,
    )

    private fun pdfboxCharacters(
        pdfbox: PDDocument,
        n: Int,
    ): List<Glyph> {
        val found = mutableListOf<Glyph>()
        val stripper =
            object : PDFTextStripper() {
                override fun writeString(
                    text: String,
                    textPositions: MutableList<TextPosition>,
                ) {
                    for (p in textPositions) {
                        val left = p.xDirAdj.toDouble()
                        val unicode = spellOutLigatures(p.unicode)
                        found += Glyph(unicode, left, left + p.widthDirAdj, p.yDirAdj.toDouble())
                    }
                }
            }
        stripper.startPage = n
        stripper.endPage = n
        stripper.getText(pdfbox)
        return found.filter { it.text.isNotBlank() }
    }

    /**
     * PDFBox returns the ligature characters of a font (ﬁ); PDFium spells them out (fi). Only these are expanded:
     * a general normalization would also turn the long s into s and hide what the import must keep.
     */
    private fun spellOutLigatures(text: String): String =
        when (text) {
            "ﬀ" -> "ff"
            "ﬁ" -> "fi"
            "ﬂ" -> "fl"
            "ﬃ" -> "ffi"
            "ﬄ" -> "ffl"
            else -> text
        }

    /**
     * For each imported word, the characters of PDFBox whose baseline lies at the bottom edge of the word's box and
     * whose middle lies between its left and right edge must spell the same word, and together span the same
     * width within a point. Words that reach beyond the page are skipped. Pages 140 and 156 have a letter drawn
     * twice, which PDFBox leaves out and PDFium keeps; they are not among the pages checked.
     */
    private fun disagreements(
        imported: List<korrektor.format.Word>,
        page: PageLayer,
        characters: List<Glyph>,
        tolerance: Double,
    ): List<String> {
        val problems = mutableListOf<String>()
        // Specks at the very edge of a scan (page 36 has a Hebrew 'word' at x = -1.7): the two readers cannot be
        // compared there, and the text stays in the import as it is (V1).
        val onPage = imported.filter { w -> w.box?.let { it.x >= 0 && it.x + it.width <= page.width } == true }
        for (word in onPage) {
            val box = word.box!!
            val bottom = box.y + box.height
            val inside =
                characters
                    .filter { (it.left + it.right) / 2 in box.x..(box.x + box.width) }
                    .filter { it.baseline in (bottom - BASELINE_SLACK)..(bottom + BASELINE_SLACK) }
                    .sortedBy { it.left }
            val spelled = inside.joinToString("") { it.text }
            when {
                spelled != word.text -> problems += "'${word.text}' at $box is '$spelled' for PDFBox"
                abs(inside.first().left - box.x) > tolerance ->
                    problems += "'${word.text}': left edge ${box.x} against ${inside.first().left}"
                abs(inside.last().right - (box.x + box.width)) > tolerance ->
                    problems += "'${word.text}': right edge ${box.x + box.width} against ${inside.last().right}"
            }
        }
        return problems
    }

    // --- the files ----------------------------------------------------------------------------------------

    @Test
    fun theImportedProjectReopensAndOpeningAndSavingChangesNoByte() =
        withImport { imported ->
            val folder = imported.project.folder
            val before = snapshot(folder)
            for (file in folder.resolve("pages").listDirectoryEntries()) {
                val text = file.readText()
                assertEquals(text, PageCodec.encode(PageCodec.decode(text)), file.name)
            }

            val reopened = Project.open(folder)
            assertEquals(165, reopened.pages.size)
            for (n in 1..165) {
                assertEquals(imported.original(n), reopened.readOriginalPage(n), "page $n original")
                reopened.writePage(n, reopened.readPage(n))
            }
            reopened.saveProjectFile()

            val after = snapshot(folder)
            assertEquals(before.keys, after.keys)
            for ((name, bytes) in before) assertContentEquals(bytes, after.getValue(name), name)
        }

    private fun snapshot(folder: Path): Map<String, ByteArray> =
        (listOf(folder.resolve("project.json")) + folder.resolve("pages").listDirectoryEntries())
            .associate { it.name to it.readBytes() }

    private companion object {
        const val HALF_TURN = 180
        const val LINES_LOOKED_AT = 12
        const val TOLERANCE = 1.0

        /**
         * The notice page has a real font. PDFBox's characters are as wide as the font's advance (with side
         * bearings), PDFium's boxes are as wide as the drawn glyph, so the edges differ by a little more.
         */
        const val NOTICE_PAGE = 1
        const val NOTICE_TOLERANCE = 2.0

        /**
         * The bottom of PDFium's box is the baseline (or, with a real font, the lowest point of the glyphs, a few
         * points below it). Lines of a page can overlap, so only characters near that edge belong to a word.
         */
        const val BASELINE_SLACK = 2.5
        const val MAX_REPORTED = 10
    }
}
