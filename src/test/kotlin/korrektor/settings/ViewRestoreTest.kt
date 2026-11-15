package korrektor.settings

import androidx.compose.runtime.snapshots.Snapshot
import korrektor.AppModel
import korrektor.format.Metadata
import korrektor.format.PageInfo
import korrektor.format.Project
import korrektor.keepViewSaved
import korrektor.saveView
import korrektor.viewer.PageSize
import korrektor.viewer.SavedView
import korrektor.viewer.SavedZoom
import korrektor.viewer.ViewerState
import korrektor.viewer.ZoomMode
import korrektor.viewer.fitPage
import korrektor.viewer.restore
import korrektor.viewer.saved
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Page and zoom come back when a project is opened again, and nothing of it goes into the project. */
class ViewRestoreTest {
    private fun withDir(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("viewrestore")
        try {
            block(dir)
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    /** A project of [pages] pages with a stand-in PDF: the viewer's state needs no PDF to be restored. */
    private fun project(
        dir: Path,
        name: String,
        pages: Int = 20,
    ): Path {
        val pdf = dir.resolve("$name.pdf").also { Files.write(it, "%PDF-1.4\n".toByteArray()) }
        return Project
            .create(
                dir.resolve("$name.korrektur"),
                pdf,
                (1..pages).map { PageInfo(it) },
                Metadata(name),
            ).folder
    }

    // --- the state ----------------------------------------------------------------------------------------

    @Test
    fun everyZoomModeIsSavedAndRestored() {
        for (zoom in listOf(
            SavedZoom(ZoomMode.FIT_WIDTH),
            SavedZoom(ZoomMode.FIT_PAGE),
            SavedZoom(ZoomMode.FIXED, 1.75),
        )) {
            val state = ViewerState(pageCount = 20)
            state.restore(SavedView(8, zoom))
            state.setViewport(800, 600)
            state.setPage(PageSize(300.0, 480.0), null)

            assertEquals(SavedView(8, zoom), state.saved(), "$zoom")
        }
    }

    @Test
    fun aFixedScaleIsRestoredBeforeTheWindowOrThePageIsKnown() {
        val state = ViewerState(pageCount = 20)

        state.restore(SavedView(2, SavedZoom(ZoomMode.FIXED, 2.5)))
        state.setViewport(800, 600)
        state.setPage(PageSize(300.0, 480.0), 6.0)

        assertEquals(ZoomMode.FIXED, state.mode)
        assertEquals(2.5, state.scale, 1e-9)
        assertEquals(2, state.page)
    }

    @Test
    fun aPageBeyondTheBookBecomesTheLastPageAndOneBelowOneTheFirst() {
        val state = ViewerState(pageCount = 20)

        state.restore(SavedView(99, SavedZoom(ZoomMode.FIT_WIDTH)))
        assertEquals(20, state.page)
        state.restore(SavedView(0, SavedZoom(ZoomMode.FIT_WIDTH)))
        assertEquals(1, state.page)
        state.restore(SavedView(-5, SavedZoom(ZoomMode.FIT_WIDTH)))
        assertEquals(1, state.page)
    }

    @Test
    fun aFitModeIsSavedWithoutAScale() {
        val state = ViewerState(pageCount = 3)
        state.setViewport(400, 300)
        state.setPage(PageSize(200.0, 300.0), null)
        state.fitPage()

        assertEquals(SavedView(1, SavedZoom(ZoomMode.FIT_PAGE, null)), state.saved())
    }

    // --- through the app ----------------------------------------------------------------------------------

    @Test
    fun aProjectOpensAtTheViewItWasLeftIn() =
        withDir { dir ->
            val file = dir.resolve("config/settings.json")
            val folder = project(dir, "book")
            val first = AppModel(settings = AppSettings(file))
            first.openProject(folder)
            first.viewer!!.goTo(5)
            first.viewer!!.zoomTo(1.5)
            first.saveView()

            val second = AppModel(settings = AppSettings(file)) // the next session
            second.openProject(folder)

            assertEquals(5, second.viewer!!.page)
            assertEquals(ZoomMode.FIXED, second.viewer!!.mode)
            assertEquals(
                1.5,
                second.viewer!!
                    .saved()
                    .zoom.scale!!,
                1e-9,
            )
        }

    @Test
    fun aProjectThatWasNeverViewedOpensAtPageOneFittingTheWidth() =
        withDir { dir ->
            val model = AppModel(settings = AppSettings(dir.resolve("settings.json")))
            model.openProject(project(dir, "book"))

            assertEquals(SavedView(1, SavedZoom(ZoomMode.FIT_WIDTH)), model.viewer!!.saved())
        }

    @Test
    fun openingAnotherProjectSavesTheViewOfTheFirst() =
        withDir { dir ->
            val file = dir.resolve("settings.json")
            val a = project(dir, "a")
            val b = project(dir, "b")
            val model = AppModel(settings = AppSettings(file))
            model.openProject(a)
            model.viewer!!.goTo(7)

            model.openProject(b) // no explicit save

            assertEquals(7, AppSettings(file).viewOf(a)!!.page)
            assertEquals(1, model.viewer!!.page, "b starts at its own view")
        }

    @Test
    fun twoProjectsKeepTheirOwnViews() =
        withDir { dir ->
            val file = dir.resolve("settings.json")
            val a = project(dir, "a")
            val b = project(dir, "b")
            val model = AppModel(settings = AppSettings(file))
            model.openProject(a)
            model.viewer!!.goTo(3)
            model.openProject(b)
            model.viewer!!.goTo(9)
            model.saveView()

            val again = AppModel(settings = AppSettings(file))
            again.openProject(a)
            assertEquals(3, again.viewer!!.page)
            again.openProject(b)
            assertEquals(9, again.viewer!!.page)
        }

    @Test
    fun aSavedPageBeyondAShorterProjectFallsBackToItsLastPage() =
        withDir { dir ->
            val file = dir.resolve("settings.json")
            val folder = project(dir, "book", pages = 20)
            AppSettings(file).rememberView(folder, SavedView(18, SavedZoom(ZoomMode.FIT_WIDTH)))
            Files.walk(dir).use { stream ->
                stream.filter { it.name == "book.korrektur" }.toList().forEach { it.toFile().setWritable(true) }
            }
            val shorter = project(dir, "short", pages = 4)
            AppSettings(file).rememberView(shorter, SavedView(18, SavedZoom(ZoomMode.FIT_WIDTH)))

            val model = AppModel(settings = AppSettings(file))
            model.openProject(shorter)

            assertEquals(4, model.viewer!!.page)
        }

    @Test
    fun theProjectFolderIsNotTouchedByViewingAndSavingTheView() =
        withDir { dir ->
            val folder = project(dir, "book")

            fun snapshot() =
                folder.walk().filter { Files.isRegularFile(it) }.associate {
                    it.toString() to
                        it.readBytes()
                }
            val before = snapshot()
            val model = AppModel(settings = AppSettings(dir.resolve("config/settings.json")))

            model.openProject(folder)
            model.viewer!!.goTo(6)
            model.viewer!!.zoomTo(2.0)
            model.saveView()
            model.openProject(project(dir, "other"))

            val after = snapshot()
            assertEquals(before.keys, after.keys, "no file added")
            for ((name, bytes) in before) assertContentEquals(bytes, after.getValue(name), name)
        }

    // --- when it is saved ---------------------------------------------------------------------------------

    @Test
    fun changesWithinTheDelayAreWrittenOnceAfterTheLastOne() =
        withDir { dir ->
            val settings = AppSettings(dir.resolve("settings.json"))
            val folder = project(dir, "book")
            val model = AppModel(settings = settings)
            model.openProject(folder)
            val viewer = model.viewer!!
            runBlocking {
                val saver = launch { model.keepViewSaved(delayMs = DELAY) }
                delay(SETTLE) // the collector is running
                for (page in 2..6) {
                    viewer.goTo(page)
                    Snapshot.sendApplyNotifications()
                    delay(DELAY / 10) // far less than the delay: one burst
                }
                assertEquals(0, settings.writeCount, "nothing is written during the burst")

                delay(DELAY * 3)
                assertEquals(1, settings.writeCount, "one write for the whole burst")
                assertEquals(6, settings.viewOf(folder)!!.page, "of the last state")

                viewer.goTo(9)
                Snapshot.sendApplyNotifications()
                delay(DELAY * 3)
                assertEquals(2, settings.writeCount)
                assertEquals(9, settings.viewOf(folder)!!.page)
                saver.cancel()
            }
        }

    @Test
    fun nothingIsWrittenForAProjectThatWasOnlyOpened() =
        withDir { dir ->
            val settings = AppSettings(dir.resolve("settings.json"))
            val model = AppModel(settings = settings)
            model.openProject(project(dir, "book"))
            runBlocking {
                val saver = launch { model.keepViewSaved(delayMs = DELAY) }
                delay(DELAY * 3)
                assertEquals(0, settings.writeCount)
                saver.cancel()
            }
            assertNotNull(model.viewer)
        }

    private companion object {
        const val DELAY = 150L
        const val SETTLE = 100L
    }

    @Test
    fun goingAwayAndBackToTheStartingViewWithinTheDelayWritesTheStartingView() =
        withDir { dir ->
            val settings = AppSettings(dir.resolve("settings.json"))
            val folder = project(dir, "book")
            val model = AppModel(settings = settings)
            model.openProject(folder)
            val viewer = model.viewer!!
            viewer.goTo(12) // the view the project was left in
            model.saveView()
            runBlocking {
                val saver = launch { model.keepViewSaved(delayMs = DELAY) }
                delay(SETTLE)
                viewer.goTo(13)
                Snapshot.sendApplyNotifications()
                delay(DELAY / 10)
                viewer.goTo(12) // back, within the delay
                Snapshot.sendApplyNotifications()

                delay(DELAY * 3)
                assertEquals(12, settings.viewOf(folder)!!.page, "not the page the user has already left")
                saver.cancel()
            }
        }
}
