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
            assertEquals(mapOf(1 to PageStatus.CORRECTED), project.statuses)
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
            val file = dir.resolve("history").resolve("book.jsonl")
            file.parent.createDirectories()
            file.writeText(example("history"))
            val log = HistoryLog(file)
            val entries = log.readAll()
            assertEquals(6, entries.size)

            // Through the entries of the code and back: the same text.
            val decoded = entries.map(HistoryCodec::decode)
            assertEquals(
                listOf("EditEntry", "EditEntry", "UndoEntry", "RedoEntry", "EditEntry", "SavedEntry"),
                decoded.map { it::class.simpleName },
            )
            val copy = HistoryLog(dir.resolve("copy.jsonl"))
            copy.appendAll(decoded.map(HistoryCodec::encode))
            assertEquals(example("history"), dir.resolve("copy.jsonl").readText())
            assertTrue(example("history").endsWith("\n"))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun theHistoryExampleBelongsToTheCorrectedPageExample() {
        // The entries, made on the original page one after the other, give the corrected page.
        val original = PageCodec.decode(example("page-original"))
        val corrected = PageCodec.decode(example("page-corrected"))
        val entries = example("history").lines().filter { it.isNotBlank() }.map { HistoryCodec.decode(parseObject(it)) }
        var layer =
            original.copy(
                regions =
                    original.regions.map { r ->
                        r.copy(lines = r.lines.map { it.copy(origin = it.id) })
                    },
            )
        for (entry in entries) {
            val changes =
                when (entry) {
                    is EditEntry -> entry.changes
                    is UndoEntry -> entry.changes
                    is RedoEntry -> entry.changes
                    else -> emptyList()
                }
            for (change in changes.filterIsInstance<LineChange>()) layer = korrektor.editor.apply(layer, change)!!
        }
        assertEquals(corrected.copy(history = null), layer)
        assertEquals(entries.filterIsInstance<RedoEntry>().single().seq, corrected.history)
    }

    private fun parseObject(line: String) = CanonicalJson.parse(line) as kotlinx.serialization.json.JsonObject
}
