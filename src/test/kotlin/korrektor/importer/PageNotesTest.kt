package korrektor.importer

import korrektor.format.Metadata
import korrektor.format.Project
import korrektor.pdf.PdfException
import korrektor.pdf.TestPage
import korrektor.pdf.TestPdfs
import korrektor.pdf.TestText
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PageNotesTest {
    private val samplePdf = Path.of("samples", "Abhandlung_über_den_kleinen_Krieg.pdf")

    private fun withDir(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("pagenotes")
        try {
            block(dir)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    private fun projectFrom(
        dir: Path,
        pages: List<TestPage>,
        picturePages: Set<Int> = emptySet(),
        brokenPages: Set<Int> = emptySet(),
    ): Project {
        val pdf = dir.resolve("book.pdf")
        TestPdfs.write(pdf, pages, picturePages = picturePages, brokenPages = brokenPages)
        return Project.create(dir.resolve("book.korrektur"), pdf, emptyList(), Metadata("Book"))
    }

    private fun text(line: String) = TestPage(texts = listOf(TestText(line, 40f, 200f, 12f)))

    private fun firstLine(
        project: Project,
        page: Int,
    ) = project
        .readOriginalPage(page)
        .regions
        .flatMap { it.lines }
        .firstOrNull()
        ?.words
        ?.joinToString(" ") { it.text }

    // --- no text -----------------------------------------------------------------------------------------

    @Test
    fun aPageWithoutTextBecomesAnEmptyPageWithANoteInBothLayers() =
        withDir { dir ->
            val project = projectFrom(dir, listOf(text("First page"), TestPage(), text("Third page")))

            val result = PdfImport.run(project)

            assertEquals(3, result.pageCount)
            assertEquals(listOf(PageNote(2, "No text on this page.")), result.notes)
            for (layer in listOf(project.readOriginalPage(2), project.readPage(2))) {
                assertEquals(emptyList(), layer.regions)
                assertEquals("No text on this page.", layer.note)
            }
            assertNull(project.readOriginalPage(1).note)
            assertNull(project.readPage(3).note)
            assertEquals("First page", firstLine(project, 1))
            assertEquals("Third page", firstLine(project, 3))
        }

    @Test
    fun aPageWithOnlyAPictureOrOnlySpacesIsTreatedTheSame() =
        withDir { dir ->
            val pages =
                listOf(TestPage(), TestPage(texts = listOf(TestText("     ", 20f, 100f, 12f))), text("Real text"))
            val project = projectFrom(dir, pages, picturePages = setOf(0))

            val result = PdfImport.run(project)

            assertEquals(listOf(1, 2), result.notes.map { it.page })
            assertTrue(result.notes.all { it.text == "No text on this page." })
            assertEquals(emptyList(), project.readOriginalPage(1).regions)
            assertEquals(emptyList(), project.readOriginalPage(2).regions)
            assertEquals("Real text", firstLine(project, 3))
        }

    @Test
    fun theNoteSurvivesOpeningAndSavingTheProject() =
        withDir { dir ->
            val project = projectFrom(dir, listOf(TestPage(), text("Text")))
            PdfImport.run(project)

            val reopened = Project.open(project.folder)

            assertEquals("No text on this page.", reopened.readOriginalPage(1).note)
            assertEquals("No text on this page.", reopened.readPage(1).note)
        }

    // --- broken pages ------------------------------------------------------------------------------------

    @Test
    fun aPageWithABrokenContentStreamDoesNotStopTheImport() =
        withDir { dir ->
            val project =
                projectFrom(dir, listOf(text("Good one"), text("Bad one"), text("Good two")), brokenPages = setOf(1))

            val result = PdfImport.run(project)

            assertEquals(3, result.pageCount)
            assertEquals("Good one", firstLine(project, 1))
            assertEquals("Good two", firstLine(project, 3))
            // The broken page is either read as far as it goes, or an empty page with a note: both are readable.
            val broken = project.readOriginalPage(2)
            assertTrue(broken.regions.isNotEmpty() || broken.note != null, "page 2: ${broken.regions} / ${broken.note}")
            assertEquals(project.readOriginalPage(2).note, project.readPage(2).note)
        }

    @Test
    fun aPageThatCannotBeLoadedBecomesAnEmptyPageWithThePreviousSizeAndANote() =
        withDir { dir ->
            val project = projectFrom(dir, listOf(text("One"), TestPage(150f, 100f), text("Three")))

            val result =
                PdfImport.run(project, openPage = { pdf, index ->
                    if (index == 1) throw PdfException("Page 2 of the PDF cannot be loaded") else pdf.page(index)
                })

            val layer = project.readOriginalPage(2)
            assertEquals(emptyList(), layer.regions)
            assertEquals(200.0 to 300.0, layer.width to layer.height, "the size of page 1 is used")
            val note = layer.note!!
            assertTrue("could not be read" in note && "Page 2 of the PDF cannot be loaded" in note, note)
            assertTrue("size of the previous page" in note, note)
            assertEquals(listOf(2), result.notes.map { it.page })
            assertEquals("One", firstLine(project, 1))
            assertEquals("Three", firstLine(project, 3))
            assertEquals(layer.note, project.readPage(2).note)
        }

    @Test
    fun aPageThatCannotBeLoadedAtTheStartUsesA4() =
        withDir { dir ->
            val project = projectFrom(dir, listOf(text("One"), text("Two")))

            PdfImport.run(project, openPage = { pdf, index -> if (index == 0) error("broken") else pdf.page(index) })

            val layer = project.readOriginalPage(1)
            assertEquals(595.3 to 841.9, layer.width to layer.height)
            assertTrue("broken" in layer.note!!, layer.note)
            assertEquals("Two", firstLine(project, 2))
        }

    @Test
    fun aPageWhoseTextCannotBeReadKeepsItsOwnSize() =
        withDir { dir ->
            val project =
                projectFrom(
                    dir,
                    listOf(text("One"), TestPage(150f, 100f, texts = listOf(TestText("Two", 20f, 50f))), text("Three")),
                )

            // A page that is closed before its text is read: the page's size is known, its text is not.
            val result =
                PdfImport.run(project, openPage = { pdf, index -> pdf.page(index).also { if (index == 1) it.close() } })

            val layer = project.readOriginalPage(2)
            assertEquals(150.0 to 100.0, layer.width to layer.height, "the page's own size")
            val note = layer.note!!
            assertTrue("could not be read" in note && "is closed" in note, note)
            assertTrue("previous page" !in note, note)
            assertEquals(listOf(2), result.notes.map { it.page })
            assertEquals("Three", firstLine(project, 3))
        }

    // --- the sample --------------------------------------------------------------------------------------

    @Test
    fun theSampleHasNotesOnExactlyThePagesWithoutText() {
        assumeTrue(samplePdf.exists(), "the sample PDF is not available")
        withDir { dir ->
            val project =
                Project.create(
                    dir.resolve("sample.korrektur"),
                    samplePdf,
                    emptyList(),
                    Metadata("Abhandlung"),
                )

            val result = PdfImport.run(project)

            assertEquals(listOf(3, 139, 161, 163, 165), result.notes.map { it.page })
            assertTrue(result.notes.all { it.text == "No text on this page." })
            val reopened = Project.open(project.folder)
            for (n in 1..165) {
                val original = reopened.readOriginalPage(n)
                val expected = if (n in result.notes.map { it.page }) "No text on this page." else null
                assertEquals(expected, original.note, "page $n")
                assertEquals(expected, reopened.readPage(n).note, "page $n, corrected layer")
            }
        }
    }
}
