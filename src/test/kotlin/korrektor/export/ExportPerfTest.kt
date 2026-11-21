package korrektor.export

import korrektor.search.withBigBook
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.util.concurrent.Executors
import kotlin.io.path.fileSize
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exporting a book of 1000 pages (S11, N1): it runs in the background, so a stand-in for the UI thread keeps running.
 * What is checked always: the content, the progress and that the UI thread was not used for the work. The limits on
 * the time only with `KORREKTOR_PERF_TESTS=1` (a CI machine is slow).
 */
class ExportPerfTest {
    private val timing = System.getenv("KORREKTOR_PERF_TESTS") == "1"
    private val pages = 1000

    @Test
    fun aBookOf1000PagesIsExportedInBothFormatsInTheBackgroundWhileTheUiKeepsRunning() =
        withBigBook(pages) { project ->
            val ui = Executors.newSingleThreadExecutor { Thread(it, "fake-ui").apply { isDaemon = true } }
            val uiDispatcher = ui.asCoroutineDispatcher()
            val longest = LongArray(1)
            val finished = CompletableDeferred<Unit>()
            val tick =
                CoroutineScope(uiDispatcher).launch {
                    var last = System.nanoTime()
                    while (!finished.isCompleted) {
                        delay(5)
                        val now = System.nanoTime()
                        longest[0] = maxOf(longest[0], (now - last) / 1_000_000)
                        last = now
                    }
                }
            val target = Files.createTempFile("exportperf", ".out")
            try {
                for (format in ExportFormat.entries) {
                    val options = ExportOptions(format, pageMarkers = true, header = true, keepLineBreaks = true)
                    val progress = ArrayList<Int>()
                    val started = System.nanoTime()
                    val count =
                        runBlocking {
                            withContext(uiDispatcher) {
                                BookExport(project::readPage, Dispatchers.IO).run(
                                    target,
                                    project.metadata,
                                    1..pages,
                                    options,
                                ) { synchronized(progress) { progress += it } }
                            }
                        }
                    val millis = (System.nanoTime() - started) / 1_000_000
                    println("export of $pages pages as ${format.label}: $millis ms, ${target.fileSize() / 1024} KB")
                    assertEquals(pages, count)
                    assertEquals((1..pages).toList(), progress)
                    val text = target.readText()
                    val marker = if (format == ExportFormat.TEXT) "[Page " else "\\[Page "
                    assertEquals(pages, text.split(marker).size - 1, "a marker for every page")
                    assertTrue(text.contains("Zebra"))
                    if (timing) assertTrue(millis < 5_000, "took $millis ms")
                }
            } finally {
                finished.complete(Unit)
                runBlocking { tick.join() }
                Files.deleteIfExists(target)
                ui.shutdownNow()
            }
            println("the fake UI thread waited at most ${longest[0]} ms between two ticks of 5 ms")
            if (timing) assertTrue(longest[0] < 500, "the UI thread was blocked for ${longest[0]} ms")
        }
}
