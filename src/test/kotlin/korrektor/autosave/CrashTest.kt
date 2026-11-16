package korrektor.autosave

import korrektor.editor.PageEdits
import korrektor.format.Metadata
import korrektor.format.Project
import korrektor.importer.PdfImport
import korrektor.pdf.TestPage
import korrektor.pdf.TestPdfs
import korrektor.pdf.TestText
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Kills the app in the middle of typing and in the middle of saving, again and again, and opens the project
 * afterwards (S07, N4). A small number of kills runs everywhere; `KORREKTOR_PERF_TESTS=1` makes it ten each, with one
 * long run of continuous typing, which is what the ten-second promise is about.
 */
class CrashTest {
    private val long = System.getenv("KORREKTOR_PERF_TESTS") != null

    private class Child(
        val process: Process,
        val lines: MutableList<Pair<Int, Long>>,
        val reader: Thread,
    )

    private fun withProject(
        pages: Int,
        block: (Project) -> Unit,
    ) {
        val dir = Files.createTempDirectory("crashtest")
        try {
            val pdf = dir.resolve("book.pdf")
            TestPdfs.write(
                pdf,
                List(pages) { n -> TestPage(texts = listOf(TestText("Seite ${n + 1}", 30f, 250f, 14f))) },
            )
            val project = Project.create(dir.resolve("b.korrektur"), pdf, emptyList(), Metadata("B"))
            PdfImport.run(project)
            block(project)
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    private fun start(
        folder: Path,
        mode: String,
        first: Int,
    ): Child {
        val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val process =
            ProcessBuilder(
                java,
                "-cp",
                System.getProperty("java.class.path"),
                "korrektor.autosave.AutosaveChildKt",
                folder.toString(),
                mode,
                first.toString(),
            ).redirectErrorStream(true).start()
        val lines = CopyOnWriteArrayList<Pair<Int, Long>>()
        val reader =
            Thread {
                process.inputStream.bufferedReader().forEachLine { line ->
                    val parts = line.split(' ')
                    if (parts.size == 2 &&
                        parts[0].toIntOrNull() != null
                    ) {
                        lines += parts[0].toInt() to parts[1].toLong()
                    }
                }
            }.apply {
                isDaemon = true
                start()
            }
        return Child(process, lines, reader)
    }

    /** Lets the child run for [millis] after it has started working, then kills it as a crash would. */
    private fun Child.killAfter(millis: Long) {
        val deadline = System.nanoTime() + 60_000_000_000L
        while (lines.isEmpty() && System.nanoTime() < deadline) Thread.sleep(20) // the JVM is starting
        assertTrue(lines.isNotEmpty(), "the child started working")
        Thread.sleep(millis)
        process.destroyForcibly()
        process.waitFor()
        reader.join(5_000)
    }

    @Test
    fun killedWhileTypingTheProjectOpensAndAtMostTheLastTenSecondsAreLost() {
        val kills = if (long) 10 else 3
        withProject(pages = 3) { project ->
            val random = Random(11)
            var counter = 0 // the last edit that the previous run printed
            var onDisk = 0 // the edit that the page file holds (0: the original text)
            repeat(kills) { round ->
                val run = if (long && round == 0) 12_000L else random.nextLong(600, 4_500)
                // The long run types without a pause: only the ten-second limit saves it. The others type like a
                // person.
                val child = start(project.folder, if (long && round == 0) "continuous" else "typing", counter)
                child.killAfter(run)

                val tmpBefore = temporaryFiles(project.folder).size
                val reopened = Project.open(project.folder) // opens, and removes what the kill left (*.tmp)
                val text = PageEdits.readingOrder(reopened.readPage(1)).first().text
                val sent = child.lines.toList()
                val last = sent.last()
                // The state on disk is one whole state: the one from before this run, or the state of one of its edits.
                val saved = text.removePrefix("edit ").toIntOrNull() ?: 0
                assertTrue(
                    saved == onDisk || saved in (counter + 1)..last.first,
                    "the page holds edit $saved; before the run $onDisk, " +
                        "this run's edits ran from ${counter + 1} to ${last.first}",
                )
                // The loss: the time from the edit on disk (or, if this run saved nothing, from the run's first edit).
                val savedAt = sent.firstOrNull { it.first == saved }?.second ?: sent.first().second
                val lost = last.second - savedAt
                assertTrue(
                    lost <= 10_500,
                    "at most ten seconds are lost, not $lost ms (run $round, ${sent.size} edits)",
                )
                assertNoTemporaryFiles(project.folder)
                println(
                    "typing kill $round after ${run / 1000.0} s: lost $lost ms, " +
                        "$tmpBefore temporary files left by the kill",
                )
                counter = last.first
                onDisk = saved
            }
        }
    }

    @Test
    fun killedWhileSavingManyPagesNoPageIsHalfWrittenOrMixed() {
        val kills = if (long) 10 else 3
        withProject(pages = 40) { project ->
            val random = Random(5)
            var round = 0
            repeat(kills) {
                val child = start(project.folder, "saving", round)
                child.killAfter(random.nextLong(300, 2_500))

                val tmpBefore = temporaryFiles(project.folder).size
                val reopened = Project.open(project.folder)
                println("saving kill: $tmpBefore temporary file(s) left by the kill (a save was in progress)")
                assertEquals(40, reopened.pages.size)
                for (page in 1..40) {
                    // Every page file decodes, and holds one whole state: the first, or a state of some round.
                    val text = PageEdits.readingOrder(reopened.readPage(page)).first().text
                    assertTrue(text == "Seite $page" || Regex("p$page r\\d+").matches(text), "page $page: '$text'")
                }
                assertNoTemporaryFiles(project.folder)
                round = child.lines.last().first
            }
        }
    }

    private fun temporaryFiles(folder: Path): List<Path> =
        Files.walk(folder).use { paths -> paths.filter { it.isRegularFile() && it.extension == "tmp" }.toList() }

    private fun assertNoTemporaryFiles(folder: Path) {
        val left =
            Files.walk(folder).use { paths ->
                paths.filter { it.isRegularFile() && it.extension == "tmp" }.toList()
            }
        assertEquals(emptyList(), left, "opening the project removed the temporary files")
    }
}
