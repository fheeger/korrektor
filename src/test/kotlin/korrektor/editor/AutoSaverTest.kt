package korrektor.editor

import korrektor.format.Metadata
import korrektor.format.PageLayer
import korrektor.format.Project
import korrektor.importer.PdfImport
import korrektor.pdf.TestPage
import korrektor.pdf.TestPdfs
import korrektor.pdf.TestText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.readBytes
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Writing the unsaved pages in the background (T058). */
class AutoSaverTest {
    private fun withProject(block: (Project) -> Unit) {
        val dir = Files.createTempDirectory("autosaver")
        try {
            val pdf = dir.resolve("book.pdf")
            val pages =
                List(4) { n ->
                    TestPage(texts = listOf(TestText("Seite ${n + 1} oben", 30f, 250f), TestText("unten", 30f, 230f)))
                }
            TestPdfs.write(pdf, pages)
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

    private fun snapshot(project: Project) =
        project.folder
            .walk()
            .filter { Files.isRegularFile(it) }
            .associate { it.toString() to it.readBytes() }

    private fun editorAndSaver(
        project: Project,
        write: (Int, PageLayer) -> Unit = project::writePage,
    ): Pair<BookEditor, AutoSaver> {
        val editor = BookEditor(project::readPage, Dispatchers.IO)
        return editor to AutoSaver(editor, Dispatchers.IO, write)
    }

    private fun Project.firstText(page: Int) = PageEdits.readingOrder(readPage(page)).first().text

    @Test
    fun theChangedPagesAreWrittenAndNothingIsUnsavedAfterwards() =
        withProject { project ->
            val (editor, saver) = editorAndSaver(project)
            runBlocking {
                editor.show(1)
                editor.setLineText("l1", "Seite eins korrigiert")
                editor.show(3)
                editor.setLineText("l2", "unten korrigiert")

                assertEquals(SaveOutcome.Done, saver.saveNow())
            }
            assertEquals(emptySet(), editor.changedPages)
            assertEquals("Seite eins korrigiert", project.firstText(1))
            assertEquals("unten korrigiert", PageEdits.readingOrder(project.readPage(3)).last().text)
            assertNull(saver.error)
            assertFalse(saver.saving)
        }

    @Test
    fun onlyTheChangedPagesAreWrittenAndTheRestOfTheFolderIsUntouched() =
        withProject { project ->
            val (editor, saver) = editorAndSaver(project)
            val before = snapshot(project)
            runBlocking {
                editor.show(2)
                editor.setLineText("l1", "geändert")
                saver.saveNow()
            }
            val after = snapshot(project)
            assertEquals(before.keys, after.keys, "no file added or removed")
            val changed = after.filter { (name, bytes) -> !bytes.contentEquals(before.getValue(name)) }.keys
            assertEquals(1, changed.size, "only one file differs: $changed")
            assertTrue(changed.single().endsWith("0002.json"), changed.single())
        }

    @Test
    fun aPageThatWasSavedAndNotChangedSinceIsNotWrittenAgain() =
        withProject { project ->
            val written = mutableListOf<Int>()
            val (editor, saver) =
                editorAndSaver(project) { page, layer ->
                    written += page
                    project.writePage(page, layer)
                }
            runBlocking {
                editor.show(1)
                editor.setLineText("l1", "a")
                editor.show(2)
                editor.setLineText("l1", "b")
                saver.saveNow()
                assertEquals(listOf(1, 2), written)
                saver.saveNow()
                assertEquals(listOf(1, 2), written, "nothing to write the second time")
                editor.setLineText("l1", "bb")
                saver.saveNow()
                assertEquals(listOf(1, 2, 2), written)
            }
        }

    @Test
    fun aPageEditedWhileItIsBeingWrittenIsNotLostAndTheOldLayerNeverOverwritesTheNewOne() =
        withProject { project ->
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val writes = mutableListOf<String>()
            val (editor, saver) =
                editorAndSaver(project) { page, layer ->
                    writes += PageEdits.readingOrder(layer).first().text
                    if (writes.size == 1) {
                        entered.complete(Unit)
                        runBlocking { release.await() }
                    }
                    project.writePage(page, layer)
                }
            runBlocking {
                editor.show(1)
                editor.setLineText("l1", "erste Fassung")
                val first = async { saver.saveNow() }
                entered.await()
                editor.setLineText("l1", "zweite Fassung") // while the first is being written
                release.complete(Unit)
                first.await()
                assertEquals(setOf(1), editor.changedPages, "the page is still unsaved")
                saver.saveNow()
            }
            assertEquals(listOf("erste Fassung", "zweite Fassung"), writes)
            assertEquals("zweite Fassung", project.firstText(1))
            assertEquals(emptySet(), editor.changedPages)
        }

    @Test
    fun twoSavesAtOnceWriteInTurnAndNotTogether() =
        withProject { project ->
            val running = AtomicInteger()
            val most = AtomicInteger()
            val (editor, saver) =
                editorAndSaver(project) { page, layer ->
                    most.accumulateAndGet(running.incrementAndGet(), ::maxOf)
                    Thread.sleep(50)
                    project.writePage(page, layer)
                    running.decrementAndGet()
                }
            runBlocking {
                editor.show(1)
                editor.setLineText("l1", "a")
                editor.show(2)
                editor.setLineText("l1", "b")
                val a = async { saver.saveNow() }
                val b = async { saver.saveNow() }
                a.await()
                b.await()
            }
            assertEquals(1, most.get(), "never two writes at the same time")
            assertEquals(emptySet(), editor.changedPages)
        }

    @Test
    fun aFailingWriteLeavesThatPageAndTheOnesAfterItUnsaved() =
        withProject { project ->
            val (editor, saver) =
                editorAndSaver(project) { page, layer ->
                    if (page == 2) throw IOException("disk full")
                    project.writePage(page, layer)
                }
            runBlocking {
                for (n in 1..3) {
                    editor.show(n)
                    editor.setLineText("l1", "neu $n")
                }
                val outcome = saver.saveNow()
                assertIs<SaveOutcome.Failed>(outcome)
                assertEquals(2, outcome.page)
                assertTrue(outcome.reason.contains("disk full"), outcome.reason)
                assertEquals(outcome.reason, saver.error)
            }
            assertEquals(setOf(2, 3), editor.changedPages)
            assertEquals("neu 1", project.firstText(1))
            assertEquals("Seite 2 oben", project.firstText(2), "not written")
            assertFalse(saver.saving)
        }

    @Test
    fun theNextSaveWorksAgainAfterAFailureAndClearsTheError() =
        withProject { project ->
            var failing = true
            val (editor, saver) =
                editorAndSaver(project) { page, layer ->
                    if (failing) throw IOException("no permission")
                    project.writePage(page, layer)
                }
            runBlocking {
                editor.show(1)
                editor.setLineText("l1", "a")
                assertIs<SaveOutcome.Failed>(saver.saveNow())
                failing = false
                assertEquals(SaveOutcome.Done, saver.saveNow())
            }
            assertNull(saver.error)
            assertEquals(emptySet(), editor.changedPages)
        }

    @Test
    fun aRealFailureOfTheFileSystemBecomesAReadableReason() =
        withProject { project ->
            val (editor, saver) = editorAndSaver(project)
            runBlocking {
                editor.show(1)
                editor.setLineText("l1", "a")
                // The pages folder is replaced by a file, so that the page cannot be written.
                val pages = project.folder.resolve(Project.PAGES_DIR)
                pages.toFile().deleteRecursively()
                Files.writeString(pages, "not a folder")
                val outcome = saver.saveNow()
                assertIs<SaveOutcome.Failed>(outcome)
                assertTrue(outcome.reason.isNotBlank(), "a reason")
            }
            assertEquals(setOf(1), editor.changedPages, "the correction is kept in memory")
        }

    @Test
    fun savingIsTrueWhileTheWriterRuns() =
        withProject { project ->
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val (editor, saver) =
                editorAndSaver(project) { page, layer ->
                    entered.complete(Unit)
                    runBlocking { release.await() }
                    project.writePage(page, layer)
                }
            runBlocking {
                editor.show(1)
                editor.setLineText("l1", "a")
                assertFalse(saver.saving)
                val job = async { saver.saveNow() }
                entered.await()
                assertTrue(saver.saving)
                release.complete(Unit)
                job.await()
                assertFalse(saver.saving)
            }
        }

    @Test
    fun typingIsNotHeldUpByASaveThatIsRunning() =
        withProject { project ->
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val (editor, saver) =
                editorAndSaver(project) { page, layer ->
                    entered.complete(Unit)
                    runBlocking { release.await() } // the writer is stuck until the test says so
                    project.writePage(page, layer)
                }
            runBlocking {
                editor.show(1)
                editor.setLineText("l1", "a")
                val job = launch { saver.saveNow() }
                entered.await()
                // If an edit had to wait for the writer, this would never return (the writer waits for us).
                repeat(100) { editor.setLineText("l1", "typed $it") }
                assertEquals("typed 99", editor.lines.first().text)
                release.complete(Unit)
                job.join()
                assertEquals(setOf(1), editor.changedPages, "the typing is still to be saved")
            }
        }

    @Test
    fun nothingUnsavedMeansNothingIsWritten() =
        withProject { project ->
            val calls = AtomicInteger()
            val (editor, saver) =
                editorAndSaver(project) { page, layer ->
                    calls.incrementAndGet()
                    project.writePage(page, layer)
                }
            runBlocking {
                editor.show(1)
                assertEquals(SaveOutcome.Done, saver.saveNow())
            }
            assertEquals(0, calls.get())
        }
}
