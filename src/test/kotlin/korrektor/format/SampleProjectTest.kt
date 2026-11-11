package korrektor.format

import kotlinx.serialization.json.JsonPrimitive
import org.apache.pdfbox.Loader
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.relativeTo
import kotlin.io.path.writeBytes
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A book of the sample's size and shape goes through the whole format. The real OCR text of the sample can't be
 * used before the import (S03); this shows that the format carries a book like it. If the sample PDF is present,
 * it is the project's source and gives the page count and sizes.
 */
class SampleProjectTest {
    private val samplePdf = Path.of("samples", "Abhandlung_über_den_kleinen_Krieg.pdf")

    private val vocabulary =
        listOf(
            "ſich",
            "uͤber",
            "Krieg",
            "ꝛc.",
            "daß",
            "Haupt=",
            "Feind",
            "oͤffentlich",
            "Ordnung",
            "iſt",
            "und",
            "der",
            "ſeine",
            "Trouppen",
        )

    private fun pageSizes(): List<Pair<Double, Double>> =
        if (samplePdf.exists()) {
            Loader.loadPDF(samplePdf.toFile()).use { doc ->
                doc.pages.map { CanonicalJson.round1(it.mediaBox.width.toDouble()) to CanonicalJson.round1(it.mediaBox.height.toDouble()) }
            }
        } else {
            List(165) { 360.0 to 576.0 }
        }

    private fun originalLayer(
        random: Random,
        pdfPage: Int,
        size: Pair<Double, Double>,
    ): PageLayer {
        val lines =
            (1..random.nextInt(30, 41)).map { i ->
                val words =
                    (1..random.nextInt(8, 13)).map { j ->
                        Word(vocabulary.random(random), Box(40.0 + j * 30.0, 40.0 + i * 12.0, 28.5, 10.2))
                    }
                Line("l$i", Box(40.0, 40.0 + i * 12.0, CanonicalJson.round1(size.first - 80.0), 10.2), null, words)
            }
        return PageLayer(
            pdfPage,
            size.first,
            size.second,
            listOf(Region("r1", RegionType.TEXT, 1, Box(40.0, 40.0, CanonicalJson.round1(size.first - 80.0), 400.0), lines)),
        )
    }

    /** The corrected layer starts as a copy that refers to the original lines; some lines are typed anew. */
    private fun correctedLayer(
        random: Random,
        original: PageLayer,
    ): PageLayer {
        val region = original.regions[0]
        val lines =
            region.lines.map { line ->
                val copy = line.copy(origin = line.id, id = "c${line.id}")
                when (random.nextInt(10)) {
                    0 -> copy.copy(box = null, words = copy.words.map { it.copy(box = null) })
                    1 -> copy.copy(extra = mapOf("note" to JsonPrimitive("checked")))
                    else -> copy
                }
            } + Line("cnew", words = listOf(Word("neu"), Word("eingefügt")))
        return original.copy(regions = listOf(region.copy(lines = lines)), extra = mapOf("reviewed" to JsonPrimitive(true)))
    }

    private fun snapshot(folder: Path): Map<String, List<Byte>> =
        Files
            .walk(folder)
            .use { it.filter { p -> Files.isRegularFile(p) }.toList() }
            .associate { it.relativeTo(folder).toString().replace('\\', '/') to it.readBytes().toList() }

    private fun withProject(block: (Project, List<PageLayer>, List<PageLayer>, Path) -> Unit) {
        val dir = Files.createTempDirectory("sample")
        try {
            val sizes = pageSizes()
            val random = Random(42)
            val pdf =
                if (samplePdf.exists()) {
                    samplePdf
                } else {
                    dir.resolve("book.pdf").also { it.writeBytes(ByteArray(50_000) { i -> (i % 251).toByte() }) }
                }
            val originals = sizes.mapIndexed { i, s -> originalLayer(random, i + 1, s) }
            val corrected = originals.map { correctedLayer(random, it) }
            val project = Project.create(dir.resolve("book.korrektur"), pdf, sizes.indices.map { PageInfo(it + 1) })
            originals.forEachIndexed { i, l -> project.writeOriginalPage(i + 1, l) }
            corrected.forEachIndexed { i, l -> project.writePage(i + 1, l) }
            block(project, originals, corrected, dir.resolve("book.korrektur"))
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    @Test
    fun aBookOfTheSamplesSizeIsWrittenReopenedAndEqual() =
        withProject { project, originals, corrected, folder ->
            val reopened = Project.open(folder)
            assertEquals(originals.size, reopened.pages.size)
            originals.indices.forEach { i ->
                assertEquals(originals[i], reopened.readOriginalPage(i + 1))
                assertEquals(corrected[i], reopened.readPage(i + 1))
            }

            // Opening and saving the whole project changes no byte.
            val before = snapshot(folder)
            reopened.saveProjectFile()
            originals.indices.forEach { i -> reopened.writePage(i + 1, reopened.readPage(i + 1)) }
            assertEquals(before, snapshot(folder))
            assertTrue(project.pages.size >= 100)
        }

    @Test
    fun readingOnePageIsFast() {
        // A timing check is flaky on shared CI machines, so it only runs on request (process.md: performance tests are not run on every PR).
        assumeTrue(System.getenv("KORREKTOR_PERF_TESTS") != null, "set KORREKTOR_PERF_TESTS=1 to run")
        readingOnePageIsFastBody()
    }

    private fun readingOnePageIsFastBody() =
        withProject { _, _, _, folder ->
            val project = Project.open(folder)
            project.readPage(1) // warm up the JVM
            val best = (1..5).minOf { n -> time { project.readPage(100 + n) } }
            assertTrue(best < 50, "Reading one page took $best ms")
        }

    private fun time(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }

    @Test
    fun changingOneWordChangesOneLineInOneFile() =
        withProject { _, _, corrected, folder ->
            val project = Project.open(folder)
            val before = snapshot(folder)
            val page = corrected[49]
            val region = page.regions[0]
            val line = region.lines[3]
            val newWords = line.words.mapIndexed { i, w -> if (i == 2) w.copy(text = "verbeſſert") else w }
            val changed =
                page.copy(
                    regions =
                        listOf(
                            region.copy(
                                lines =
                                    region.lines.toMutableList().also {
                                        it[3] =
                                            line.copy(words = newWords)
                                    },
                            ),
                        ),
                )
            project.writePage(50, changed)

            val after = snapshot(folder)
            val differing = after.keys.filter { before[it] != after[it] }
            assertEquals(listOf("pages/0050.json"), differing)
            val old = String(before.getValue("pages/0050.json").toByteArray(), Charsets.UTF_8).lines()
            val new = String(after.getValue("pages/0050.json").toByteArray(), Charsets.UTF_8).lines()
            assertEquals(old.size, new.size)
            assertEquals(1, old.indices.count { old[it] != new[it] })
        }

    @Test
    fun theSamplePdfIsCopiedAndPagesMatchIt() {
        assumeTrue(samplePdf.exists(), "sample PDF is not available")
        withProject { project, _, _, _ ->
            assertContentEquals(samplePdf.readBytes(), project.sourcePdf.readBytes())
            val sizes = pageSizes()
            assertEquals(sizes.size, project.pages.size)
            sizes.forEachIndexed { i, (w, h) ->
                val layer = project.readPage(i + 1)
                assertEquals(w, layer.width)
                assertEquals(h, layer.height)
            }
        }
    }
}
