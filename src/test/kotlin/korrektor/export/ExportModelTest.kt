package korrektor.export

import korrektor.format.Metadata
import korrektor.format.PageStatus
import korrektor.search.layerOf
import korrektor.settings.AppSettings
import korrektor.settings.exportOptions
import korrektor.settings.rememberExportOptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The state of the export dialog: the options per format, the range, the note, and the export itself. */
class ExportModelTest {
    private val settings = AppSettings.inMemory()
    private var statuses: Map<Int, PageStatus> = emptyMap()
    private val done = ArrayList<String>()
    private var read: (Int) -> korrektor.format.PageLayer = { layerOf("Zeile $it", number = it) }

    private fun model(
        pages: Int = 10,
        scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
    ) = ExportModel(
        ExportBook(pages, Metadata("Der kleine Krieg")) { statuses },
        settings,
        BookExport({ read(it) }, Dispatchers.IO),
        scope,
        done::add,
    )

    private fun withTarget(block: (Path) -> Unit) {
        val dir = createTempDirectory("exportmodel")
        try {
            block(dir.resolve("out.txt"))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    private fun waitFor(condition: () -> Boolean) =
        runBlocking { withTimeout(10_000) { while (!condition()) delay(5) } }

    @Test
    fun theWholeBookAndTheDefaultsOfTheFormatAtFirst() {
        val model = model(pages = 165)
        assertEquals(ExportFormat.TEXT, model.format)
        assertEquals(ExportOptions.defaultsFor(ExportFormat.TEXT), model.options)
        assertEquals(1..165, model.range)
        model.selectFormat(ExportFormat.MARKDOWN)
        assertEquals(ExportOptions(ExportFormat.MARKDOWN, false, false, false), model.options)
    }

    @Test
    fun eachFormatKeepsItsOwnChoicesInTheDialogAndStartsWithWhatWasRemembered() {
        settings.rememberExportOptions(ExportOptions(ExportFormat.MARKDOWN, true, true, true))
        val model = model()
        model.selectFormat(ExportFormat.MARKDOWN)
        assertEquals(ExportOptions(ExportFormat.MARKDOWN, true, true, true), model.options)
        model.changeOptions(model.options.copy(header = false))
        model.selectFormat(ExportFormat.TEXT)
        assertEquals(ExportOptions.defaultsFor(ExportFormat.TEXT), model.options)
        model.changeOptions(model.options.copy(pageMarkers = false))
        model.selectFormat(ExportFormat.MARKDOWN)
        assertEquals(ExportOptions(ExportFormat.MARKDOWN, true, false, true), model.options)
        model.selectFormat(ExportFormat.TEXT)
        assertEquals(ExportOptions(ExportFormat.TEXT, false, true, true), model.options)
    }

    @Test
    fun theRangeMustBeNumbersInTheBookWithTheFirstNotAfterTheLast() {
        val model = model(pages = 10)
        assertNull(model.rangeProblem)
        for ((from, to) in listOf("0" to "10", "1" to "11", "a" to "5", "" to "5", "3" to "", "-1" to "4")) {
            model.updateFrom(from)
            model.updateTo(to)
            assertNull(model.range, "$from-$to")
            assertEquals("Enter page numbers from 1 to 10.", model.rangeProblem)
            assertFalse(model.canExport)
        }
        model.updateFrom("6")
        model.updateTo("5")
        assertEquals("The first page must not come after the last.", model.rangeProblem)
        model.updateFrom(" 5 ")
        model.updateTo("5")
        assertEquals(5..5, model.range)
        assertTrue(model.canExport)
    }

    @Test
    fun theNoteSaysHowManyPagesInTheRangeAreStillUnchecked() {
        val model = model(pages = 165)
        statuses = (1..127).associateWith { PageStatus.CORRECTED }
        assertEquals("38 of 165 pages are still unchecked. They are exported as they are.", model.uncheckedNote)
        model.updateFrom("120")
        model.updateTo("165")
        assertEquals(
            "38 of 46 pages in the range are still unchecked. They are exported as they are.",
            model.uncheckedNote,
        )
        model.updateFrom("1")
        model.updateTo("127")
        assertNull(model.uncheckedNote)
        model.updateTo("128")
        assertEquals(
            "1 of 128 pages in the range is still unchecked. They are exported as they are.",
            model.uncheckedNote,
        )
        model.updateFrom("128")
        assertEquals("1 of 1 page is still unchecked. They are exported as they are.", model.uncheckedNote)
        model.updateTo("x")
        assertNull(model.uncheckedNote)
    }

    @Test
    fun aPageWithAnotherStatusThanUncheckedIsNotCounted() {
        statuses = mapOf(1 to PageStatus("proofread"))
        val model = model(pages = 2)
        assertEquals("1 of 2 pages is still unchecked. They are exported as they are.", model.uncheckedNote)
    }

    @Test
    fun startingWritesTheRangeRemembersTheOptionsAndReportsWhereItWent() =
        withTarget { target ->
            val model = model()
            model.selectFormat(ExportFormat.MARKDOWN)
            model.changeOptions(model.options.copy(pageMarkers = true))
            model.updateFrom("2")
            model.updateTo("3")
            model.start(target)
            waitFor { done.isNotEmpty() }
            assertEquals("\\[Page 2\\]\n\nZeile 2\n\n\\[Page 3\\]\n\nZeile 3\n", target.readText())
            assertEquals("Exported 2 pages to $target", done.single())
            assertFalse(model.busy)
            assertNull(model.error)
            assertEquals(
                ExportOptions(ExportFormat.MARKDOWN, true, false, false),
                settings.exportOptions(ExportFormat.MARKDOWN),
            )
        }

    @Test
    fun aFailureIsShownAndNothingIsWritten() =
        withTarget { target ->
            read =
                { if (it == 2) throw korrektor.format.FormatException("broken") else layerOf("Zeile $it", number = it) }
            val model = model()
            model.start(target)
            waitFor { model.error != null && !model.busy }
            assertTrue(model.error!!.contains("Page 2"), model.error)
            assertTrue(done.isEmpty())
            assertFalse(target.exists())
        }

    @Test
    fun cancellingStopsTheExportAndLeavesTheOldFile() =
        withTarget { target ->
            target.toFile().writeText("old")
            val inPage3 = CompletableDeferred<Unit>()
            read = {
                if (it == 3) inPage3.complete(Unit)
                Thread.sleep(30)
                layerOf("Zeile $it", number = it)
            }
            val model = model()
            model.start(target)
            assertTrue(model.busy)
            runBlocking { withTimeout(10_000) { inPage3.await() } }
            model.cancel()
            waitFor { !model.busy }
            assertEquals("old", target.readText())
            assertTrue(done.isEmpty())
            assertNull(model.error)
        }

    @Test
    fun whileItRunsTheChoicesAndAnotherStartAreIgnored() =
        withTarget { target ->
            val release = CompletableDeferred<Unit>()
            read = { runBlocking { release.await() }.let { _ -> layerOf("Zeile $it", number = it) } }
            val model = model()
            model.start(target)
            assertTrue(model.busy)
            val before = model.options
            model.changeOptions(before.copy(header = !before.header))
            model.selectFormat(ExportFormat.MARKDOWN)
            assertEquals(before, model.options)
            assertEquals(ExportFormat.TEXT, model.format)
            assertFalse(model.canExport)
            model.start(target)
            release.complete(Unit)
            waitFor { done.isNotEmpty() }
            assertEquals(1, done.size)
            assertNotNull(target.readText())
        }

    @Test
    fun theSuggestedNameIsTheTitleWithTheExtensionOfTheFormat() {
        val model = model()
        assertEquals("Der kleine Krieg.txt", model.suggestedName { it })
        model.selectFormat(ExportFormat.MARKDOWN)
        assertEquals("Der kleine Krieg.md", model.suggestedName { it })
    }

    @Test
    fun theWindowCannotBeClosedWhileAnExportIsWritten() {
        val app = korrektor.AppModel()
        assertTrue(app.canClose)
        app.exporting = true
        assertFalse(app.canClose)
        app.exporting = false
        assertTrue(app.canClose)
    }
}
