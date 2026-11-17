package korrektor.search

import korrektor.editor.BookEditor
import korrektor.editor.UndoResult
import korrektor.editor.undo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Searching and replacing in a book of 1000 pages (T089, N1): the results come in batches while it searches, a cancel
 * is immediate, and a stand-in for the UI thread keeps running. What is checked always: the counts, the batches, the
 * cancel and the gaps of the UI. The limits on the time only with `KORREKTOR_PERF_TESTS=1` (a CI machine is slow).
 */
class SearchPerfTest {
    private val timing = System.getenv("KORREKTOR_PERF_TESTS") == "1"
    private val pages = 1000

    private class Ui {
        val executor = Executors.newSingleThreadExecutor { Thread(it, "fake-ui").apply { isDaemon = true } }
        val dispatcher = executor.asCoroutineDispatcher()
    }

    private fun ticker(
        ui: Ui,
        longest: LongArray,
        until: CompletableDeferred<Unit>,
    ) = CoroutineScope(ui.dispatcher).launch {
        var last = System.nanoTime()
        while (!until.isCompleted) {
            delay(5)
            val now = System.nanoTime()
            longest[0] = maxOf(longest[0], (now - last) / 1_000_000)
            last = now
        }
    }

    @Test
    fun aRareWordAndACommonWordAreFoundInABookOf1000PagesWhileTheUiKeepsRunning() =
        withBigBook(pages) { project ->
            val ui = Ui()
            val editor = BookEditor(project::readPage, Dispatchers.Unconfined)
            val model = SearchModel(editor, pages, ui.dispatcher, Dispatchers.IO, pause = 0)
            val longest = LongArray(1)
            val done = CompletableDeferred<Unit>()
            val tick = ticker(ui, longest, done)
            try {
                for ((text, expected) in listOf("Zebra" to pages, "der" to null)) {
                    val sizes = ArrayList<Int>()
                    val started = System.nanoTime()
                    model.change(SearchQuery(text, matchCase = true, wholeWords = true))
                    runBlocking {
                        withTimeout(60_000) {
                            while (model.searching) {
                                sizes += model.results.size
                                delay(2)
                            }
                        }
                    }
                    val millis = (System.nanoTime() - started) / 1_000_000
                    println("search '$text' in $pages pages: ${model.results.size} hits in $millis ms")
                    if (expected != null) assertEquals(expected, model.results.size)
                    assertTrue(model.results.size >= pages, "at least one hit on every page")
                    assertEquals(pages, model.pagesWithHits)
                    assertEquals(pages, model.pagesDone)
                    assertTrue(sizes.distinct().size > 3, "the hits came in several batches: ${sizes.distinct().size}")
                    assertEquals(model.results.map { it.page }.sorted(), model.results.map { it.page }, "in page order")
                    if (timing) assertTrue(millis < 10_000, "took $millis ms")
                }
            } finally {
                done.complete(Unit)
                runBlocking { tick.join() }
            }
            println("the fake UI thread waited at most ${longest[0]} ms between two ticks of 5 ms")
            if (timing) assertTrue(longest[0] < 500, "the UI thread was blocked for ${longest[0]} ms")
            ui.executor.shutdownNow()
        }

    @Test
    fun cancellingABookSearchIsImmediateAndKeepsTheHitsSoFar() =
        withBigBook(pages) { project ->
            val editor = BookEditor(project::readPage, Dispatchers.Unconfined)
            val model = SearchModel(editor, pages, Dispatchers.Default, Dispatchers.IO, pause = 0)
            model.change(SearchQuery("der"))
            runBlocking { withTimeout(30_000) { while (model.results.isEmpty()) delay(1) } }
            val started = System.nanoTime()
            model.cancel()
            val millis = (System.nanoTime() - started) / 1_000_000
            assertFalse(model.searching)
            val kept = model.results.size
            runBlocking { delay(300) }
            assertEquals(kept, model.results.size, "nothing arrives after the cancel")
            println("cancel after $kept hits took $millis ms")
            assertTrue(millis < 500, "cancel took $millis ms")
        }

    @Test
    fun replacing1000HitsOn1000PagesIsOneStepAndUndoPutsAllBack() =
        withBigBook(pages) { project ->
            val editor = BookEditor(project::readPage, Dispatchers.Unconfined)
            val model = SearchModel(editor, pages, Dispatchers.Default, Dispatchers.IO, pause = 0)
            model.change(SearchQuery("Zebra", matchCase = true))
            runBlocking { withTimeout(60_000) { while (model.searching) delay(2) } }
            assertEquals(pages, model.results.size)
            model.replacement = "Esel"

            val started = System.nanoTime()
            runBlocking { withContext(Dispatchers.Default) { model.replaceSelected() } }
            val millis = (System.nanoTime() - started) / 1_000_000
            println("replacing $pages hits on $pages pages took $millis ms")
            assertEquals("Replaced $pages matches on $pages pages.", model.outcome)
            assertEquals(pages, editor.changedPages.size)
            assertEquals(1, editor.history.undoSteps.size)
            assertTrue(model.results.isEmpty())
            if (timing) assertTrue(millis < 15_000, "took $millis ms")

            val undone = System.nanoTime()
            assertIs<UndoResult.Done>(editor.undo())
            println("undoing it took ${(System.nanoTime() - undone) / 1_000_000} ms")
            assertEquals(pages, runBlocking { searchAgain(editor, "Zebra") })
            assertEquals(0, runBlocking { searchAgain(editor, "Esel") })
        }

    private suspend fun searchAgain(
        editor: BookEditor,
        text: String,
    ): Int {
        val model = SearchModel(editor, pages, Dispatchers.Default, Dispatchers.IO, pause = 0)
        model.change(SearchQuery(text, matchCase = true))
        withTimeout(60_000) { while (model.searching) delay(2) }
        return model.results.size.also { model.close() }
    }
}
