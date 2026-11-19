package korrektor.export

import korrektor.editor.BookEditor
import korrektor.editor.currentLayer
import korrektor.editor.filesOf
import korrektor.format.AtomicFile
import korrektor.format.FormatException
import korrektor.format.Metadata
import korrektor.format.PageLayer
import korrektor.search.withBigBook
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.io.path.createTempDirectory
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The export job: what it writes, and that it fails, cancels and runs in the background without a trace. */
class BookExportTest {
    private val options = ExportOptions(ExportFormat.TEXT, pageMarkers = true, header = false, keepLineBreaks = true)

    private fun withTarget(block: (Path) -> Unit) {
        val dir = createTempDirectory("export")
        try {
            block(dir.resolve("out.txt"))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun writesTheRangeAsUtf8WithoutBomAndWithLf() =
        withBigBook(5) { project ->
            withTarget { target ->
                val progress = ArrayList<Int>()
                val export = BookExport(project::readPage, Dispatchers.IO)
                val count =
                    runBlocking {
                        export.run(
                            target,
                            project.metadata,
                            2..4,
                            options.copy(header = true),
                            progress::add,
                        )
                    }
                val bytes = target.readBytes()
                val text = bytes.toString(Charsets.UTF_8)
                assertEquals(3, count)
                assertEquals(listOf(1, 2, 3), progress)
                assertTrue(text.startsWith("B\n\n[Page 2]\n"), text.take(40))
                assertTrue(text.contains("\n\n[Page 3]\n") && text.contains("\n\n[Page 4]\n"))
                assertFalse(text.contains("[Page 1]") || text.contains("[Page 5]"))
                assertFalse(text.contains('\r'))
                assertFalse(bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte())
                assertTrue(text.endsWith("\n") && !text.endsWith("\n\n"))
                assertEquals(listOf("out.txt"), target.parent.listDirectoryEntries().map { it.name })
            }
        }

    @Test
    fun pagesWithCorrectionsThatAreNotSavedYetAreExportedAsTheyAreInTheEditor() =
        withBigBook(3) { project ->
            withTarget { target ->
                val editor = BookEditor(project::readPage, Dispatchers.Unconfined)
                runBlocking { editor.show(2) }
                val line = editor.lines.first()
                editor.setLineText(line.id, "Korrigiert ſo")
                assertTrue(2 in editor.changedPages)
                runBlocking { BookExport(editor::currentLayer, Dispatchers.IO).run(target, null, 1..3, options) }
                val text = target.readText()
                assertTrue(text.contains("Korrigiert ſo\n"))
                val page2 = text.substringAfter("[Page 2]\n").substringBefore("\n\n[Page 3]").lines()
                assertEquals("Korrigiert ſo", page2.first())
                assertEquals(
                    project
                        .readPage(2)
                        .regions
                        .first()
                        .lines.size,
                    page2.size,
                )
            }
        }

    @Test
    fun theProjectIsNotChangedByAnExport() =
        withBigBook(4) { project ->
            withTarget { target ->
                val before = filesOf(project)
                runBlocking {
                    BookExport(
                        project::readPage,
                        Dispatchers.IO,
                    ).run(target, project.metadata, 1..4, options)
                }
                val after = filesOf(project)
                assertEquals(before.keys, after.keys)
                before.forEach { (name, bytes) -> assertContentEquals(bytes, after.getValue(name), name) }
            }
        }

    @Test
    fun aPageThatCannotBeReadStopsTheExportAndNamesThePage() =
        withBigBook(4) { project ->
            withTarget { target ->
                val read = { n: Int -> if (n == 3) throw FormatException("broken json") else project.readPage(n) }
                val failure =
                    assertFailsWith<ExportException> {
                        runBlocking { BookExport(read, Dispatchers.IO).run(target, null, 1..4, options) }
                    }
                assertTrue(failure.message!!.contains("Page 3"), failure.message)
                assertTrue(failure.message!!.contains("broken json"))
                assertFalse(target.exists())
            }
        }

    @Test
    fun aFailingWriteLeavesTheOldFileAndNoTempFile() =
        withBigBook(3) { project ->
            withTarget { target ->
                target.writeText("the old export")
                val failing = { path: Path, bytes: ByteArray ->
                    AtomicFile.write(path, bytes) { _, _ -> throw IOException("disk full") }
                }
                val failure =
                    assertFailsWith<ExportException> {
                        runBlocking {
                            BookExport(
                                project::readPage,
                                Dispatchers.IO,
                                failing,
                            ).run(target, null, 1..3, options)
                        }
                    }
                assertTrue(failure.message!!.contains("disk full"), failure.message)
                assertEquals("the old export", target.readText())
                assertEquals(listOf("out.txt"), target.parent.listDirectoryEntries().map { it.name })
            }
        }

    @Test
    fun cancellingLeavesTheOldFileAsItWas() =
        withBigBook(6) { project ->
            withTarget { target ->
                target.writeText("the old export")
                val reading = CompletableDeferred<Unit>()
                val slow = { n: Int ->
                    if (n == 3) reading.complete(Unit)
                    Thread.sleep(40)
                    project.readPage(n)
                }
                runBlocking {
                    val job = async { BookExport(slow, Dispatchers.IO).run(target, null, 1..6, options) }
                    withTimeout(10_000) { reading.await() }
                    job.cancelAndJoin()
                    assertTrue(job.isCancelled)
                }
                assertEquals("the old export", target.readText())
                assertEquals(listOf("out.txt"), target.parent.listDirectoryEntries().map { it.name })
            }
        }

    @Test
    fun theWorkIsNotDoneOnTheThreadThatCalls() =
        withBigBook(5) { project ->
            withTarget { target ->
                val ui = Executors.newSingleThreadExecutor { Thread(it, "fake-ui").apply { isDaemon = true } }
                val threads = HashSet<String>()
                val read = { n: Int ->
                    project.readPage(n).also {
                        synchronized(threads) {
                            threads +=
                                Thread.currentThread().name
                        }
                    }
                }
                val write = { path: Path, bytes: ByteArray ->
                    synchronized(threads) { threads += Thread.currentThread().name }
                    AtomicFile.write(path, bytes)
                }
                runBlocking {
                    withContext(ui.asCoroutineDispatcher()) {
                        BookExport(read, Dispatchers.IO, write).run(target, null, 1..5, options)
                    }
                }
                ui.shutdown()
                assertTrue(threads.isNotEmpty() && "fake-ui" !in threads, "$threads")
                assertTrue(target.exists())
            }
        }

    @Test
    fun anEmptyLayerGivesNoTextButTheFileIsWritten() =
        withTarget { target ->
            val empty = { n: Int -> PageLayer(n, 100.0, 100.0, emptyList()) }
            runBlocking {
                BookExport(
                    empty,
                    Dispatchers.IO,
                ).run(target, Metadata("T"), 1..2, options.copy(pageMarkers = false))
            }
            assertTrue(Files.exists(target))
            assertEquals("", target.readText())
        }
}
