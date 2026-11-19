package korrektor.export

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The export of a small book compared with files that were checked by hand, for all eight combinations of the three
 * options in both formats (`src/test/resources/export/<format>-<options>.<extension>`: `m` markers, `h` header,
 * `k` printed line breaks kept; a letter that is missing means the option is off).
 */
class ExportGoldenTest {
    private fun name(options: ExportOptions): String {
        val letters =
            listOf('m' to options.pageMarkers, 'h' to options.header, 'k' to options.keepLineBreaks)
                .filter { it.second }
                .joinToString("") { it.first.toString() }
        return "${options.format.name.lowercase()}-${letters.ifEmpty { "none" }}.${options.format.extension}"
    }

    private fun expected(name: String): ByteArray {
        val resource = ExportGoldenTest::class.java.getResource("/export/$name")
        return assertNotNull(resource, "the expected file $name is missing").readBytes()
    }

    private fun allOptions(format: ExportFormat) =
        listOf(false, true).flatMap { markers ->
            listOf(false, true).flatMap { header ->
                listOf(false, true).map { keep -> ExportOptions(format, markers, header, keep) }
            }
        }

    private fun export(
        options: ExportOptions,
        pages: IntRange = 1..3,
    ): ByteArray {
        var bytes = ByteArray(0)
        withGoldenBook { project ->
            val target = Files.createTempFile("golden", ".out")
            try {
                runBlocking {
                    BookExport(
                        project::readPage,
                        Dispatchers.IO,
                    ).run(target, project.metadata, pages, options)
                }
                bytes = target.readBytes()
            } finally {
                Files.deleteIfExists(target)
            }
        }
        return bytes
    }

    @Test
    fun everyCombinationOfOptionsInBothFormatsGivesTheExpectedFile() {
        for (format in ExportFormat.entries) {
            for (options in allOptions(format)) {
                assertContentEquals(expected(name(options)), export(options), name(options))
            }
        }
    }

    @Test
    fun aRangeLeavesOutThePagesBeforeIt() {
        val options = ExportOptions(ExportFormat.TEXT, pageMarkers = true, header = false, keepLineBreaks = true)
        val text = export(options, 3..3).toString(Charsets.UTF_8)
        assertEquals("[Page 14]\n${GOLDEN_PAGES[2].joinToString("\n")}\n", text)
    }
}
