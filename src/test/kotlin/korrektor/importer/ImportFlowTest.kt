package korrektor.importer

import korrektor.CreationOutcome
import korrektor.CreationRequest
import korrektor.DefaultProjectFactory
import korrektor.NewProject
import korrektor.ProjectFactory
import korrektor.createProject
import korrektor.format.Metadata
import korrektor.format.Project
import korrektor.pdf.TestPage
import korrektor.pdf.TestPdfs
import korrektor.pdf.TestText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The flow from "New project" to the written pages, without the UI (`createProject` is what the dialog runs), and
 * the 1000-page run. The dialog's own handling of progress, cancel and failure is tested in `NewProjectModelTest`.
 */
class ImportFlowTest {
    private fun withDir(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("importflow")
        try {
            block(dir)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    private fun pdf(
        dir: Path,
        count: Int,
    ): Path {
        val path = dir.resolve("source.pdf")
        TestPdfs.write(
            path,
            List(count) { i ->
                if (i % 3 == 1) TestPage() else TestPage(texts = listOf(TestText("Page ${i + 1}", 30f, 200f, 12f)))
            },
        )
        return path
    }

    private fun request(
        dir: Path,
        source: Path,
    ) = CreationRequest(
        dir.resolve("projects").also {
            Files.createDirectories(it)
        },
        source,
        Metadata("Flow"),
        DefaultProjectFactory,
        Dispatchers.IO,
    )

    private fun createFlow(
        request: CreationRequest,
        cancelRequested: () -> Boolean = { false },
        progress: (Int, Int) -> Unit = { _, _ -> },
    ) = runBlocking { createProject(request, cancelRequested, progress) }

    private fun projectFolders(request: CreationRequest) = request.location.listDirectoryEntries().map { it.name }

    @Test
    fun creatingAProjectImportsEveryPageAndTheProjectReopens() =
        withDir { dir ->
            val request = request(dir, pdf(dir, 6))
            val steps = mutableListOf<Pair<Int, Int>>()

            val outcome = createFlow(request, progress = { done, total -> steps += done to total })

            val done = assertIs<CreationOutcome.Done>(outcome)
            assertEquals(6, done.result.pageCount)
            assertEquals(listOf(2, 5), done.result.notes.map { it.page })
            assertEquals((1..6).map { it to 6 }, steps)
            val reopened = Project.open(done.project.folder)
            assertEquals(6, reopened.pages.size)
            assertEquals("Flow", reopened.metadata?.title)
            for (n in 1..6) {
                assertEquals(reopened.readOriginalPage(n), done.project.readOriginalPage(n))
                assertEquals(PdfImport.startingPoint(reopened.readOriginalPage(n)), reopened.readPage(n))
            }
            assertEquals(
                listOf("Page 1"),
                reopened
                    .readOriginalPage(1)
                    .regions
                    .flatMap { it.lines }
                    .map { it.text },
            )
        }

    @Test
    fun cancellingWithARealPdfLeavesTheFolderAsItWas() =
        withDir { dir ->
            val request = request(dir, pdf(dir, 8))
            var done = 0

            val outcome = createFlow(request, cancelRequested = { done >= 3 }, progress = { d, _ -> done = d })

            assertIs<CreationOutcome.Cancelled>(outcome)
            assertEquals(emptyList(), projectFolders(request), "no project folder is left")
            assertEquals(listOf("projects", "source.pdf"), dir.listDirectoryEntries().map { it.name }.sorted())
        }

    @Test
    fun aPdfThatCannotBeReadFailsWithAMessageAndLeavesTheFolderAsItWas() =
        withDir { dir ->
            val fake = dir.resolve("source.pdf").also { it.writeText("%PDF-1.4\nthis is not a real PDF") }
            val request = request(dir, fake)

            val outcome = createFlow(request)

            val failed = assertIs<CreationOutcome.Failed>(outcome)
            assertTrue(failed.message.isNotBlank(), "there is a reason to show")
            assertEquals(emptyList(), projectFolders(request))
        }

    @Test
    fun aProjectCreatedAndImportedByHandIsTheSameAsOneFromTheFlow() =
        withDir { dir ->
            val source = pdf(dir, 4)
            val byHand =
                NewProject.create(
                    dir.resolve("a").also { Files.createDirectories(it) },
                    source,
                    Metadata("Flow"),
                )
            PdfImport.run(byHand)
            val flow = assertIs<CreationOutcome.Done>(createFlow(request(dir, source))).project

            for (n in 1..4) {
                assertEquals(byHand.readOriginalPage(n), flow.readOriginalPage(n), "page $n")
                assertEquals(byHand.readPage(n), flow.readPage(n), "corrected page $n")
            }
        }

    @Test
    fun aCancelWhileTheProjectFolderIsBeingMadeLeavesNoFolder() =
        withDir { dir ->
            val request = request(dir, pdf(dir, 3))
            lateinit var job: Job
            val factory =
                object : ProjectFactory by DefaultProjectFactory {
                    override fun create(
                        location: Path,
                        pdf: Path,
                        metadata: Metadata,
                    ): Project = DefaultProjectFactory.create(location, pdf, metadata).also { job.cancel() }
                }
            val cancelling = CreationRequest(request.location, request.pdf, request.metadata, factory, Dispatchers.IO)

            runBlocking {
                job = launch { createProject(cancelling, { false }, { _, _ -> }) }
                job.join()
            }

            assertEquals(emptyList(), projectFolders(request), "the project made a moment ago is deleted")
        }

    // --- 1000 pages ---------------------------------------------------------------------------------------

    private fun usedMegabytes(): Long {
        // The test measures what stays in memory, which needs a collection first.
        @Suppress("ExplicitGarbageCollectionCall")
        System.gc()
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MEGABYTE
    }

    @Test
    fun aBookOf1000PagesIsImportedWithProgressForEveryPageAndBoundedMemory() {
        // Timing and memory checks are flaky on shared CI machines: on request only (process.md).
        assumeTrue(System.getenv("KORREKTOR_PERF_TESTS") != null, "set KORREKTOR_PERF_TESTS=1 to run")
        withDir { dir ->
            val source = dir.resolve("big.pdf")
            TestPdfs.book(source, PAGES, blankEvery = BLANK_EVERY)
            val project =
                NewProject.create(
                    dir.resolve("projects").also { Files.createDirectories(it) },
                    source,
                    Metadata("Big"),
                )
            val done = mutableListOf<Int>()
            val used = mutableMapOf<Int, Long>()

            val started = System.nanoTime()
            val result =
                PdfImport.run(project, progress = { d, total ->
                    assertEquals(PAGES, total)
                    done += d
                    if (d % SAMPLE_EVERY == 0) used[d] = usedMegabytes()
                })
            val seconds = (System.nanoTime() - started) / 1e9

            assertEquals(PAGES, result.pageCount)
            assertEquals((1..PAGES).toList(), done, "progress for every page, in order")
            assertEquals(PAGES, Project.open(project.folder).pages.size)
            val blank = (1..PAGES).count { (it - 1) % BLANK_EVERY == 0 }
            assertEquals(blank, result.notes.size)
            // The importer holds one page at a time: memory after page 1000 is not far above memory after page 100.
            val growth = used.getValue(PAGES) - used.getValue(SAMPLE_EVERY)
            assertTrue(growth < MAX_GROWTH_MB, "memory grew by $growth MB between page $SAMPLE_EVERY and $PAGES: $used")
            println("Import of $PAGES pages: ${"%.1f".format(seconds)} s, memory (MB) by page: ${used.toSortedMap()}")
        }
    }

    private companion object {
        const val PAGES = 1000
        const val BLANK_EVERY = 7
        const val SAMPLE_EVERY = 100
        const val MAX_GROWTH_MB = 100L
        const val BYTES_PER_MEGABYTE = 1024L * 1024L
    }
}
