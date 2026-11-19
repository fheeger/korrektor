package korrektor.export

import korrektor.editor.PageEdits
import korrektor.format.Metadata
import korrektor.format.Project
import korrektor.importer.PdfImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The export of the real sample (the Abhandlung, 165 pages) in both formats with the options on and off. The sample's
 * text is too long to keep a copy of it: the expected file is worked out again here, in the simplest way, from the
 * stored lines, and a few stable points are checked. Skipped when the sample is missing, which is the case on CI.
 */
class ExportSampleTest {
    private val sample = Path.of("samples", "Abhandlung_über_den_kleinen_Krieg.pdf")

    private fun withSampleProject(block: (Project, List<List<String>>) -> Unit) {
        assumeTrue(sample.exists(), "the sample PDF is not available")
        val dir = Files.createTempDirectory("exportsample")
        try {
            val project =
                Project.create(dir.resolve("s.korrektur"), sample, emptyList(), Metadata("Abhandlung", "Ewald", "1785"))
            PdfImport.run(project)
            val pages =
                (1..project.pages.size).map { n ->
                    PageEdits.readingOrder(project.readPage(n)).map { it.text }.filter { it.isNotEmpty() }
                }
            block(project, pages)
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    private fun export(
        project: Project,
        options: ExportOptions,
        pages: IntRange = 1..project.pages.size,
    ): String {
        val target = Files.createTempFile("exportsample", ".out")
        try {
            runBlocking { BookExport(project::readPage, Dispatchers.IO).run(target, project.metadata, pages, options) }
            return target.readText()
        } finally {
            Files.deleteIfExists(target)
        }
    }

    /** The plain text export worked out from the stored lines, without the renderer. */
    private fun expectedText(
        pages: List<List<String>>,
        first: Int,
        options: ExportOptions,
    ): String {
        val blocks = ArrayList<String>()
        if (options.header) blocks += "Abhandlung\nAuthor: Ewald\nYear: 1785"
        for ((i, lines) in pages.withIndex()) {
            val body = if (options.keepLineBreaks) lines.joinToString("\n") else lines.joinToString(" ")
            val marker = "[Page ${first + i}]"
            when {
                options.pageMarkers && body.isNotEmpty() -> blocks += "$marker\n$body"
                options.pageMarkers -> blocks += marker
                body.isNotEmpty() -> blocks += body
            }
        }
        return blocks.joinToString("\n\n") + "\n"
    }

    private fun allOptions(format: ExportFormat) =
        listOf(false, true).flatMap { m ->
            listOf(false, true).flatMap { h -> listOf(false, true).map { k -> ExportOptions(format, m, h, k) } }
        }

    @Test
    fun plainTextWithEveryCombinationOfOptionsIsTheStoredLines() =
        withSampleProject { project, pages ->
            println("sample: ${pages.size} pages, ${pages.sumOf { it.size }} lines")
            assertTrue(pages.size > 100)
            for (options in allOptions(ExportFormat.TEXT)) {
                assertEquals(expectedText(pages, 1, options), export(project, options), "$options")
            }
        }

    @Test
    fun markdownWithEveryCombinationOfOptionsIsReadBackAsTheStoredLines() =
        withSampleProject { project, pages ->
            for (options in allOptions(ExportFormat.MARKDOWN)) {
                val read = readMarkdown(export(project, options))
                val expected = ArrayList<String>()
                if (options.header) expected += listOf("Abhandlung", "Author: Ewald\nYear: 1785")
                for ((i, lines) in pages.withIndex()) {
                    if (options.pageMarkers) expected += "[Page ${i + 1}]"
                    if (lines.isNotEmpty()) {
                        expected += if (options.keepLineBreaks) lines.joinToString("\n") else lines.joinToString(" ")
                    }
                }
                assertEquals(expected.size, read.size, "$options: the number of paragraphs")
                assertEquals(expected, read, "$options")
            }
        }

    @Test
    fun aRangeLeavesOutTheFirstPageAndTheLongSIsKept() =
        withSampleProject { project, pages ->
            val options = ExportOptions(ExportFormat.TEXT, pageMarkers = true, header = false, keepLineBreaks = true)
            val text = export(project, options, 2..pages.size)
            assertFalse(text.contains("[Page 1]\n"))
            assertTrue(text.startsWith("[Page 2]\n"))
            assertEquals(expectedText(pages.drop(1), 2, options), text)
            assertTrue(text.contains('ſ'), "the long s is not normalized")
            assertTrue(text.endsWith("\n") && !text.contains('\r'))
        }
}
