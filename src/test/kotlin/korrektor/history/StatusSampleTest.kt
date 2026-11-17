package korrektor.history

import korrektor.AppModel
import korrektor.closeProject
import korrektor.correctedPages
import korrektor.editor.SaveOutcome
import korrektor.editor.setStatus
import korrektor.editor.uncheckedAfter
import korrektor.editor.uncheckedBefore
import korrektor.format.Metadata
import korrektor.format.PageStatus
import korrektor.format.Project
import korrektor.importer.PdfImport
import korrektor.progressText
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Page statuses on the real sample (the Abhandlung, 165 pages): twenty pages are marked corrected and saved, the
 * project is opened again, and the progress and the jumps are checked. Skipped when the sample is missing, which is
 * always the case on CI.
 */
class StatusSampleTest {
    private val sample = Path.of("samples", "Abhandlung_über_den_kleinen_Krieg.pdf")

    @Test
    fun twentyCorrectedPagesAreKeptAndTheProgressAndTheJumpsFollow() {
        assumeTrue(sample.exists(), "the sample PDF is not available")
        val dir = Files.createTempDirectory("statussample")
        try {
            val project = Project.create(dir.resolve("sample.korrektur"), sample, emptyList(), Metadata("Abhandlung"))
            PdfImport.run(project)
            val total = project.pages.size
            val marked = (1..20).map { it * 5 } // 5, 10, … 100

            val first = AppModel()
            first.openProject(project.folder)
            val editor = checkNotNull(first.editor)
            assertEquals("0 of $total corrected (0 %)", progressText(editor.correctedPages(total), total))
            for (page in marked) editor.setStatus(page, PageStatus.CORRECTED)
            assertEquals(SaveOutcome.Done, runBlocking { checkNotNull(first.saver).saveNow() })
            first.closeProject()

            val second = AppModel()
            second.openProject(project.folder)
            assertNull(second.error)
            val reopened = checkNotNull(second.editor)
            assertEquals(marked.toSet(), reopened.statuses.keys)
            assertEquals("20 of $total corrected (12 %)", progressText(reopened.correctedPages(total), total))
            assertEquals(1, reopened.uncheckedAfter(0, total))
            assertEquals(6, reopened.uncheckedAfter(5, total))
            assertEquals(101, reopened.uncheckedAfter(99, total))
            assertEquals(9, reopened.uncheckedBefore(10))
            assertNull(reopened.uncheckedBefore(1))
            println("sample: 20 corrected pages of $total saved, reopened, progress and jumps as expected")
            second.closeProject()
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }
}
