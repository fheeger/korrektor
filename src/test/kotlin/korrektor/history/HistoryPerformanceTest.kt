package korrektor.history

import korrektor.AppModel
import korrektor.closeProject
import korrektor.editor.BookEditor
import korrektor.editor.UndoResult
import korrektor.editor.redo
import korrektor.editor.undo
import korrektor.editor.unsaved
import korrektor.format.HistoryCodec
import korrektor.format.Metadata
import korrektor.format.Project
import korrektor.format.SavedEntry
import korrektor.importer.PdfImport
import korrektor.pdf.TestPdfs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Undo in a book of 1000 pages after a history of 100,000 steps (S08, N1): opening it, the first undo, many undos in a
 * row, and typing with that history loaded. Timing checks are flaky on shared CI machines, so they run on request
 * only: `KORREKTOR_PERF_TESTS=1`. The numbers are printed for the story report.
 */
class HistoryPerformanceTest {
    private fun enabled() =
        assumeTrue(System.getenv("KORREKTOR_PERF_TESTS") != null, "set KORREKTOR_PERF_TESTS=1 to run")

    private fun List<Long>.p95(): Long = sorted()[(size * 0.95).toInt().coerceAtMost(size - 1)]

    private inline fun <T> timed(block: () -> T): Pair<T, Long> {
        val start = System.nanoTime()
        val value = block()
        return value to (System.nanoTime() - start) / 1_000
    }

    /** Makes a history of [STEPS] steps with the real editor on pages in memory; each page is written once. */
    private fun makeHistory(project: Project): Long {
        val pagesInMemory = (1..1000).associateWith { project.readPage(it) }
        val editor = BookEditor({ pagesInMemory.getValue(it) }, Dispatchers.Unconfined)
        val random = Random(3)
        val (_, making) =
            timed {
                runBlocking {
                    repeat(STEPS) { i ->
                        editor.show(random.nextInt(1, 1001))
                        editor.setLineText("l1", "Schritt $i")
                        if (i % 2_000 == 1_999) {
                            project.historyLog.appendAll(editor.history.drainPending().map(HistoryCodec::encode))
                        }
                    }
                }
                project.historyLog.appendAll(editor.history.drainPending().map(HistoryCodec::encode))
                for ((page, layer) in editor.unsaved()) project.writePage(page, layer)
                project.historyLog.append(HistoryCodec.encode(SavedEntry(STEPS)))
            }
        return making
    }

    /** Undo and redo in a row, also back into history that is read on demand. */
    private fun measureUndo(loaded: BookEditor) {
        val (first, firstTime) = timed { loaded.undo() }
        assertIs<UndoResult.Done>(first)
        println("first undo: ${firstTime / 1_000.0} ms (page ${first.page}, never shown in this session)")
        assertTrue(firstTime < 50_000, "the first undo took ${firstTime / 1_000.0} ms")

        val undoTimes = List(100) { timed { loaded.undo() }.second }
        val redoTimes = List(101) { timed { loaded.redo() }.second }
        println("100 undos: p95 ${undoTimes.p95() / 1_000.0} ms, max ${undoTimes.max() / 1_000.0} ms")
        println("101 redos: p95 ${redoTimes.p95() / 1_000.0} ms, max ${redoTimes.max() / 1_000.0} ms")
        assertTrue(undoTimes.max() < 50_000 && redoTimes.max() < 50_000)

        val deep = List(300) { timed { loaded.undo() }.second }
        println("300 more undos (older history read on demand): max ${deep.max() / 1_000.0} ms")
        assertTrue(deep.max() < 200_000, "reading older history took ${deep.max() / 1_000.0} ms")
    }

    @Test
    fun openingUndoAndTypingWithAHistoryOf100000StepsOver1000Pages() {
        enabled()
        val dir = Files.createTempDirectory("historyperf")
        try {
            val pdf = dir.resolve("big.pdf")
            TestPdfs.book(pdf, 1000)
            val project = Project.create(dir.resolve("big.korrektur"), pdf, emptyList(), Metadata("Big"))
            PdfImport.run(project)

            // Without a history: the time to open, to compare with.
            val plain = AppModel()
            val (_, plainOpen) = timed { plain.openProject(project.folder) }
            plain.closeProject()

            val making = makeHistory(project)
            val size = Files.size(project.folder.resolve("history/book.jsonl")) / 1_000_000
            println("history of $STEPS steps: $size MB, made in ${making / 1_000} ms")

            val model = AppModel()
            val (_, open) = timed { model.openProject(project.folder) }
            assertTrue(model.error == null, model.error)
            val loaded = checkNotNull(model.editor)
            println("open: ${open / 1_000} ms with the history, ${plainOpen / 1_000} ms without")
            assertTrue(open < 1_000_000, "opening took ${open / 1_000} ms")
            val more = (open - plainOpen) / 1_000
            assertTrue(open - plainOpen < 300_000, "opening with the history took $more ms more")

            measureUndo(loaded)

            // Typing with the history loaded.
            runBlocking { loaded.show(1) }
            val typing = List(300) { i -> timed { loaded.setLineText("l1", "x".repeat(i % 20 + 1) + i) }.second }
            println("typing: p95 ${typing.p95() / 1_000.0} ms")
            assertTrue(typing.p95() < 5_000, "typing p95 ${typing.p95() / 1_000.0} ms")
            assertEquals(false, loaded.history.canRedo)
            model.closeProject()
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    private companion object {
        const val STEPS = 100_000
    }
}
