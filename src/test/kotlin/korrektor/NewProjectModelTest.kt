package korrektor

import korrektor.format.Metadata
import korrektor.format.Project
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NewProjectModelTest {
    private val pdfBytes = "%PDF-1.4\n% stand-in".toByteArray()

    /** Runs work only when told to, so a test can look at the state while creating. */
    private class ManualDispatcher : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            queue.addLast(block)
        }

        fun runAll() {
            while (queue.isNotEmpty()) queue.removeFirst().run()
        }
    }

    private fun withDir(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("newprojectmodel")
        try {
            block(dir)
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    private fun pdf(
        dir: Path,
        name: String = "Some_Book.pdf",
    ): Path = dir.resolve(name).also { it.writeBytes(pdfBytes) }

    private fun newModel(
        app: AppModel,
        io: CoroutineDispatcher = Dispatchers.Unconfined,
    ) = NewProjectModel(app, CoroutineScope(Dispatchers.Unconfined), io)

    private fun NewProjectModel.createAndWait() = runBlocking { create()?.join() }

    // --- what the user sees before creating ---------------------------------------------------------------

    @Test
    fun createIsDisabledUntilAPdfIsChosenAndTheTitleIsNotBlank() =
        withDir { dir ->
            val model = newModel(AppModel())
            assertFalse(model.canCreate)
            assertNull(model.create())

            model.selectPdf(pdf(dir))
            assertTrue(model.canCreate)

            model.updateTitle("   ")
            assertFalse(model.canCreate)
            assertNull(model.create())

            model.updateTitle("A title")
            assertTrue(model.canCreate)
        }

    @Test
    fun choosingAPdfSuggestsTheTitleAndTheLocation() =
        withDir { dir ->
            val model = newModel(AppModel())
            model.selectPdf(pdf(dir, "Abhandlung_über_den_kleinen_Krieg.pdf"))

            assertEquals("Abhandlung über den kleinen Krieg", model.title)
            assertEquals(dir, model.location)
            assertEquals(dir.resolve("Abhandlung über den kleinen Krieg.korrektur"), model.projectFolder)
        }

    @Test
    fun theSuggestionCanBeEditedAndTheLocationChanged() =
        withDir { dir ->
            val elsewhere = dir.resolve("elsewhere").also { it.createDirectories() }
            val model = newModel(AppModel())
            model.selectPdf(pdf(dir))

            model.updateTitle("My own title")
            model.selectLocation(elsewhere)

            assertEquals("My own title", model.title)
            assertEquals(elsewhere.resolve("My own title.korrektur"), model.projectFolder)
        }

    @Test
    fun aTitleTypedBeforeChoosingThePdfIsNotOverwritten() =
        withDir { dir ->
            val model = newModel(AppModel())
            model.updateTitle("Typed first")

            model.selectPdf(pdf(dir))

            assertEquals("Typed first", model.title)
        }

    @Test
    fun aLocationChosenBeforeChoosingThePdfIsNotOverwritten() =
        withDir { dir ->
            val elsewhere = dir.resolve("elsewhere").also { it.createDirectories() }
            val model = newModel(AppModel())
            model.selectLocation(elsewhere)

            model.selectPdf(pdf(dir))

            assertEquals(elsewhere, model.location)
        }

    @Test
    fun aSuggestedTitleFollowsASecondPdfButAClearedTitleGetsASuggestion() =
        withDir { dir ->
            val model = newModel(AppModel())
            model.selectPdf(pdf(dir, "First_Book.pdf"))
            model.selectPdf(pdf(dir, "Second_Book.pdf"))
            assertEquals("Second Book", model.title)

            model.updateTitle("Typed")
            model.selectPdf(pdf(dir, "Third_Book.pdf"))
            assertEquals("Typed", model.title)

            model.updateTitle("")
            model.selectPdf(pdf(dir, "Fourth_Book.pdf"))
            assertEquals("Fourth Book", model.title)
        }

    // --- creating -----------------------------------------------------------------------------------------

    @Test
    fun creatingMakesTheProjectAndOpensIt() =
        withDir { dir ->
            val app = AppModel().also { it.openNewProject() }
            val model = newModel(app)
            model.selectPdf(pdf(dir))
            model.updateTitle("Abhandlung")
            model.author = "Ewald"
            // Free text: nothing about these values is checked or changed.
            model.year = "ca. 1790"
            model.source = "https://books.google.de/books?id=MhRLAAAAcAAJ"

            model.createAndWait()

            val project = assertNotNull(app.project)
            assertEquals(dir.resolve("Abhandlung.korrektur"), project.folder)
            assertEquals(
                Metadata("Abhandlung", "Ewald", "ca. 1790", "https://books.google.de/books?id=MhRLAAAAcAAJ"),
                project.metadata,
            )
            assertEquals(pdfBytes.toList(), project.sourcePdf.readBytes().toList())
            assertEquals("Abhandlung – korrektor", windowTitle(app.project))
            assertEquals(
                Metadata("Abhandlung", "Ewald", "ca. 1790", "https://books.google.de/books?id=MhRLAAAAcAAJ"),
                Project.open(project.folder).metadata,
            )
            assertFalse(app.newProjectOpen)
            assertFalse(model.busy)
            assertNull(model.error)
        }

    @Test
    fun yearsLikeASingleQuestionMarkAreKept() =
        withDir { dir ->
            val app = AppModel()
            val model = newModel(app)
            model.selectPdf(pdf(dir))
            model.year = "1785?"
            model.createAndWait()
            assertEquals("1785?", app.project?.metadata?.year)
        }

    @Test
    fun anExistingProjectFolderIsAnErrorAndTheDialogStaysOpen() =
        withDir { dir ->
            val app = AppModel().also { it.openNewProject() }
            val model = newModel(app)
            model.selectPdf(pdf(dir))
            model.updateTitle("Same")
            model.createAndWait()
            val first = assertNotNull(app.project)
            val before = first.folder.resolve("project.json").readBytes()

            val app2 = AppModel().also { it.openNewProject() }
            val second = newModel(app2)
            second.selectPdf(pdf(dir, "Other.pdf"))
            second.updateTitle("Same")
            second.createAndWait()

            assertTrue("already exists" in second.error!!, second.error)
            assertNull(app2.project)
            assertTrue(app2.newProjectOpen)
            assertFalse(second.busy)
            assertEquals(
                before.toList(),
                first.folder
                    .resolve("project.json")
                    .readBytes()
                    .toList(),
            )
        }

    @Test
    fun aLocationThatDoesNotExistIsAnErrorAndNothingIsCreated() =
        withDir { dir ->
            val app = AppModel()
            val model = newModel(app)
            model.selectPdf(pdf(dir))
            model.selectLocation(dir.resolve("not-there"))

            model.createAndWait()

            assertTrue("does not exist" in model.error!!, model.error)
            assertNull(app.project)
            assertFalse(dir.resolve("not-there").exists())
        }

    @Test
    fun aPdfThatVanishedIsAnErrorAndNothingIsCreated() =
        withDir { dir ->
            val app = AppModel()
            val model = newModel(app)
            val source = pdf(dir)
            model.selectPdf(source)
            source.deleteExisting()

            model.createAndWait()

            assertTrue("does not exist" in model.error!!, model.error)
            assertNull(app.project)
            assertEquals(emptyList(), dir.listDirectoryEntries().map { it.name })
        }

    @Test
    fun anErrorGoesAwayWhenTheUserChangesSomething() =
        withDir { dir ->
            val model = newModel(AppModel())
            val source = pdf(dir)
            model.selectPdf(source)
            source.deleteExisting()
            model.createAndWait()
            assertNotNull(model.error)

            model.updateTitle("Another title")

            assertNull(model.error)
        }

    // --- busy ---------------------------------------------------------------------------------------------

    @Test
    fun whileCreatingTheModelIsBusyAndCannotStartAgain() =
        withDir { dir ->
            val manual = ManualDispatcher()
            val app = AppModel()
            val model = newModel(app, manual)
            model.selectPdf(pdf(dir))

            val job = model.create()

            assertNotNull(job)
            assertTrue(model.busy)
            assertFalse(model.canCreate)
            assertNull(model.create())
            assertNull(app.project)

            manual.runAll()

            assertFalse(model.busy)
            assertNotNull(app.project)
            assertEquals(1, dir.listDirectoryEntries("*.korrektur").size)
        }

    @Test
    fun theModelIsNotBusyAfterAFailure() =
        withDir { dir ->
            val manual = ManualDispatcher()
            val model = newModel(AppModel(), manual)
            val source = pdf(dir)
            model.selectPdf(source)
            source.deleteExisting()

            model.create()
            assertTrue(model.busy)
            manual.runAll()

            assertFalse(model.busy)
            assertNotNull(model.error)
            assertTrue(model.canCreate)
        }
}
