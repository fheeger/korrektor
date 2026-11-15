package korrektor.importer

import korrektor.format.FormatException
import korrektor.format.Metadata
import korrektor.format.PageCodec
import korrektor.format.PageInfo
import korrektor.format.PageLayer
import korrektor.format.Project
import korrektor.pdf.PdfException
import korrektor.pdf.TestPage
import korrektor.pdf.TestPdfs
import korrektor.pdf.TestText
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PdfImportTest {
    private val samplePdf = Path.of("samples", "Abhandlung_über_den_kleinen_Krieg.pdf")

    private fun withDir(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("pdfimport")
        try {
            block(dir)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    private fun projectFrom(
        dir: Path,
        pages: List<TestPage>,
    ): Project {
        val pdf = dir.resolve("book.pdf")
        TestPdfs.write(pdf, pages)
        return Project.create(dir.resolve("book.korrektur"), pdf, emptyList(), Metadata("Book"))
    }

    private val threePages =
        listOf(
            TestPage(texts = listOf(TestText("Hello world", 50f, 250f, 12f), TestText("second line", 50f, 230f, 12f))),
            TestPage(),
            TestPage(150f, 100f, texts = listOf(TestText("Page three", 20f, 50f, 12f))),
        )

    private fun lines(layer: PageLayer) =
        layer.regions.flatMap { region ->
            region.lines.map { line ->
                line.words.joinToString(" ") {
                    it.text
                }
            }
        }

    private fun pageFiles(project: Project) =
        project.folder
            .resolve("pages")
            .listDirectoryEntries()
            .map {
                it.name
            }.sorted()

    // --- what is written ----------------------------------------------------------------------------------

    @Test
    fun everyPageIsImportedAndWrittenInBothLayers() =
        withDir { dir ->
            val project = projectFrom(dir, threePages)

            val result = PdfImport.run(project)

            assertEquals(3, result.pageCount)
            assertEquals(listOf(PageInfo(1), PageInfo(2), PageInfo(3)), project.pages)
            assertEquals(
                listOf("0001.json", "0001.ocr.json", "0002.json", "0002.ocr.json", "0003.json", "0003.ocr.json"),
                pageFiles(project),
            )
            val first = project.readOriginalPage(1)
            assertEquals(listOf("Hello world", "second line"), lines(first))
            assertEquals(200.0 to 300.0, first.width to first.height)
            assertEquals(emptyList(), project.readOriginalPage(2).regions, "a page without text has no region")
            assertEquals(listOf("Page three"), lines(project.readOriginalPage(3)))
            assertEquals(150.0 to 100.0, project.readOriginalPage(3).let { it.width to it.height })
        }

    @Test
    fun theCorrectedLayerStartsAsACopyThatPointsToTheOriginalLines() =
        withDir { dir ->
            val project = projectFrom(dir, threePages)
            PdfImport.run(project)

            for (n in 1..3) {
                val original = project.readOriginalPage(n)
                val corrected = project.readPage(n)
                assertEquals(PdfImport.startingPoint(original), corrected, "page $n")
                assertTrue(
                    original.regions.flatMap { it.lines }.all { it.origin == null },
                    "the original has no origins",
                )
                assertTrue(
                    corrected.regions.flatMap { it.lines }.all { it.origin == it.id },
                    "every line points to itself",
                )
                assertEquals(original.regions.map { it.id }, corrected.regions.map { it.id }, "same region ids")
            }
        }

    @Test
    fun theFilesAreCanonicalSoOpeningAndSavingChangesNothing() =
        withDir { dir ->
            val project = projectFrom(dir, threePages)
            PdfImport.run(project)

            for (file in project.folder.resolve("pages").listDirectoryEntries()) {
                val text = file.readText()
                assertEquals(text, PageCodec.encode(PageCodec.decode(text)), file.name)
            }
            val projectJson = project.folder.resolve("project.json")
            val before = projectJson.readBytes()
            Project.open(project.folder).saveProjectFile()
            assertContentEquals(before, projectJson.readBytes())
            assertEquals(3, Project.open(project.folder).pages.size)
        }

    // --- progress and cancelling --------------------------------------------------------------------------

    @Test
    fun progressIsReportedOncePerPage() =
        withDir { dir ->
            val calls = mutableListOf<Pair<Int, Int>>()
            PdfImport.run(projectFrom(dir, threePages), progress = { done, total -> calls += done to total })
            assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), calls)
        }

    @Test
    fun cancellingStopsTheImportAndLeavesOnlyThePagesAlreadyWritten() =
        withDir { dir ->
            val project =
                projectFrom(dir, List(5) { i -> TestPage(texts = listOf(TestText("Page ${i + 1}", 20f, 100f, 12f))) })
            var done = 0

            val e =
                assertFailsWith<CancellationException> {
                    PdfImport.run(project, progress = { d, _ -> done = d }, isCancelled = { done >= 2 })
                }

            assertTrue("after 2 of 5" in e.message!!, e.message)
            assertEquals(5, project.pages.size, "the page list was set before the pages")
            assertEquals(listOf("0001.json", "0001.ocr.json", "0002.json", "0002.ocr.json"), pageFiles(project))
        }

    @Test
    fun anImportThatIsCancelledAtOnceWritesNoPage() =
        withDir { dir ->
            val project = projectFrom(dir, threePages)
            assertFailsWith<CancellationException> { PdfImport.run(project, isCancelled = { true }) }
            assertEquals(emptyList(), pageFiles(project))
        }

    // --- refusals -----------------------------------------------------------------------------------------

    @Test
    fun aSecondImportIsRefusedAndChangesNothing() =
        withDir { dir ->
            val project = projectFrom(dir, threePages)
            PdfImport.run(project)
            val original = project.folder.resolve("pages/0001.ocr.json").readBytes()

            assertFailsWith<FormatException> { PdfImport.run(project) }

            assertContentEquals(original, project.folder.resolve("pages/0001.ocr.json").readBytes())
            assertEquals(6, pageFiles(project).size)
        }

    @Test
    fun aPdfThatCannotBeOpenedFailsWithItsMessageAndChangesNothing() =
        withDir { dir ->
            val fake = dir.resolve("book.pdf").also { it.writeText("this is not a PDF at all") }
            val project = Project.create(dir.resolve("book.korrektur"), fake, emptyList(), Metadata("Book"))
            val before = project.folder.resolve("project.json").readBytes()

            val e = assertFailsWith<PdfException> { PdfImport.run(project) }

            assertTrue("not a PDF file" in e.message!!, e.message)
            assertEquals(emptyList(), project.pages)
            assertContentEquals(before, project.folder.resolve("project.json").readBytes())
            assertEquals(emptyList(), pageFiles(project))
        }

    // --- the sample ---------------------------------------------------------------------------------------

    @Test
    fun theSampleIsImportedWith165PagesAllReadable() {
        assumeTrue(samplePdf.exists(), "the sample PDF is not available")
        withDir { dir ->
            val project =
                Project.create(
                    dir.resolve("sample.korrektur"),
                    samplePdf,
                    emptyList(),
                    Metadata("Abhandlung"),
                )

            val started = System.nanoTime()
            val result = PdfImport.run(project)
            val seconds = (System.nanoTime() - started) / 1e9

            assertEquals(165, result.pageCount)
            val reopened = Project.open(project.folder)
            assertEquals(165, reopened.pages.size)
            val withoutText = mutableListOf<Int>()
            for (n in 1..165) {
                val original = reopened.readOriginalPage(n)
                assertEquals(PdfImport.startingPoint(original), reopened.readPage(n), "page $n")
                if (original.regions.isEmpty()) withoutText += n
            }
            assertEquals(listOf(3, 139, 161, 163, 165), withoutText)
            assertEquals(listOf("Vorrede. 5"), lines(reopened.readOriginalPage(6)).take(1))
            println("Import of the sample: ${"%.1f".format(seconds)} s for 165 pages")
        }
    }
}
