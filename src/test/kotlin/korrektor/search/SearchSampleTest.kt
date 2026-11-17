package korrektor.search

import korrektor.AppModel
import korrektor.closeProject
import korrektor.editor.UndoResult
import korrektor.editor.undo
import korrektor.format.Metadata
import korrektor.format.Project
import korrektor.importer.PdfImport
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Find and replace on the real sample (the Abhandlung, 165 pages): the words and a regex are searched in the whole
 * book, a word is replaced on all pages and taken back in one step. Skipped when the sample is missing, which is
 * always the case on CI.
 */
class SearchSampleTest {
    private val sample = Path.of("samples", "Abhandlung_über_den_kleinen_Krieg.pdf")

    private fun count(
        model: SearchModel,
        query: SearchQuery,
    ): Int {
        model.change(query)
        runBlocking { withTimeout(30_000) { while (model.searching) delay(2) } }
        return model.results.size
    }

    @Test
    fun aWordIsReplacedOnAllPagesOfTheSampleAndTakenBackInOneStep() {
        assumeTrue(sample.exists(), "the sample PDF is not available")
        val dir = Files.createTempDirectory("searchsample")
        try {
            val project = Project.create(dir.resolve("sample.korrektur"), sample, emptyList(), Metadata("Abhandlung"))
            PdfImport.run(project)
            val model = AppModel()
            model.openProject(project.folder)
            val editor = checkNotNull(model.editor)
            val search = checkNotNull(model.search)
            val total = project.pages.size

            val started = System.nanoTime()
            val krieg = count(search, SearchQuery("Krieg", matchCase = true, wholeWords = true))
            val millis = (System.nanoTime() - started) / 1_000_000
            val longS = count(search, SearchQuery("ſ"))
            val regex = count(search, SearchQuery("""\b[Ss]ie\b""", regex = true))
            val plainS = count(search, SearchQuery("s"))
            println("sample, $total pages: 'Krieg' $krieg hits in $millis ms, 'ſ' $longS, regex $regex, 's' $plainS")
            assertTrue(krieg > 0)
            assertTrue(longS > 0, "the book has long s")

            val kampf = count(search, SearchQuery("Kampf", matchCase = true, wholeWords = true))
            count(search, SearchQuery("Krieg", matchCase = true, wholeWords = true))
            search.replacement = "Kampf"
            runBlocking { search.replaceSelected() }
            println("sample: ${search.outcome}")
            assertEquals(0, count(search, SearchQuery("Krieg", matchCase = true, wholeWords = true)))
            assertEquals(kampf + krieg, count(search, SearchQuery("Kampf", matchCase = true, wholeWords = true)))
            assertEquals(1, editor.history.undoSteps.size)

            assertIs<UndoResult.Done>(editor.undo())
            assertEquals(krieg, count(search, SearchQuery("Krieg", matchCase = true, wholeWords = true)))
            assertEquals(kampf, count(search, SearchQuery("Kampf", matchCase = true, wholeWords = true)))
            model.closeProject()
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }
}
