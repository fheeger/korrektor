package korrektor

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import korrektor.editor.PageEdits
import korrektor.editor.historyOf
import korrektor.editor.setStatus
import korrektor.format.EditEntry
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
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Closing the app, opening another project and saving somewhere else (T062, T066). */
@OptIn(ExperimentalTestApi::class)
class CloseTest {
    private class Rig(
        val dir: Path,
        val folder: Path,
        val model: AppModel,
    ) {
        var failing = false
        var exits = 0
        val written = CopyOnWriteArrayList<Int>()

        /** While set, every page write waits for it. */
        @Volatile
        var gate: CountDownLatch? = null
    }

    private fun withRig(block: (Rig) -> Unit) {
        val dir = Files.createTempDirectory("closetest")
        try {
            val pdf = dir.resolve("book.pdf")
            TestPdfs.write(pdf, List(3) { n -> TestPage(texts = listOf(TestText("Seite ${n + 1}", 30f, 250f, 14f))) })
            val project = Project.create(dir.resolve("b.korrektur"), pdf, emptyList(), Metadata("B"))
            PdfImport.run(project)
            lateinit var rig: Rig
            val writer: (Project) -> (Int, PageLayer) -> Unit = { opened ->
                { page, layer ->
                    rig.written += page
                    rig.gate?.await()
                    if (rig.failing) throw IOException("disk full")
                    opened.writePage(page, layer)
                }
            }
            val model = AppModel(pageWriter = writer)
            rig = Rig(dir, project.folder, model)
            model.exit = { rig.exits++ }
            model.openProject(project.folder)
            runBlocking { model.editor!!.show(1) }
            block(rig)
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    private fun Project.firstText(page: Int) = PageEdits.readingOrder(readPage(page)).first().text

    @Test
    fun closingSavesTheChangesFirstWithoutAskingAndThenExits() =
        withRig { rig ->
            rig.model.editor!!.setLineText("l1", "korrigiert")
            assertEquals("Seite 1", Project.open(rig.folder).firstText(1), "not saved yet: the timer has not run")

            val closing = runBlocking { rig.model.requestClose() }

            assertTrue(closing)
            assertEquals(1, rig.exits)
            assertEquals("korrigiert", Project.open(rig.folder).firstText(1), "written before the exit")
            assertNull(rig.model.closeProblem)
        }

    @Test
    fun closingWithNothingUnsavedWritesNothingAndAsksNothing() =
        withRig { rig ->
            assertTrue(runBlocking { rig.model.requestClose() })
            assertEquals(1, rig.exits)
            assertEquals(emptyList(), rig.written)
            assertNull(rig.model.closeProblem)
        }

    @Test
    fun closingWithoutAProjectJustExits() {
        val model = AppModel()
        var exits = 0
        model.exit = { exits++ }
        assertTrue(runBlocking { model.requestClose() })
        assertEquals(1, exits)
    }

    @Test
    fun closingWhileSavingFailsStaysOpenAndSaysWhatWentWrong() =
        withRig { rig ->
            rig.failing = true
            rig.model.editor!!.setLineText("l1", "korrigiert")

            val closing = runBlocking { rig.model.requestClose() }

            assertFalse(closing)
            assertEquals(0, rig.exits, "the app does not close")
            val problem = rig.model.closeProblem!!
            assertTrue(problem.contains("1 page") && problem.contains("disk full"), problem)
            assertEquals(setOf(1), rig.model.editor!!.changedPages, "the changes are kept")
        }

    @Test
    fun aSecondCloseRequestWhileTheFirstIsSavingIsIgnored() =
        withRig { rig ->
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val slow =
                AppModel(pageWriter = { opened ->
                    { page, layer ->
                        entered.complete(Unit)
                        runBlocking { release.await() }
                        opened.writePage(page, layer)
                    }
                })
            slow.exit = { rig.exits++ }
            // a project of its own for the slow model
            val pdf = rig.dir.resolve("book2.pdf").also { Files.copy(rig.dir.resolve("book.pdf"), it) }
            val project = Project.create(rig.dir.resolve("c.korrektur"), pdf, emptyList(), Metadata("C"))
            PdfImport.run(project)
            slow.openProject(project.folder)
            runBlocking {
                slow.editor!!.show(1)
                slow.editor!!.setLineText("l1", "x")
                val first = async { slow.requestClose() }
                entered.await()
                assertFalse(slow.requestClose(), "ignored while the first is working")
                release.complete(Unit)
                assertTrue(first.await())
            }
            assertEquals(1, rig.exits)
        }

    @Test
    fun aCloseRequestWhileAProjectIsBeingCreatedIsIgnored() =
        withRig { rig ->
            rig.model.creating = true
            assertFalse(runBlocking { rig.model.requestClose() })
            assertEquals(0, rig.exits)
        }

    // --- opening another project ----------------------------------------------------------------------------

    private fun otherProject(rig: Rig): Path {
        val pdf = rig.dir.resolve("o.pdf").also { Files.copy(rig.dir.resolve("book.pdf"), it) }
        return Project.create(rig.dir.resolve("o.korrektur"), pdf, emptyList(), Metadata("O")).folder
    }

    @Test
    fun openingAnotherProjectSavesTheChangesOfTheOpenOneFirst() =
        withRig { rig ->
            val other = otherProject(rig)
            rig.model.editor!!.setLineText("l1", "korrigiert")

            rig.model.openProject(other)

            assertNull(rig.model.error)
            assertEquals(other, rig.model.project!!.folder)
            assertEquals("korrigiert", Project.open(rig.folder).firstText(1))
        }

    @Test
    fun openingAnotherProjectWhileSavingFailsDoesNotOpenItAndKeepsTheChanges() =
        withRig { rig ->
            val other = otherProject(rig)
            rig.failing = true
            rig.model.editor!!.setLineText("l1", "korrigiert")

            rig.model.openProject(other)

            assertTrue(rig.model.error!!.contains("disk full"), rig.model.error)
            assertEquals(rig.folder, rig.model.project!!.folder, "still the first project")
            assertEquals(setOf(1), rig.model.editor!!.changedPages)
        }

    // --- save elsewhere -------------------------------------------------------------------------------------

    @Test
    fun savingElsewhereCopiesTheProjectWritesTheChangesAndOpensTheCopy() =
        withRig { rig ->
            rig.failing = true
            rig.model.editor!!.setLineText("l1", "korrigiert")
            runBlocking { rig.model.requestClose() }
            assertNotNull(rig.model.closeProblem)
            val parent = Files.createDirectory(rig.dir.resolve("elsewhere"))
            rig.failing = false

            val worked = runBlocking { rig.model.saveElsewhere(parent) }

            assertTrue(worked, rig.model.closeProblem)
            val copy = parent.resolve("b.korrektur")
            assertEquals(copy, rig.model.project!!.folder, "the copy is open")
            assertEquals("korrigiert", Project.open(copy).firstText(1), "with the change that could not be saved")
            assertEquals("Seite 1", Project.open(rig.folder).firstText(1), "the old folder is as it was")
            assertEquals(3, Project.open(copy).pages.size)
            assertNull(rig.model.closeProblem)
            assertTrue(rig.model.notice!!.contains("copy"), rig.model.notice)
            assertEquals(0, rig.exits)
            // And later edits are saved into the copy.
            runBlocking { rig.model.editor!!.show(2) }
            rig.model.editor!!.setLineText("l1", "später")
            runBlocking { rig.model.saver!!.saveNow() }
            assertEquals("später", Project.open(copy).firstText(2))
        }

    @Test
    fun savingElsewhereCopiesTheHistoryWithTheEntriesThatWerePending() =
        withRig { rig ->
            rig.model.editor!!.setLineText("l1", "x") // never saved: its entry is pending
            val parent = Files.createDirectory(rig.dir.resolve("elsewhere"))

            val worked = runBlocking { rig.model.saveElsewhere(parent) }

            assertTrue(worked, rig.model.closeProblem)
            val copy = Project.open(parent.resolve("b.korrektur"))
            // The copy has the entry, a `saved` entry after it, and its page carries the number of the entry.
            assertEquals(listOf("EditEntry", "SavedEntry"), historyOf(copy).map { it::class.simpleName })
            assertEquals(1, copy.readPage(1).history)
            assertEquals("x", copy.firstText(1))
            // The next change in the copy continues the numbers, so that they never run ahead of its history.
            val editor = rig.model.editor!!
            runBlocking { editor.show(1) }
            editor.setLineText("l1", "y")
            assertEquals(listOf(2), editor.history.pendingEntries.map { (it as EditEntry).seq })
        }

    @Test
    fun savingElsewhereKeepsAStatusThatWasNotSaved() =
        withRig { rig ->
            val corrected = korrektor.format.PageStatus.CORRECTED
            rig.model.editor!!.setStatus(2, corrected) // never saved
            val parent = Files.createDirectory(rig.dir.resolve("elsewhere"))

            val worked = runBlocking { rig.model.saveElsewhere(parent) }

            assertTrue(worked, rig.model.closeProblem)
            assertEquals(mapOf(2 to corrected), Project.open(parent.resolve("b.korrektur")).statuses)
            assertEquals(mapOf(2 to corrected), rig.model.editor!!.statuses)
        }

    @Test
    fun aNameThatIsTakenGetsACopySuffix() =
        withRig { rig ->
            rig.model.editor!!.setLineText("l1", "x")
            val parent = Files.createDirectory(rig.dir.resolve("elsewhere"))
            Files.createDirectory(parent.resolve("b.korrektur"))
            val worked = runBlocking { rig.model.saveElsewhere(parent) }
            assertTrue(worked, rig.model.closeProblem)
            assertEquals(parent.resolve("b (copy).korrektur"), rig.model.project!!.folder)
        }

    @Test
    fun savingElsewhereFailsCleanlyWhenTheProjectFolderIsGone() =
        withRig { rig ->
            rig.model.editor!!.setLineText("l1", "x")
            val parent = Files.createDirectory(rig.dir.resolve("elsewhere"))
            // The project's PDF is gone, as if the disk had been removed: the project cannot be copied any more.
            rig.folder.resolve("source.pdf").toFile().apply {
                setWritable(true)
                delete()
            }

            val worked = runBlocking { rig.model.saveElsewhere(parent) }

            assertFalse(worked)
            assertTrue(rig.model.closeProblem!!.contains("another location"), rig.model.closeProblem)
            assertEquals(emptyList(), parent.toFile().list()!!.toList(), "nothing is left at the target")
            assertEquals(setOf(1), rig.model.editor!!.changedPages, "the changes are still in memory")
            assertEquals(rig.folder, rig.model.project!!.folder)
        }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (!condition()) {
            check(System.nanoTime() < deadline) { "timed out" }
            Thread.sleep(5)
        }
    }

    @Test
    fun whatIsTypedWhileTheSaveOfAClosingAppRunsIsSavedToo() =
        withRig { rig ->
            rig.gate = CountDownLatch(1)
            rig.model.editor!!.setLineText("l1", "eins")
            runBlocking {
                val closing = async(Dispatchers.Default) { rig.model.requestClose() }
                waitUntil { rig.written.isNotEmpty() } // the save is running, held at the gate
                rig.model.editor!!.setLineText("l1", "zwei") // typed during the save
                rig.gate!!.countDown()
                assertTrue(closing.await())
            }
            assertEquals(1, rig.exits)
            assertEquals("zwei", Project.open(rig.folder).firstText(1))
            assertEquals(emptySet(), rig.model.editor!!.changedPages)
        }

    @Test
    fun openingAnotherProjectWhileASaveIsRunningDoesNotFreezeTheWindow() =
        withRig { rig ->
            val other = otherProject(rig)
            assertTimeoutPreemptively(Duration.ofSeconds(60)) {
                runComposeUiTest {
                    setContent { KeepSaving(rig.model.saver, null) }
                    rig.gate = CountDownLatch(1)
                    runOnIdle { rig.model.editor!!.setLineText("l1", "neu") }
                    waitUntil(timeoutMillis = 10_000) { rig.written.isNotEmpty() } // the timer save, held at the gate
                    Thread {
                        // the disk answers a moment later
                        Thread.sleep(500)
                        rig.gate!!.countDown()
                    }.start()
                    // On the UI thread, as the menu does it: waits for the save, which must be able to finish.
                    runOnUiThread { rig.model.openProject(other) }
                    runOnIdle { assertNull(rig.model.error, rig.model.error) }
                    assertEquals(other, rig.model.project!!.folder)
                }
            }
            assertEquals("neu", Project.open(rig.folder).firstText(1))
        }

    @Test
    fun savingIntoTheProjectFolderItselfIsRefused() =
        withRig { rig ->
            rig.model.editor!!.setLineText("l1", "x")
            val inside = Files.createDirectory(rig.folder.resolve("elsewhere"))
            assertFalse(runBlocking { rig.model.saveElsewhere(inside) })
            assertTrue(rig.model.closeProblem!!.contains("into itself"), rig.model.closeProblem)
            assertEquals(rig.folder, rig.model.project!!.folder, "nothing changed")
            assertEquals(emptyList(), Files.list(inside).use { it.toList() }, "nothing was copied into it")
        }

    @Test
    fun aSecondSaveElsewhereWhileOneIsWorkingDoesNothing() =
        withRig { rig ->
            rig.model.copying = true
            val parent = Files.createDirectory(rig.dir.resolve("parent"))
            assertFalse(runBlocking { rig.model.saveElsewhere(parent) })
            assertEquals(emptyList(), Files.list(parent).use { it.toList() })
        }
}
