package korrektor.format

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The examples in `docs/project-format.md` are real: they are read and written again, and must be identical. */
class SpecExamplesTest {
    private val spec = Path.of("docs", "project-format.md").readText(Charsets.UTF_8).replace("\r\n", "\n")

    private fun examples(): Map<String, String> =
        Regex("```json (\\S+)\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
            .findAll(spec)
            .associate { it.groupValues[1] to it.groupValues[2] }

    private fun example(name: String) = examples().getValue(name)

    @Test
    fun theSpecHasAllExamples() {
        assertEquals(
            setOf("project", "page-original", "page-corrected", "page-unknown", "page-note", "history"),
            examples().keys,
        )
    }

    @Test
    fun pageExamplesRoundTripByteForByte() {
        for (name in listOf("page-original", "page-corrected", "page-unknown", "page-note")) {
            val text = example(name)
            assertEquals(text, PageCodec.encode(PageCodec.decode(text)), name)
        }
    }

    @Test
    fun theCorrectedExampleBelongsToTheOriginalExample() {
        val original = PageCodec.decode(example("page-original")).regions[0].lines
        val corrected = PageCodec.decode(example("page-corrected")).regions[0].lines
        assertEquals(listOf("l1", "l2", "l3"), corrected.map { it.id })
        assertEquals("l2", corrected[1].origin)
        assertEquals(null, corrected[2].origin)
        assertEquals("rnng iſt nuͤtzlich", original[1].text)
        assertEquals("rung iſt nuͤtzlich", corrected[1].text)
    }

    @Test
    fun projectExampleOpensAndSavesIdentically() {
        val dir = Files.createTempDirectory("spec")
        try {
            val folder = dir.resolve("example.korrektur").createDirectories()
            folder.resolve("project.json").writeText(example("project"))
            folder.resolve("source.pdf").writeText("pdf")
            val project = Project.open(folder)
            assertEquals(3, project.pages.size)
            project.saveProjectFile()
            assertEquals(example("project"), folder.resolve("project.json").readText())
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun historyExampleIsReadAndAppendedIdentically() {
        val dir = Files.createTempDirectory("spec")
        try {
            val file = dir.resolve("history").resolve("0003.jsonl")
            file.parent.createDirectories()
            file.writeText(example("history"))
            val log = HistoryLog(file)
            val entries = log.readAll()
            assertEquals(2, entries.size)

            val copy = HistoryLog(dir.resolve("copy.jsonl"))
            entries.forEach { copy.append(it) }
            assertEquals(example("history"), dir.resolve("copy.jsonl").readText())
            assertTrue(example("history").endsWith("\n"))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
