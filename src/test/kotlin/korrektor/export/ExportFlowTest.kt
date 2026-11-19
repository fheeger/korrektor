package korrektor.export

import korrektor.AppModel
import korrektor.closeProject
import korrektor.editor.BookEditor
import korrektor.editor.currentLayer
import korrektor.editor.filesOf
import korrektor.editor.withHistoryProject
import korrektor.format.PageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Open a project, correct a line, export it: the file has the correction and the project is as it was. */
class ExportFlowTest {
    @Test
    fun aCorrectionIsInTheExportAndTheExportChangesNothingInTheProject() =
        withHistoryProject(pages = 3) { project ->
            val model = AppModel()
            model.openProject(project.folder)
            val editor = checkNotNull(model.editor)
            runBlocking { editor.show(2) }
            editor.setLineText(editor.lines.first().id, "Seite zwei, ſo verbeſſert")
            assertTrue(2 in editor.changedPages)
            val target = Files.createTempFile("exportflow", ".md")
            try {
                val before = filesOf(project)
                val book = ExportBook(3, model.metadata) { editor.statuses }
                var message: String? = null
                val export =
                    ExportModel(
                        book,
                        model.settings,
                        BookExport(editor::currentLayer, Dispatchers.IO),
                        CoroutineScope(Dispatchers.Default),
                    ) {
                        message =
                            it
                    }
                export.selectFormat(ExportFormat.MARKDOWN)
                export.start(target)
                runBlocking { withTimeout(10_000) { while (message == null) delay(5) } }

                assertEquals(
                    "Seite 1 oben unten\n\nSeite zwei, ſo verbeſſert unten\n\nSeite 3 oben unten\n",
                    target.readText(),
                )
                assertEquals("Exported 3 pages to $target", message)
                val after = filesOf(project)
                assertEquals(before.keys, after.keys)
                before.forEach { (name, bytes) -> assertContentEquals(bytes, after.getValue(name), name) }
                assertTrue(2 in editor.changedPages, "the correction is still unsaved, as before")
                assertEquals(1, editor.history.undoSteps.size, "no step was added to the history")
                assertEquals(emptyMap<Int, PageStatus>(), editor.statuses)
            } finally {
                Files.deleteIfExists(target)
                model.closeProject()
            }
        }

    @Test
    fun theEditorIsNotNeededToExportASavedBook() =
        withHistoryProject(pages = 2) { project ->
            val editor = BookEditor(project::readPage, Dispatchers.Unconfined)
            val target = Files.createTempFile("exportflow", ".txt")
            try {
                val options = ExportOptions.defaultsFor(ExportFormat.TEXT)
                runBlocking {
                    BookExport(
                        editor::currentLayer,
                        Dispatchers.IO,
                    ).run(target, project.metadata, 1..2, options)
                }
                assertEquals("B\n\n[Page 1]\nSeite 1 oben\nunten\n\n[Page 2]\nSeite 2 oben\nunten\n", target.readText())
            } finally {
                Files.deleteIfExists(target)
            }
        }
}
