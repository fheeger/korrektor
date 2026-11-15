package korrektor

import korrektor.format.FormatException
import korrektor.format.Metadata
import korrektor.format.Project
import korrektor.importer.ImportResult
import korrektor.importer.PageNote
import korrektor.importer.PdfImport
import korrektor.pdf.PdfException
import korrektor.pdf.TestPage
import korrektor.pdf.TestPdfs
import korrektor.pdf.TestText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException
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
            val model = newModel(AppModel(FakeFactory()))
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
            val model = newModel(AppModel(FakeFactory()))
            model.selectPdf(pdf(dir, "Abhandlung_über_den_kleinen_Krieg.pdf"))

            assertEquals("Abhandlung über den kleinen Krieg", model.title)
            assertEquals(dir, model.location)
            assertEquals(dir.resolve("Abhandlung über den kleinen Krieg.korrektur"), model.projectFolder)
        }

    @Test
    fun theSuggestionCanBeEditedAndTheLocationChanged() =
        withDir { dir ->
            val elsewhere = dir.resolve("elsewhere").also { it.createDirectories() }
            val model = newModel(AppModel(FakeFactory()))
            model.selectPdf(pdf(dir))

            model.updateTitle("My own title")
            model.selectLocation(elsewhere)

            assertEquals("My own title", model.title)
            assertEquals(elsewhere.resolve("My own title.korrektur"), model.projectFolder)
        }

    @Test
    fun aTitleTypedBeforeChoosingThePdfIsNotOverwritten() =
        withDir { dir ->
            val model = newModel(AppModel(FakeFactory()))
            model.updateTitle("Typed first")

            model.selectPdf(pdf(dir))

            assertEquals("Typed first", model.title)
        }

    @Test
    fun aLocationChosenBeforeChoosingThePdfIsNotOverwritten() =
        withDir { dir ->
            val elsewhere = dir.resolve("elsewhere").also { it.createDirectories() }
            val model = newModel(AppModel(FakeFactory()))
            model.selectLocation(elsewhere)

            model.selectPdf(pdf(dir))

            assertEquals(elsewhere, model.location)
        }

    @Test
    fun aSuggestedTitleFollowsASecondPdfButAClearedTitleGetsASuggestion() =
        withDir { dir ->
            val model = newModel(AppModel(FakeFactory()))
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
            val app = AppModel(FakeFactory()).also { it.openNewProject() }
            val model = newModel(app)
            model.selectPdf(pdf(dir))
            model.updateTitle("Abhandlung")
            model.updateAuthor(
                "Ewald",
            )
            // Free text: nothing about these values is checked or changed.
            model.updateYear(
                "ca. 1790",
            )
            model.updateSource(
                "https://books.google.de/books?id=MhRLAAAAcAAJ",
            )

            model.createAndWait()

            val project = assertNotNull(app.project)
            assertEquals(dir.resolve("Abhandlung.korrektur"), project.folder)
            assertEquals(
                Metadata("Abhandlung", "Ewald", "ca. 1790", "https://books.google.de/books?id=MhRLAAAAcAAJ"),
                project.metadata,
            )
            assertEquals(pdfBytes.toList(), project.sourcePdf.readBytes().toList())
            assertEquals("Abhandlung – korrektor", windowTitle(app.project, app.metadata))
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
            val app = AppModel(FakeFactory())
            val model = newModel(app)
            model.selectPdf(pdf(dir))
            model.updateYear(
                "1785?",
            )
            model.createAndWait()
            assertEquals("1785?", app.project?.metadata?.year)
        }

    @Test
    fun anExistingProjectFolderIsAnErrorAndTheDialogStaysOpen() =
        withDir { dir ->
            val app = AppModel(FakeFactory()).also { it.openNewProject() }
            val model = newModel(app)
            model.selectPdf(pdf(dir))
            model.updateTitle("Same")
            model.createAndWait()
            val first = assertNotNull(app.project)
            val before = first.folder.resolve("project.json").readBytes()

            val app2 = AppModel(FakeFactory()).also { it.openNewProject() }
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
            val app = AppModel(FakeFactory())
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
            val app = AppModel(FakeFactory())
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
            val model = newModel(AppModel(FakeFactory()))
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
            val app = AppModel(FakeFactory())
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
    fun theWindowCannotBeClosedWhileCreating() =
        withDir { dir ->
            val manual = ManualDispatcher()
            val app = AppModel(FakeFactory())
            val model = newModel(app, manual)
            model.selectPdf(pdf(dir))
            assertTrue(app.canClose)

            model.create()
            assertFalse(app.canClose)

            manual.runAll()
            assertTrue(app.canClose)
        }

    @Test
    fun anUnexpectedFailureBecomesAMessageAndTheModelIsNotStuck() =
        withDir { dir ->
            val app = AppModel(FakeFactory(createFails = IllegalStateException("something unexpected")))
            val model = newModel(app)
            model.selectPdf(pdf(dir))

            model.createAndWait()

            assertTrue("something unexpected" in model.error!!, model.error)
            assertFalse(model.busy)
            assertTrue(app.canClose)
            assertNull(app.project)
            assertTrue(model.canCreate, "the user can try again")
        }

    @Test
    fun cancellingWhileCreatingIsNotAnError() =
        withDir { dir ->
            val manual = ManualDispatcher()
            val app = AppModel(FakeFactory())
            val model = newModel(app, manual)
            model.selectPdf(pdf(dir))

            val job = model.create()
            job?.cancel()
            manual.runAll()
            runBlocking { job?.join() }

            assertNull(model.error)
            assertFalse(model.busy)
            assertTrue(app.canClose)
        }

    @Test
    fun theModelIsNotBusyAfterAFailure() =
        withDir { dir ->
            val manual = ManualDispatcher()
            val model = newModel(AppModel(FakeFactory()), manual)
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

    // --- the import ---------------------------------------------------------------------------------------

    /** A real PDF of [count] pages; the second page has no text. */
    private fun realPdf(
        dir: Path,
        count: Int = 3,
    ): Path =
        dir.resolve("Real_Book.pdf").also { file ->
            val pages =
                List(count) { i ->
                    if (i ==
                        1
                    ) {
                        TestPage()
                    } else {
                        TestPage(texts = listOf(TestText("Page ${i + 1}", 20f, 200f)))
                    }
                }
            TestPdfs.write(file, pages)
        }

    private fun projectFolders(dir: Path) = dir.listDirectoryEntries("*.korrektur")

    @Test
    fun theProgressOfTheImportIsShown() =
        withDir { dir ->
            lateinit var model: NewProjectModel
            val seen = mutableListOf<Triple<Int, Int, Boolean>>()
            val factory =
                FakeFactory(importer = { _, progress, _ ->
                    for (n in 1..3) {
                        progress(n, 3)
                        seen += Triple(model.importedPages, model.totalPages, model.importing)
                    }
                    ImportResult(3)
                })
            model = newModel(AppModel(factory))
            model.selectPdf(pdf(dir))

            model.createAndWait()

            assertEquals(listOf(Triple(1, 3, true), Triple(2, 3, true), Triple(3, 3, true)), seen)
            assertFalse(model.busy)
            assertFalse(model.importing)
        }

    @Test
    fun theProjectOpensWithASummaryOfThePagesThatHadNoTextAndTheDialogCloses() =
        withDir { dir ->
            val factory = FakeFactory(importer = { _, _, _ -> ImportResult(3, listOf(PageNote(2, PdfImport.NO_TEXT))) })
            val app = AppModel(factory).also { it.openNewProject() }
            val model = newModel(app)
            model.selectPdf(pdf(dir))

            model.createAndWait()

            assertNotNull(app.project)
            assertEquals("1 page has no text: 2.", app.notice)
            assertFalse(app.newProjectOpen)
            app.dismissNotice()
            assertNull(app.notice)
        }

    @Test
    fun cancellingDeletesTheProjectAndLeavesTheDialogAsItWas() =
        withDir { dir ->
            lateinit var model: NewProjectModel
            val factory =
                FakeFactory(importer = { _, progress, isCancelled ->
                    for (n in 1..5) {
                        if (isCancelled()) throw CancellationException("stop")
                        progress(n, 5)
                        if (n == 2) model.cancel() // the user clicks "Cancel" while page 2 is done
                    }
                    ImportResult(5)
                })
            val app = AppModel(factory).also { it.openNewProject() }
            model = newModel(app)
            model.selectPdf(pdf(dir))
            model.updateAuthor("Someone")

            model.createAndWait()

            assertEquals("Import cancelled.", model.error)
            assertEquals(emptyList(), projectFolders(dir), "the project folder is gone")
            assertEquals(
                listOf("Some_Book.pdf"),
                dir.listDirectoryEntries().map { it.name },
                "the target folder is as it was",
            )
            assertNull(app.project)
            assertTrue(app.newProjectOpen, "the dialog stays open")
            assertEquals("Some Book", model.title)
            assertEquals("Someone", model.author)
            assertFalse(model.busy)
            assertTrue(app.canClose)
            assertTrue(model.canCreate, "the user can create again")
        }

    @Test
    fun aFailedImportDeletesTheProjectAndShowsTheReason() =
        withDir { dir ->
            val failures =
                listOf(
                    PdfException("The PDF is damaged") to "The PDF is damaged",
                    FormatException("A page is wrong") to "A page is wrong",
                )
            for ((failure, message) in failures) {
                val model = newModel(AppModel(FakeFactory(importer = { _, _, _ -> throw failure })))
                model.selectPdf(pdf(dir))

                model.createAndWait()

                assertEquals(message, model.error)
                assertEquals(emptyList(), projectFolders(dir), "nothing is left behind")
                assertFalse(model.busy)
            }
        }

    @Test
    fun anUnexpectedImportFailureIsAMessageAndDeletesTheProject() =
        withDir { dir ->
            val model = newModel(AppModel(FakeFactory(importer = { _, _, _ -> error("boom") })))
            model.selectPdf(pdf(dir))

            model.createAndWait()

            assertTrue("Importing the text failed unexpectedly: boom" in model.error!!, model.error)
            assertEquals(emptyList(), projectFolders(dir))
            assertTrue(model.canCreate)
        }

    @Test
    fun cancelDoesNothingWhenNothingIsRunningAndDoesNotSpoilTheNextCreate() =
        withDir { dir ->
            val app = AppModel(FakeFactory())
            val model = newModel(app)
            model.selectPdf(pdf(dir))

            model.cancel()
            model.createAndWait()

            assertNotNull(app.project)
            assertNull(model.error)
        }

    @Test
    fun theWindowCannotBeClosedWhileTheTextIsImported() =
        withDir { dir ->
            lateinit var app: AppModel
            val during = mutableListOf<Boolean>()
            app =
                AppModel(
                    FakeFactory(importer = { _, _, _ ->
                        during += app.canClose
                        ImportResult(0)
                    }),
                )
            val model = newModel(app)
            model.selectPdf(pdf(dir))

            model.createAndWait()

            assertEquals(listOf(false), during)
            assertTrue(app.canClose)
        }

    @Test
    fun aRealImportOpensTheProjectWithAllItsPagesAndTheNote() =
        withDir { dir ->
            val app = AppModel().also { it.openNewProject() }
            val model = newModel(app, Dispatchers.IO)
            model.selectPdf(realPdf(dir))

            model.createAndWait()

            val project = assertNotNull(app.project)
            assertEquals(3, project.pages.size)
            assertEquals("1 page has no text: 2.", app.notice)
            assertEquals(PdfImport.NO_TEXT, project.readOriginalPage(2).note)
            val firstPage =
                project.readOriginalPage(1).regions.flatMap { it.lines }.map { l ->
                    l.words.joinToString(" ") { it.text }
                }
            assertEquals(listOf("Page 1"), firstPage)
            assertFalse(app.newProjectOpen)
        }

    @Test
    fun aRealImportThatIsCancelledLeavesNothingBehind() =
        withDir { dir ->
            lateinit var model: NewProjectModel
            val real = DefaultProjectFactory
            val factory =
                object : ProjectFactory {
                    override fun create(
                        location: Path,
                        pdf: Path,
                        metadata: Metadata,
                    ) = real.create(location, pdf, metadata)

                    override fun importText(
                        project: Project,
                        progress: (Int, Int) -> Unit,
                        isCancelled: () -> Boolean,
                    ) = real.importText(project, { done, total ->
                        progress(done, total)
                        if (done == 2) model.cancel()
                    }, isCancelled)
                }
            val app = AppModel(factory).also { it.openNewProject() }
            model = newModel(app, Dispatchers.IO)
            val source = realPdf(dir, count = 6)
            model.selectPdf(source)

            model.createAndWait()

            assertEquals("Import cancelled.", model.error)
            assertEquals(emptyList(), projectFolders(dir))
            assertEquals(listOf(source.name), dir.listDirectoryEntries().map { it.name })
            assertNull(app.project)
            assertTrue(app.newProjectOpen)
        }

    @Test
    fun aRealImportOfAPdfThatIsNotOneFailsWithItsMessageAndLeavesNothing() =
        withDir { dir ->
            val model = newModel(AppModel(), Dispatchers.IO)
            model.selectPdf(pdf(dir)) // a stand-in with a %PDF- header but no structure

            model.createAndWait()

            assertTrue("damaged" in model.error!!, model.error)
            assertEquals(emptyList(), projectFolders(dir))
        }
}
