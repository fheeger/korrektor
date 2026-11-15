package korrektor.editor

import korrektor.AppModel
import korrektor.format.FormatException
import korrektor.format.Metadata
import korrektor.format.PageLayer
import korrektor.format.Project
import korrektor.importer.PdfImport
import korrektor.pdf.TestPage
import korrektor.pdf.TestPdfs
import korrektor.pdf.TestText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readBytes
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookEditorTest {
    private fun withDir(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("bookeditor")
        try {
            block(dir)
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    /** A project of three pages made by the real import: two lines, no text, one line. */
    private fun project(dir: Path): Project {
        val pdf = dir.resolve("book.pdf")
        TestPdfs.write(
            pdf,
            listOf(
                TestPage(texts = listOf(TestText("Eins oben", 30f, 250f), TestText("Zwei unten", 30f, 230f))),
                TestPage(),
                TestPage(texts = listOf(TestText("Drei", 30f, 250f))),
            ),
        )
        val project = Project.create(dir.resolve("book.korrektur"), pdf, emptyList(), Metadata("Book"))
        PdfImport.run(project)
        return project
    }

    private fun editor(project: Project) = BookEditor(project::readPage, Dispatchers.IO)

    private fun BookEditor.texts() = lines.map { it.text }

    // --- showing pages ----------------------------------------------------------------------------------------

    @Test
    fun aShownPageIsTheCorrectedLayerOfTheProjectInReadingOrder() =
        withDir { dir ->
            val project = project(dir)
            val editor = editor(project)

            runBlocking { editor.show(1) }

            assertEquals(project.readPage(1), editor.layer)
            assertEquals(listOf("Eins oben", "Zwei unten"), editor.texts())
            assertEquals(1, editor.page)
            assertNull(editor.error)
        }

    @Test
    fun linesComeInTheOrderOfTheRegionsEvenIfTheyAreStoredAnotherWay() {
        val layer = PageEditsTestLayers.twoRegionsStoredInTheWrongOrder()
        val editor = BookEditor({ layer }, Dispatchers.IO)

        runBlocking { editor.show(1) }

        assertEquals(listOf("eins", "zwei", "drei"), editor.texts())
    }

    @Test
    fun aPageWithoutTextHasNoLinesButItsNote() =
        withDir { dir ->
            val editor = editor(project(dir))

            runBlocking { editor.show(2) }

            assertEquals(emptyList(), editor.lines)
            assertEquals("No text on this page.", editor.layer!!.note)
        }

    // --- edits ------------------------------------------------------------------------------------------------

    @Test
    fun anEditChangesTheLayerAndMarksThePageAndItSurvivesGoingAwayAndBack() =
        withDir { dir ->
            val project = project(dir)
            val editor = editor(project)
            runBlocking { editor.show(1) }
            runBlocking { editor.show(3) } // only looked at
            assertEquals(emptySet(), editor.changedPages, "looking is not changing")
            runBlocking { editor.show(1) }

            editor.setLineText(editor.lines[0].id, "Eins korrigiert")
            runBlocking {
                editor.show(2)
                editor.show(3)
                editor.show(1)
            }

            assertEquals(listOf("Eins korrigiert", "Zwei unten"), editor.texts(), "the edit was kept in memory")
            assertEquals(setOf(1), editor.changedPages)
            assertEquals(
                listOf("Eins oben", "Zwei unten"),
                project
                    .readPage(1)
                    .regions
                    .flatMap {
                        it.lines
                    }.map { it.text },
                "the file is as it was",
            )
        }

    @Test
    fun nothingIsWrittenToTheProject() =
        withDir { dir ->
            val project = project(dir)

            fun snapshot() =
                project.folder.walk().filter { Files.isRegularFile(it) }.associate {
                    it.toString() to
                        it.readBytes()
                }
            val before = snapshot()
            val editor = editor(project)

            runBlocking {
                editor.show(1)
                editor.setLineText(editor.lines[0].id, "ſo")
                editor.splitLine(editor.lines[0].id, 1)
                editor.addLineAfter(editor.lines[0].id, "neu")
                editor.show(2)
                editor.addLineAfter(null, "auf leerer Seite")
                editor.show(3)
            }

            val after = snapshot()
            assertEquals(before.keys, after.keys)
            for ((name, bytes) in before) assertContentEquals(bytes, after.getValue(name), name)
            assertEquals(setOf(1, 2), editor.changedPages)
        }

    @Test
    fun anEditThatChangesNothingIsNotAChange() =
        withDir { dir ->
            val editor = editor(project(dir))
            runBlocking { editor.show(1) }

            editor.setLineText(editor.lines[0].id, editor.lines[0].text)
            editor.joinWithPrevious(editor.lines[0].id) // the first line has nothing before it

            assertEquals(emptySet(), editor.changedPages)
        }

    @Test
    fun anAddedLineOnAPageWithoutTextCreatesTheFirstRegion() =
        withDir { dir ->
            val editor = editor(project(dir))
            runBlocking { editor.show(2) }

            editor.addLineAfter(null, "Anmerkung")

            assertEquals(listOf("Anmerkung"), editor.texts())
            assertEquals(
                "r1",
                editor.layer!!
                    .regions
                    .single()
                    .id,
            )
            assertEquals(setOf(2), editor.changedPages)
        }

    // --- loading ----------------------------------------------------------------------------------------------

    @Test
    fun theResultOfALoadThatWasLeftForAnotherPageIsNeverUsed() {
        val slow = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val first = PageEditsTestLayers.page(1, "erste")
        val second = PageEditsTestLayers.page(2, "zweite")
        val editor =
            BookEditor({ n ->
                if (n == 1) {
                    started.complete(Unit)
                    runBlocking { slow.await() }
                    first
                } else {
                    second
                }
            }, Dispatchers.IO)

        runBlocking {
            val loading = launch { editor.show(1) }
            started.await()
            loading.cancel() // the user went on to page 2
            editor.show(2)
            slow.complete(Unit) // the slow read of page 1 finishes now
            loading.join()
        }

        assertEquals(2, editor.page)
        assertEquals(second, editor.layer, "page 1 did not replace page 2")
    }

    @Test
    fun aLoadThatIsNotFinishedShowsNoLayer() {
        val release = CompletableDeferred<Unit>()
        val editor =
            BookEditor({
                runBlocking { release.await() }
                PageEditsTestLayers.page(1, "x")
            }, Dispatchers.IO)

        runBlocking {
            val loading = launch { editor.show(1) }
            Thread.sleep(100)
            assertNull(editor.layer)
            assertNull(editor.error)
            release.complete(Unit)
            loading.join()
        }

        assertNotNull(editor.layer)
    }

    @Test
    fun aPageThatCannotBeReadGivesAnErrorAndTheNextPageStillLoads() {
        val good = PageEditsTestLayers.page(2, "gut")
        val editor =
            BookEditor({ n ->
                if (n ==
                    1
                ) {
                    throw FormatException("The file of page 1 is damaged")
                } else {
                    good
                }
            }, Dispatchers.IO)

        runBlocking { editor.show(1) }
        assertNull(editor.layer)
        assertEquals("The file of page 1 is damaged", editor.error)
        assertEquals(emptyList(), editor.lines)
        editor.setLineText("l1", "nichts") // no page: nothing happens, no crash

        runBlocking { editor.show(2) }
        assertEquals(good, editor.layer)
        assertNull(editor.error)
    }

    // --- the active line and the cursor ---------------------------------------------------------------------

    @Test
    fun theActiveLineIsNoneAfterAPageChange() =
        withDir { dir ->
            val editor = editor(project(dir))
            runBlocking { editor.show(1) }
            editor.activate(editor.lines[1].id)
            assertEquals(editor.lines[1].id, editor.activeLineId)

            runBlocking { editor.show(3) }

            assertNull(editor.activeLineId)
        }

    @Test
    fun anUnknownLineCannotBecomeActive() =
        withDir { dir ->
            val editor = editor(project(dir))
            runBlocking { editor.show(1) }
            editor.activate("l99")
            assertNull(editor.activeLineId)
        }

    @Test
    fun splitJoinAddAndDeleteAskForTheCursorWhereItBelongs() =
        withDir { dir ->
            val editor = editor(project(dir))
            runBlocking { editor.show(1) }
            val first = editor.lines[0].id // "Eins oben"

            editor.splitLine(first, 4) // "Eins" | "oben"
            val newLine = editor.lines[1].id
            assertEquals(Focus(newLine, 0), editor.focusRequest!!.focus)
            assertEquals(newLine, editor.activeLineId)

            editor.joinWithPrevious(newLine)
            assertEquals(Focus(first, 4), editor.focusRequest!!.focus)
            assertEquals(first, editor.activeLineId)

            editor.addLineAfter(first, "x")
            val added = editor.lines[1].id
            assertEquals(Focus(added, 0), editor.focusRequest!!.focus)

            editor.deleteLine(added)
            assertEquals(first, editor.focusRequest!!.focus.lineId)
            assertEquals(first, editor.activeLineId)
        }

    @Test
    fun everyRequestIsNewEvenForTheSamePlace() =
        withDir { dir ->
            val editor = editor(project(dir))
            runBlocking { editor.show(1) }

            editor.requestFocus(editor.lines[0].id, 2)
            val first = editor.focusRequest!!
            editor.requestFocus(editor.lines[0].id, 2)

            assertTrue(editor.focusRequest!!.serial > first.serial)
        }

    @Test
    fun deletingTheOnlyLineLeavesNoActiveLine() =
        withDir { dir ->
            val editor = editor(project(dir))
            runBlocking { editor.show(3) }
            editor.activate(editor.lines[0].id)

            editor.deleteLine(editor.lines[0].id)

            assertEquals(emptyList(), editor.lines)
            assertNull(editor.activeLineId)
        }

    // --- in the app -------------------------------------------------------------------------------------------

    @Test
    fun theAppMakesAnEditorForTheOpenProjectAndAFreshOneForTheNext() =
        withDir { dir ->
            val model = AppModel()
            assertNull(model.editor)

            model.openProject(project(dir).folder)
            val first = assertNotNull(model.editor)
            runBlocking { first.show(1) }
            first.setLineText(first.lines[0].id, "geändert")

            model.openProject(project(dir.resolve("two").also { Files.createDirectories(it) }).folder)

            assertFalse(model.editor === first, "another project, another editor")
            assertEquals(emptySet(), model.editor!!.changedPages)
        }
}

/** Layers for the tests above. */
private object PageEditsTestLayers {
    fun page(
        number: Int,
        text: String,
    ): PageLayer =
        PageLayer(
            number,
            100.0,
            100.0,
            listOf(
                korrektor.format.Region(
                    "r1",
                    korrektor.format.RegionType.TEXT,
                    1,
                    null,
                    listOf(korrektor.format.Line("l1", null, null, listOf(korrektor.format.Word(text)))),
                ),
            ),
        )

    fun twoRegionsStoredInTheWrongOrder(): PageLayer {
        fun line(
            id: String,
            text: String,
        ) = korrektor.format.Line(id, null, null, listOf(korrektor.format.Word(text)))
        return PageLayer(
            1,
            100.0,
            100.0,
            listOf(
                korrektor.format.Region("r2", korrektor.format.RegionType.TEXT, 2, null, listOf(line("l3", "drei"))),
                korrektor.format.Region(
                    "r1",
                    korrektor.format.RegionType.TEXT,
                    1,
                    null,
                    listOf(line("l1", "eins"), line("l2", "zwei")),
                ),
            ),
        )
    }
}
