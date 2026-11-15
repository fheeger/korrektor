package korrektor.importer

import korrektor.format.Metadata
import korrektor.format.PageCodec
import korrektor.format.PageLayer
import korrektor.format.Project
import korrektor.pdf.TestPage
import korrektor.pdf.TestPdfs
import korrektor.pdf.TestText
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The special pages S03 has to survive, as small generated PDFs: rotated, with several columns, with only a
 * picture, with invisible text, with unusual Unicode, with a broken page. Each case is one PDF with a few pages.
 */
class ImportGeneratedPdfsTest {
    private fun withDir(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("importgenerated")
        try {
            block(dir)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    private fun importPages(
        dir: Path,
        pages: List<TestPage>,
        picturePages: Set<Int> = emptySet(),
        brokenPages: Set<Int> = emptySet(),
    ): Pair<Project, ImportResult> {
        val pdf = dir.resolve("book.pdf")
        TestPdfs.write(pdf, pages, picturePages = picturePages, brokenPages = brokenPages)
        val project = Project.create(dir.resolve("book.korrektur"), pdf, emptyList(), Metadata("Book"))
        return project to PdfImport.run(project)
    }

    private fun lineTexts(layer: PageLayer) = layer.regions.flatMap { it.lines }.map { it.text }

    // --- rotation -----------------------------------------------------------------------------------------

    @Test
    fun aRotatedPageIsImportedAsTheUserSeesIt() =
        withDir { dir ->
            // 200 x 300 points turned by 90 degrees clockwise: it is shown 300 wide and 200 high. The text is turned
            // with the page, so it runs downwards at the right side of the displayed page.
            val page = TestPage(texts = listOf(TestText("Turned", 40f, 260f, 12f)), rotation = 90)
            val (project, _) = importPages(dir, listOf(page))

            val layer = project.readOriginalPage(1)

            assertEquals(300.0 to 200.0, layer.width to layer.height)
            val word =
                layer.regions
                    .single()
                    .lines
                    .single()
                    .words
                    .single()
            assertEquals("Turned", word.text)
            val box = word.box!!
            assertTrue(box.x + box.width <= 300.0 && box.y + box.height <= 200.0, "inside the page: $box")
            assertTrue(box.x > 240.0, "near the right edge: $box")
            assertTrue(box.y < 60.0, "near the top: $box")
            assertTrue(box.height > box.width, "the word runs downwards, so its box is tall and narrow: $box")
        }

    // --- columns ------------------------------------------------------------------------------------------

    @Test
    fun twoColumnsAreImportedAndTheirRowsAreMixedAsDocumented() =
        withDir { dir ->
            val left = listOf("alpha one", "alpha two", "alpha three")
            val right = listOf("beta one", "beta two", "beta three")
            val texts =
                left.mapIndexed { i, t -> TestText(t, 15f, 250f - 20f * i, 10f) } +
                    right.mapIndexed { i, t -> TestText(t, 110f, 250f - 20f * i, 10f) }
            val (project, result) = importPages(dir, listOf(TestPage(texts = texts)))

            assertEquals(emptyList(), result.notes)
            // Known limitation until layout detection (B3, B4): one region, and every printed row is one line
            // across both columns.
            assertEquals(
                listOf("alpha one beta one", "alpha two beta two", "alpha three beta three"),
                lineTexts(project.readOriginalPage(1)),
            )
        }

    // --- text without a visible shape ---------------------------------------------------------------------

    @Test
    fun invisibleTextIsImportedLikeVisibleText() =
        withDir { dir ->
            val page =
                TestPage(
                    texts =
                        listOf(
                            TestText("hidden OCR", 20f, 200f, 12f, invisible = true),
                            TestText("shown", 20f, 180f, 12f),
                        ),
                )
            val (project, _) = importPages(dir, listOf(page))

            assertEquals(listOf("hidden OCR", "shown"), lineTexts(project.readOriginalPage(1)))
        }

    @Test
    fun aPageWithOnlyAPictureIsAnEmptyPageWithTheNote() =
        withDir { dir ->
            val (project, result) = importPages(dir, listOf(TestPage()), picturePages = setOf(0))

            assertEquals(listOf(PageNote(1, PdfImport.NO_TEXT)), result.notes)
            assertEquals(emptyList(), project.readOriginalPage(1).regions)
            assertEquals(PdfImport.NO_TEXT, project.readPage(1).note)
        }

    // --- unusual Unicode ----------------------------------------------------------------------------------

    /** Text in which each distinct glyph is one single-byte code that the page's ToUnicode map spells as given. */
    private fun unicodePage(words: List<List<String>>): TestPage {
        val glyphs = words.flatten().distinct()
        val codeOf = glyphs.withIndex().associate { (i, glyph) -> glyph to FIRST_CODE + i }
        val texts =
            words.mapIndexed { i, word ->
                TestText(word.joinToString(""), 20f, 250f - 20f * i, 12f, codes = word.map { codeOf.getValue(it) })
            }
        return TestPage(texts = texts, toUnicode = codeOf.entries.associate { (glyph, code) -> code to glyph })
    }

    @Test
    fun unusualUnicodeIsImportedUnchangedAndSurvivesTheFiles() =
        withDir { dir ->
            val long = listOf("ſ", "t", "r", "a", "ß", "e")
            val accent = listOf("uͤ", "b", "e", "r") // u with a small e above, as in old German print
            val emoji = listOf("a", "😀", "b") // a character outside the Basic Multilingual Plane
            val hebrew = listOf("ש", "ל", "ו", "ם")
            val latinExtended = listOf("ꝛ", "c", ".") // the old abbreviation sign "ꝛc."
            val (project, result) =
                importPages(dir, listOf(unicodePage(listOf(long, accent, emoji, hebrew, latinExtended))))

            assertEquals(emptyList(), result.notes)
            // PDFium puts a run of right-to-left letters into logical order, so a word drawn left to right comes
            // out reversed. It happens only to the foreign-script specks of the OCR; the letters are all there.
            val expected = listOf("ſtraße", "uͤber", "a😀b", "םולש", "ꝛc.")
            assertEquals(expected, lineTexts(project.readOriginalPage(1)))
            for (file in project.folder.resolve("pages").listDirectoryEntries()) {
                val text = file.readText()
                assertEquals(text, PageCodec.encode(PageCodec.decode(text)), file.name)
            }
            assertEquals(lineTexts(project.readOriginalPage(1)), lineTexts(Project.open(project.folder).readPage(1)))
        }

    // --- all of it together -------------------------------------------------------------------------------

    @Test
    fun aBookWithEveryKindOfSpecialPageIsImportedCompletely() =
        withDir { dir ->
            val plain = TestPage(texts = listOf(TestText("Plain page", 20f, 250f, 12f)))
            val pages =
                listOf(
                    plain,
                    TestPage(), // no text
                    TestPage(texts = listOf(TestText("Turned", 40f, 260f, 12f)), rotation = 90),
                    TestPage(texts = listOf(TestText("left", 15f, 250f, 10f), TestText("right", 110f, 250f, 10f))),
                    TestPage(), // only a picture
                    TestPage(texts = listOf(TestText("invisible", 20f, 100f, 12f, invisible = true))),
                    unicodePage(listOf(listOf("ſ", "ß"))),
                    TestPage(texts = listOf(TestText("Broken", 20f, 100f, 12f))), // content cut off
                    plain,
                )
            val (project, result) = importPages(dir, pages, picturePages = setOf(4), brokenPages = setOf(7))

            assertEquals(9, result.pageCount)
            assertEquals(9, Project.open(project.folder).pages.size)
            for (n in 1..9) {
                val original = project.readOriginalPage(n)
                assertEquals(PdfImport.startingPoint(original), project.readPage(n), "page $n")
                val text = Project.open(project.folder).let { it.readOriginalPage(n) }
                assertEquals(original, text, "page $n after reopening")
            }
            assertEquals(listOf("Plain page"), lineTexts(project.readOriginalPage(1)))
            assertEquals(listOf("left right"), lineTexts(project.readOriginalPage(4)))
            assertEquals(listOf("ſß"), lineTexts(project.readOriginalPage(7)))
            assertEquals(listOf("Plain page"), lineTexts(project.readOriginalPage(9)))
            assertNull(project.readOriginalPage(1).note)
            assertTrue(2 in result.notes.map { it.page } && 5 in result.notes.map { it.page }, "${result.notes}")
            assertEquals(
                project.folder
                    .resolve("pages")
                    .listDirectoryEntries()
                    .map { it.name }
                    .sorted(),
                (1..9).flatMap { listOf("%04d.json".format(it), "%04d.ocr.json".format(it)) }.sorted(),
            )
        }

    private companion object {
        /** The first single-byte code used for a glyph; 0x41 is 'A', which is not a space or a control code. */
        const val FIRST_CODE = 0x41
    }
}
