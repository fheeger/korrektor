package korrektor.settings

import korrektor.export.ExportFormat
import korrektor.export.ExportOptions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What the app settings remember of the export (T096): the options of the last export, for each format. */
class ExportSettingsTest {
    private fun withDir(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("exportsettings")
        try {
            block(dir)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    private val text = ExportFormat.TEXT
    private val markdown = ExportFormat.MARKDOWN

    @Test
    fun theFirstTimeTheDefaultsApply() =
        withDir { dir ->
            val settings = AppSettings(dir.resolve("settings.json"))
            assertEquals(
                ExportOptions(text, pageMarkers = true, header = true, keepLineBreaks = true),
                settings.exportOptions(text),
            )
            assertEquals(
                ExportOptions(markdown, pageMarkers = false, header = false, keepLineBreaks = false),
                settings.exportOptions(markdown),
            )
        }

    @Test
    fun theLastChoicesOfAFormatAreRememberedAcrossARestartAndTheOtherFormatIsNotAffected() =
        withDir { dir ->
            val file = dir.resolve("config/settings.json")
            val chosen = ExportOptions(markdown, pageMarkers = true, header = false, keepLineBreaks = true)
            AppSettings(file).rememberExportOptions(chosen)

            val restarted = AppSettings(file)
            assertEquals(chosen, restarted.exportOptions(markdown))
            assertEquals(ExportOptions.defaultsFor(text), restarted.exportOptions(text))

            val plain = ExportOptions(text, pageMarkers = false, header = true, keepLineBreaks = false)
            restarted.rememberExportOptions(plain)
            val again = AppSettings(file)
            assertEquals(plain, again.exportOptions(text))
            assertEquals(chosen, again.exportOptions(markdown))
        }

    @Test
    fun brokenEntriesGiveTheDefaultsForWhatIsBroken() =
        withDir { dir ->
            val file = dir.resolve("settings.json")
            file.writeText(
                """{"version": 1, "export": {"text": {"pageMarkers": "yes", "header": false, "keepLineBreaks": 1},""" +
                    """ "markdown": "oops"}}""",
            )
            val settings = AppSettings(file)
            assertEquals(
                ExportOptions(text, pageMarkers = true, header = false, keepLineBreaks = true),
                settings.exportOptions(text),
            )
            assertEquals(ExportOptions.defaultsFor(markdown), settings.exportOptions(markdown))
        }

    @Test
    fun anExportThatIsNotAnObjectIsReplacedWhenSomethingIsRemembered() =
        withDir { dir ->
            val file = dir.resolve("settings.json")
            file.writeText("""{"version": 1, "export": 5}""")
            val settings = AppSettings(file)
            assertEquals(ExportOptions.defaultsFor(text), settings.exportOptions(text))
            val chosen = ExportOptions(text, pageMarkers = false, header = false, keepLineBreaks = false)
            settings.rememberExportOptions(chosen)
            assertEquals(chosen, AppSettings(file).exportOptions(text))
        }

    @Test
    fun fieldsThisVersionDoesNotKnowSurviveAWrite() =
        withDir { dir ->
            val file = dir.resolve("settings.json")
            file.writeText(
                """{"version": 1, "projects": {}, "export": {"epub": {"x": 1}, """ +
                    """"text": {"header": true, "font": "big"}}}""",
            )
            AppSettings(
                file,
            ).rememberExportOptions(ExportOptions(text, pageMarkers = false, header = false, keepLineBreaks = true))

            val export = (Json.parseToJsonElement(file.readText()) as JsonObject).getValue("export") as JsonObject
            assertTrue("epub" in export)
            val entry = export.getValue("text") as JsonObject
            assertEquals("\"big\"", entry.getValue("font").toString())
            assertEquals("false", entry.getValue("header").toString())
        }

    @Test
    fun rememberingTheSameOptionsAgainWritesNothing() =
        withDir { dir ->
            val settings = AppSettings(dir.resolve("settings.json"))
            val options = ExportOptions(markdown, pageMarkers = true, header = true, keepLineBreaks = false)
            settings.rememberExportOptions(options)
            assertEquals(1, settings.writeCount)
            settings.rememberExportOptions(options)
            assertEquals(1, settings.writeCount)
            settings.rememberExportOptions(options.copy(header = false))
            assertEquals(2, settings.writeCount)
        }

    @Test
    fun inMemorySettingsRememberThemToo() {
        val settings = AppSettings.inMemory()
        val options = ExportOptions(text, pageMarkers = false, header = false, keepLineBreaks = false)
        settings.rememberExportOptions(options)
        assertEquals(options, settings.exportOptions(text))
    }
}
