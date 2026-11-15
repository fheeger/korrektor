package korrektor.viewer

import korrektor.pdf.TestPdfs
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * How fast the viewer switches pages (S04: under 0.5 s in a book of 1000 pages, target 0.3 s), measured with the
 * real PDFium source and the cache, following the steps the viewer takes: go to the page, learn its size, ask for the
 * picture of what is visible, wait for it. Not the drawing on the screen, which is a copy of the picture's pixels.
 * Timing checks are flaky on shared CI machines, so they run on request only: `KORREKTOR_PERF_TESTS=1`.
 */
class ViewerPerformanceTest {
    private val sample = Path.of("samples", "Abhandlung_über_den_kleinen_Krieg.pdf")

    private fun enabled() =
        assumeTrue(System.getenv("KORREKTOR_PERF_TESTS") != null, "set KORREKTOR_PERF_TESTS=1 to run")

    /** A viewer on a PDF: a window of 1400 x 900 pixels, fit width, and the real cache around the real source. */
    private class Viewer(
        pdf: Path,
        private val pages: Int,
        budgetBytes: Long = BUDGET,
    ) : AutoCloseable {
        val source = PageCache(PdfPageSource(pdf), budgetBytes)
        val state = ViewerState(pages).also { it.setViewport(WINDOW_WIDTH, WINDOW_HEIGHT) }

        /** Shows [page] and returns the milliseconds until its picture is there. */
        suspend fun show(page: Int): Long {
            val start = System.nanoTime()
            state.goTo(page)
            state.setPage(source.size(page), source.nativeScale(page))
            val request = checkNotNull(state.nextRequest(null)) { "nothing to render for page $page" }
            source.render(page, request.scale, request.region)
            return (System.nanoTime() - start) / NANOS_PER_MILLI
        }

        /** What the viewer does in the background once a page is shown: render the next one into the cache. */
        suspend fun prefetch(page: Int) {
            val request = state.preview(source.size(page), source.nativeScale(page)).nextRequest(null)
            if (request != null) source.render(page, request.scale, request.region)
        }

        override fun close() = source.close()
    }

    private class Stats(
        val label: String,
        values: List<Long>,
    ) {
        private val sorted = values.sorted()
        val median: Long get() = sorted[sorted.size / 2]
        val p95: Long get() = sorted[(sorted.size * P95).toInt().coerceAtMost(sorted.size - 1)]
        val max: Long get() = sorted.last()

        override fun toString() =
            "$label: median $median ms, 95th percentile $p95 ms, maximum $max ms (${sorted.size} switches)"
    }

    private fun withBook(
        pages: Int,
        block: suspend (Viewer) -> Unit,
    ) {
        val dir = Files.createTempDirectory("viewerperf")
        try {
            val pdf = dir.resolve("book.pdf")
            TestPdfs.book(pdf, pages, scanPixels = SCAN_WIDTH to SCAN_HEIGHT)
            println("Generated book of $pages pages: ${pdf.toFile().length() / BYTES_PER_KB} KB")
            Viewer(pdf, pages).use { viewer -> runBlocking { block(viewer) } }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    /** The measurements of one book: the first page, random pages, steps to the next page, and a jump to the end. */
    private suspend fun measure(
        viewer: Viewer,
        pages: Int,
    ): List<Stats> {
        val cold = viewer.show(1)
        val random = Random(SEED)
        val randomPages = List(SWITCHES) { random.nextInt(2, pages) }.distinct()
        val uncached = randomPages.map { viewer.show(it) }
        val start = pages / 2
        viewer.show(start)
        val steps =
            (start + 1..start + SWITCHES).map {
                viewer.prefetch(it) // done in the background while the user reads
                viewer.show(it)
            }
        val jump = viewer.show(pages)
        return listOf(
            Stats("first page (opens the PDF)", listOf(cold)),
            Stats("random pages, nothing cached", uncached),
            Stats("next page, rendered in advance", steps),
            Stats("jump to the last page", listOf(jump)),
        )
    }

    private fun check(stats: List<Stats>) {
        stats.forEach { println("  ${it.label} -> $it") }
        val uncached = stats[1]
        assertTrue(
            uncached.median < TARGET_MS,
            "median of a switch without cache: ${uncached.median} ms (target $TARGET_MS)",
        )
        assertTrue(uncached.max < LIMIT_MS, "slowest switch without cache: ${uncached.max} ms (limit $LIMIT_MS)")
        assertTrue(stats[3].max < LIMIT_MS, "jump to the last page: ${stats[3].max} ms (limit $LIMIT_MS)")
        assertTrue(stats[2].max < PREFETCHED_LIMIT_MS, "a prefetched page: ${stats[2].max} ms")
    }

    @Test
    fun aBookOf1000PagesSwitchesPagesFastEnough() {
        enabled()
        withBook(PAGES) { viewer -> check(measure(viewer, PAGES)) }
    }

    @Test
    fun theSampleSwitchesPagesFastEnough() {
        enabled()
        assumeTrue(sample.exists(), "the sample PDF is not available")
        val pages = 165
        Viewer(sample, pages).use { viewer -> runBlocking { check(measure(viewer, pages)) } }
    }

    @Test
    fun heapStaysWithinTheBudgetOver200PageSwitches() {
        enabled()
        val budget = SMALL_BUDGET
        val dir = Files.createTempDirectory("viewerperf")
        try {
            val pdf = dir.resolve("book.pdf")
            TestPdfs.book(pdf, PAGES, scanPixels = SCAN_WIDTH to SCAN_HEIGHT)
            Viewer(pdf, PAGES, budget).use { viewer ->
                runBlocking {
                    val random = Random(SEED)
                    viewer.show(1)
                    val before = usedHeap()
                    val processBefore = processMemory()
                    repeat(HEAP_SWITCHES) {
                        viewer.show(random.nextInt(1, PAGES + 1))
                        assertTrue(
                            viewer.source.usedBytes <= budget,
                            "the cache holds ${viewer.source.usedBytes} bytes",
                        )
                    }
                    val growth = usedHeap() - before
                    val processGrowth = processMemory() - processBefore
                    val cache = "$HEAP_SWITCHES switches with a cache of ${budget / BYTES_PER_MB} MB"
                    println("Heap growth over $cache: ${growth / BYTES_PER_MB} MB")
                    println("Process growth (native pixels too) over $cache: ${processGrowth / BYTES_PER_MB} MB")
                    assertTrue(growth < budget + HEAP_MARGIN, "the heap grew by ${growth / BYTES_PER_MB} MB")
                    // The pixels of a picture are in native memory (Skia), outside the heap: the cache's budget is
                    // what bounds them, so the process may grow by the budget plus what the JVM and PDFium hold.
                    assertTrue(
                        processGrowth < budget + PROCESS_MARGIN,
                        "the process grew by ${processGrowth / BYTES_PER_MB} MB",
                    )
                }
            }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    /** The memory the operating system has committed to this process, native memory included. */
    private fun processMemory(): Long =
        (ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean)
            .committedVirtualMemorySize

    private fun usedHeap(): Long {
        // The test measures what stays in memory, which needs a collection first.
        @Suppress("ExplicitGarbageCollectionCall")
        System.gc()
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private companion object {
        const val PAGES = 1000
        const val SWITCHES = 50
        const val HEAP_SWITCHES = 200
        const val SEED = 42
        const val WINDOW_WIDTH = 1400
        const val WINDOW_HEIGHT = 900
        const val SCAN_WIDTH = 1500
        const val SCAN_HEIGHT = 2400
        const val BUDGET = 256L * 1024 * 1024
        const val SMALL_BUDGET = 64L * 1024 * 1024
        const val HEAP_MARGIN = 100L * 1024 * 1024
        const val PROCESS_MARGIN = 300L * 1024 * 1024
        const val TARGET_MS = 300L
        const val LIMIT_MS = 500L
        const val PREFETCHED_LIMIT_MS = 100L
        const val P95 = 0.95
        const val NANOS_PER_MILLI = 1_000_000L
        const val BYTES_PER_KB = 1024L
        const val BYTES_PER_MB = 1024L * 1024L
    }
}
