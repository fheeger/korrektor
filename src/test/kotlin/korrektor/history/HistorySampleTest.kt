package korrektor.history

import korrektor.AppModel
import korrektor.closeProject
import korrektor.editor.SaveOutcome
import korrektor.editor.UndoResult
import korrektor.editor.undo
import korrektor.format.Metadata
import korrektor.format.PageLayer
import korrektor.format.Project
import korrektor.importer.PdfImport
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Undo on the real sample (the Abhandlung, 165 pages): fifty lines are corrected on ten pages, saved, the project is
 * opened again and everything is undone; every page is as it was imported. Skipped when the sample is missing, which
 * is always the case on CI.
 */
class HistorySampleTest {
    private val sample = Path.of("samples", "Abhandlung_über_den_kleinen_Krieg.pdf")

    private fun PageLayer.plain() = copy(history = null)

    @Test
    fun fiftyCorrectionsOnTenPagesAreAllUndoneAfterReopening() {
        assumeTrue(sample.exists(), "the sample PDF is not available")
        val dir = Files.createTempDirectory("historysample")
        try {
            val project = Project.create(dir.resolve("sample.korrektur"), sample, emptyList(), Metadata("Abhandlung"))
            PdfImport.run(project)
            val pages = (10..100 step 10).toList()
            val imported = (1..project.pages.size).associateWith { project.readPage(it).plain() }

            val first = AppModel()
            first.openProject(project.folder)
            val editor = checkNotNull(first.editor)
            var corrections = 0
            runBlocking {
                for (page in pages) {
                    editor.show(page)
                    val ids = editor.lines.map { it.id }.take(5)
                    for ((i, id) in ids.withIndex()) {
                        val text = editor.lines.first { it.id == id }.text
                        when (i % 3) {
                            0 -> editor.setLineText(id, text.replaceFirstChar { it.uppercaseChar() } + " korrigiert")
                            1 -> editor.splitLine(id, text.length / 2)
                            else -> editor.setLineText(id, "$text ſ")
                        }
                        corrections++
                    }
                }
                assertEquals(SaveOutcome.Done, checkNotNull(first.saver).saveNow())
            }
            assertEquals(50, corrections)
            first.closeProject()

            val second = AppModel()
            second.openProject(project.folder)
            assertNull(second.error)
            val reopened = checkNotNull(second.editor)
            var steps = 0
            while (true) {
                val result = reopened.undo()
                if (result == UndoResult.NothingToDo) break
                assertIs<UndoResult.Done>(result)
                steps++
            }
            println("sample: $corrections corrections undone in $steps steps")
            for (page in 1..project.pages.size) {
                val now = (reopened.changed[page] ?: project.readPage(page)).plain()
                assertEquals(imported.getValue(page), now, "page $page")
            }
            second.closeProject()
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }
}
