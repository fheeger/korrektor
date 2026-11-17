package korrektor.history

import korrektor.AppModel
import korrektor.closeProject
import korrektor.editor.UndoResult
import korrektor.editor.apply
import korrektor.editor.redo
import korrektor.editor.undo
import korrektor.format.EditEntry
import korrektor.format.HistoryCodec
import korrektor.format.HistoryEntry
import korrektor.format.LineChange
import korrektor.format.Metadata
import korrektor.format.PageLayer
import korrektor.format.Project
import korrektor.format.RedoEntry
import korrektor.format.SavedEntry
import korrektor.format.UndoEntry
import korrektor.format.entrySeq
import korrektor.importer.PdfImport
import korrektor.pdf.TestPage
import korrektor.pdf.TestPdfs
import korrektor.pdf.TestText
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Kills the app in the middle of typing, undoing and saving, again and again, and opens the project afterwards (S08):
 * the history and the pages must agree, and undo must work. A small number of kills runs everywhere;
 * `KORREKTOR_PERF_TESTS=1` makes it more.
 *
 * "Agree" is checked exactly: the pages as they were imported, with every change of the history applied in the order
 * of the file, must give the page files as they are after opening.
 */
class HistoryCrashTest {
    private val long = System.getenv("KORREKTOR_PERF_TESTS") != null
    private val pages = 40

    private class Child(
        val process: Process,
        val rounds: MutableList<Int>,
        val reader: Thread,
    )

    private fun withProject(block: (Project) -> Unit) {
        val dir = Files.createTempDirectory("historycrash")
        try {
            val pdf = dir.resolve("book.pdf")
            TestPdfs.write(
                pdf,
                List(pages) { n ->
                    TestPage(texts = listOf(TestText("Seite ${n + 1} oben", 30f, 250f), TestText("unten", 30f, 230f)))
                },
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
        first: Int,
    ): Child {
        val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val process =
            ProcessBuilder(
                java,
                "-cp",
                System.getProperty("java.class.path"),
                "korrektor.history.HistoryChildKt",
                folder.toString(),
                first.toString(),
            ).redirectErrorStream(true).start()
        val rounds = CopyOnWriteArrayList<Int>()
        val reader =
            Thread {
                process.inputStream.bufferedReader().forEachLine { line ->
                    line
                        .split(' ')
                        .takeIf { it.size == 2 }
                        ?.first()
                        ?.toIntOrNull()
                        ?.let { rounds += it }
                }
            }.apply {
                isDaemon = true
                start()
            }
        return Child(process, rounds, reader)
    }

    private fun Child.killAfter(millis: Long) {
        val deadline = System.nanoTime() + 60_000_000_000L
        while (rounds.isEmpty() && System.nanoTime() < deadline) Thread.sleep(20) // the JVM is starting
        assertTrue(rounds.isNotEmpty(), "the child started working")
        Thread.sleep(millis)
        process.destroyForcibly()
        process.waitFor()
        reader.join(5_000)
    }

    private fun changesOf(entry: HistoryEntry) =
        when (entry) {
            is EditEntry -> entry.changes
            is UndoEntry -> entry.changes
            is RedoEntry -> entry.changes
            else -> emptyList()
        }

    /** The pages as the history says: the imported pages with every change of every entry made, in file order. */
    private fun replayed(
        project: Project,
        initial: Map<Int, PageLayer>,
    ): Map<Int, PageLayer> {
        val layers = initial.toMutableMap()
        for (entry in project.historyLog.readAll().map(HistoryCodec::decode)) {
            for (change in changesOf(entry).filterIsInstance<LineChange>()) {
                layers[change.page] =
                    checkNotNull(apply(layers.getValue(change.page), change)) { "an entry does not fit" }
            }
        }
        return layers
    }

    private fun assertUndoWorks(
        model: AppModel,
        label: String,
    ) {
        val editor = checkNotNull(model.editor)
        repeat(12) {
            val result = editor.undo()
            assertTrue(result is UndoResult.Done || result == UndoResult.NothingToDo, "$label: undo gave $result")
        }
        repeat(6) {
            val result = editor.redo()
            assertTrue(result is UndoResult.Done || result == UndoResult.NothingToDo, "$label: redo gave $result")
        }
    }

    @Test
    fun killedAgainAndAgainTheHistoryAndThePagesAgreeAndUndoWorks() {
        val kills = if (long) 12 else 3
        withProject { project ->
            val initial = (1..pages).associateWith { project.readPage(it) }
            val random = Random(23)
            var round = 0
            repeat(kills) { number ->
                val child = start(project.folder, round)
                child.killAfter(random.nextLong(500, 3_500))
                round = child.rounds.last()

                // How far the kill got: the entries after the last `saved` are what the recovery has to look at.
                val unconfirmed =
                    project.historyLog
                        .readAll()
                        .map(HistoryCodec::decode)
                        .takeLastWhile { it !is SavedEntry }
                        .size
                // The app opens the project: the history is repaired and loaded.
                val model = AppModel()
                model.openProject(project.folder)
                assertNull(model.error, "kill $number: the project opens")
                val opened = Project.open(project.folder)

                // Every entry fits, the pages are what the history says, and the history ends with `saved`.
                val expected = replayed(opened, initial)
                for (page in 1..pages) {
                    val label = "kill $number, page $page"
                    assertEquals(
                        expected.getValue(page).copy(history = null),
                        opened.readPage(page).copy(history = null),
                        label,
                    )
                }
                val entries = opened.historyLog.readAll().map(HistoryCodec::decode)
                assertTrue(
                    entries.isEmpty() || entries.last() is SavedEntry,
                    "kill $number: the history ends with saved",
                )
                val newest = entries.mapNotNull { it.entrySeq }.maxOrNull() ?: 0
                for (page in 1..pages) {
                    assertTrue((opened.readPage(page).history ?: 0) <= newest, "kill $number: no page is ahead")
                }

                assertUndoWorks(model, "kill $number")
                println(
                    "history kill $number: ${entries.size} entries, $unconfirmed after the last saved, round $round",
                )
                model.closeProject()
            }
        }
    }
}
