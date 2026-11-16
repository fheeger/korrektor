package korrektor.autosave

import korrektor.editor.AutoSaver
import korrektor.editor.BookEditor
import korrektor.format.Metadata
import korrektor.format.PageLayer
import korrektor.format.Project
import korrektor.importer.PdfImport
import korrektor.pdf.TestPdfs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Saving in a book of 1000 pages (S07, N1): how long a save takes, that typing is not slower while one runs, and that
 * only the changed pages are written. Timing checks are flaky on shared CI machines, so they run on request only:
 * `KORREKTOR_PERF_TESTS=1`.
 */
class AutosavePerformanceTest {
    private fun enabled() =
        assumeTrue(System.getenv("KORREKTOR_PERF_TESTS") != null, "set KORREKTOR_PERF_TESTS=1 to run")

    private fun List<Long>.p95(): Long = sorted()[(size * 0.95).toInt().coerceAtMost(size - 1)]

    private class Rig(
        val project: Project,
        val editor: BookEditor,
        val saver: AutoSaver,
        val written: MutableList<Int>,
    )

    private fun withBigBook(block: (Rig) -> Unit) {
        val dir = Files.createTempDirectory("autosaveperf")
        try {
            val pdf = dir.resolve("big.pdf")
            TestPdfs.book(pdf, 1000)
            val project = Project.create(dir.resolve("big.korrektur"), pdf, emptyList(), Metadata("Big"))
            PdfImport.run(project)
            val written = CopyOnWriteArrayList<Int>()
            val editor = BookEditor(project::readPage, Dispatchers.IO)
            val write: (Int, PageLayer) -> Unit = { page, layer ->
                written += page
                project.writePage(page, layer)
            }
            block(Rig(project, editor, AutoSaver(editor, Dispatchers.IO, write = write), written))
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    private fun Rig.change(pages: IntRange) {
        runBlocking {
            for (page in pages) {
                editor.show(page)
                editor.setLineText("l1", "geändert $page")
            }
        }
    }

    @Test
    fun savingOnePageAndFiftyPagesInABookOf1000Pages() {
        enabled()
        withBigBook { rig ->
            rig.change(500..500)
            val one = timed { runBlocking { rig.saver.saveNow() } }
            rig.change(100..149)
            val fifty = timed { runBlocking { rig.saver.saveNow() } }
            println(
                "save of 1 changed page: $one ms; of 50 changed pages: $fifty ms (${"%.1f".format(
                    fifty / 50.0,
                )} ms a page)",
            )
            assertTrue(one < 1_000, "one page took $one ms")
            assertTrue(fifty < 10_000, "fifty pages took $fifty ms")
        }
    }

    @Test
    fun typingIsNotSlowerWhileASaveOfManyPagesRuns() {
        enabled()
        withBigBook { rig ->
            rig.change(100..599) // a save that lasts long enough to type during it
            runBlocking { rig.editor.show(700) }

            fun edit(n: Int): Long {
                val started = System.nanoTime()
                rig.editor.setLineText("l1", "tippen $n")
                return (System.nanoTime() - started) / 1_000
            }
            repeat(200) { edit(it) } // warm up
            val quiet = List(500) { edit(it) }

            val during = mutableListOf<Long>()
            runBlocking {
                // Not on this thread: the loop below keeps it busy until the save is finished.
                val save = async(Dispatchers.Default) { rig.saver.saveNow() }
                var n = 1_000
                while (!save.isCompleted) {
                    during += edit(n++)
                    Thread.sleep(1)
                }
                save.await()
            }
            println(
                "typing without a save: 95th percentile ${quiet.p95()} µs, maximum ${quiet.max()} µs; " +
                    "during a save of 500 pages: 95th percentile ${during.p95()} µs, maximum ${during.max()} µs " +
                    "(${during.size} edits)",
            )
            assertTrue(during.size > 10, "edits were made while saving: ${during.size}")
            assertTrue(during.p95() < quiet.p95() + 20_000, "an edit is at most 20 ms slower while saving")
        }
    }

    @Test
    fun onlyTheChangedPagesAreWrittenWhateverTheSizeOfTheBook() {
        enabled()
        withBigBook { rig ->
            runBlocking {
                rig.editor.show(500)
                rig.editor.show(501)
                rig.editor.show(500)
            }
            assertEquals(emptyList(), rig.written, "looking at pages writes nothing")
            runBlocking { rig.saver.saveNow() }
            assertEquals(emptyList(), rig.written, "a save with nothing changed writes nothing")
            rig.editor.setLineText("l1", "geändert")
            runBlocking { rig.saver.saveNow() }
            assertEquals(listOf(500), rig.written, "999 of 1000 pages are not written")
        }
    }

    private fun timed(block: () -> Unit): Long {
        val started = System.nanoTime()
        block()
        return (System.nanoTime() - started) / 1_000_000
    }
}
