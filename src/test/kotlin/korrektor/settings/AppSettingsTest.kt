package korrektor.settings

import korrektor.viewer.SavedView
import korrektor.viewer.SavedZoom
import korrektor.viewer.ZoomMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppSettingsTest {
    private fun withDir(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("appsettings")
        try {
            block(dir)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    private val fitWidth = SavedView(3, SavedZoom(ZoomMode.FIT_WIDTH))
    private val fitPage = SavedView(7, SavedZoom(ZoomMode.FIT_PAGE))
    private val fixed = SavedView(12, SavedZoom(ZoomMode.FIXED, 1.5))

    private fun project(
        dir: Path,
        name: String,
    ): Path = dir.resolve("$name.korrektur")

    private fun readJson(file: Path) = Json.parseToJsonElement(file.readText()) as JsonObject

    // --- remembering --------------------------------------------------------------------------------------

    @Test
    fun everyKindOfZoomIsRememberedAcrossARestart() =
        withDir { dir ->
            val file = dir.resolve("config/settings.json")
            val settings = AppSettings(file)

            settings.rememberView(project(dir, "a"), fitWidth)
            settings.rememberView(project(dir, "b"), fitPage)
            settings.rememberView(project(dir, "c"), fixed)

            val restarted = AppSettings(file)
            assertEquals(fitWidth, restarted.viewOf(project(dir, "a")))
            assertEquals(fitPage, restarted.viewOf(project(dir, "b")))
            assertEquals(fixed, restarted.viewOf(project(dir, "c")))
        }

    @Test
    fun twoProjectsKeepTheirOwnValuesAndAnUnknownOneHasNone() =
        withDir { dir ->
            val settings = AppSettings(dir.resolve("settings.json"))
            settings.rememberView(project(dir, "a"), fitWidth)
            settings.rememberView(project(dir, "b"), fixed)
            settings.rememberView(project(dir, "a"), fitPage)

            assertEquals(fitPage, settings.viewOf(project(dir, "a")))
            assertEquals(fixed, settings.viewOf(project(dir, "b")))
            assertNull(settings.viewOf(project(dir, "c")))
        }

    @Test
    fun theFileHasTheDocumentedShape() =
        withDir { dir ->
            val file = dir.resolve("settings.json")
            AppSettings(file).rememberView(project(dir, "a"), fixed)

            val json = readJson(file)
            assertEquals("1", json["version"].toString())
            val entry = (json["projects"] as JsonObject).values.single() as JsonObject
            assertEquals("12", entry["page"].toString())
            assertEquals("""{"mode":"fixed","scale":1.5}""", entry["zoom"].toString())
        }

    @Test
    fun theFileIsValidJsonAfterEveryWriteAndNoTempFileStays() =
        withDir { dir ->
            val file = dir.resolve("settings.json")
            val settings = AppSettings(file)
            repeat(20) { i ->
                settings.rememberView(project(dir, "p"), SavedView(i + 1, SavedZoom(ZoomMode.FIXED, 1.0 + i / 10.0)))
                readJson(file)
            }
            assertEquals(listOf("settings.json"), dir.toFile().list()!!.filter { it.startsWith("settings") })
        }

    @Test
    fun aViewThatHasNotChangedIsNotWrittenAgain() =
        withDir { dir ->
            val settings = AppSettings(dir.resolve("settings.json"))
            settings.rememberView(project(dir, "a"), fixed)
            settings.rememberView(project(dir, "a"), fixed)
            settings.rememberView(project(dir, "a"), fixed)
            assertEquals(1, settings.writeCount)
            settings.rememberView(project(dir, "a"), fitPage)
            assertEquals(2, settings.writeCount)
        }

    @Test
    fun settingsInMemoryWriteNothingAndStillRemember() {
        val settings = AppSettings.inMemory()
        settings.rememberView(Path.of("some", "folder"), fixed)
        assertEquals(fixed, settings.viewOf(Path.of("some", "folder")))
        assertEquals(0, settings.writeCount)
    }

    // --- a file that cannot be used -----------------------------------------------------------------------

    @Test
    fun aMissingOrEmptyFileMeansNoSettings() =
        withDir { dir ->
            val file = dir.resolve("settings.json")
            assertNull(AppSettings(file).viewOf(project(dir, "a")))
            file.writeText("")
            assertNull(AppSettings(file).viewOf(project(dir, "a")))
            file.writeText("  \n ")
            assertNull(AppSettings(file).viewOf(project(dir, "a")))
            assertFalse(dir.resolve("settings.json.bad").exists(), "nothing to keep")
        }

    @Test
    fun aDamagedFileIsMovedAsideAndTheAppStartsWithDefaults() =
        withDir { dir ->
            val damaged =
                listOf(
                    "not json at all {",
                    "[1, 2, 3]",
                    """{"version": "one"}""",
                    """{"projects": {}}""",
                    """{"version": 1, "projects": 5}""",
                )
            for (content in damaged) {
                val file = dir.resolve("settings.json")
                file.writeText(content)

                val settings = AppSettings(file)

                assertNull(settings.viewOf(project(dir, "a")), content)
                assertEquals(content, dir.resolve("settings.json.bad").readText(), "kept: $content")
                assertFalse(file.exists(), "moved, not copied")
                settings.rememberView(project(dir, "a"), fixed)
                readJson(file) // and a fresh valid file is written when something is remembered
                Files.delete(file)
            }
        }

    @Test
    fun aFileFromANewerVersionIsNotOverwrittenButMovedAside() =
        withDir { dir ->
            val file = dir.resolve("settings.json")
            val newer = """{"version": 2, "projects": {}, "future": true}"""
            file.writeText(newer)

            val settings = AppSettings(file)
            assertNull(settings.viewOf(project(dir, "a")))
            settings.rememberView(project(dir, "a"), fixed)

            assertEquals(newer, dir.resolve("settings.json.bad").readText())
            assertEquals(fixed, AppSettings(file).viewOf(project(dir, "a")))
        }

    @Test
    fun anEntryThatIsNotValidIsIgnoredAndTheOthersStay() =
        withDir { dir ->
            val file = dir.resolve("settings.json")
            val projects =
                mapOf(
                    "page as text" to """{"page": "3", "zoom": {"mode": "fitWidth"}}""",
                    "unknown mode" to """{"page": 3, "zoom": {"mode": "sideways"}}""",
                    "fixed without scale" to """{"page": 3, "zoom": {"mode": "fixed"}}""",
                    "negative scale" to """{"page": 3, "zoom": {"mode": "fixed", "scale": -1}}""",
                    "no zoom" to """{"page": 3}""",
                    "not an object" to "17",
                    "good" to """{"page": 9, "zoom": {"mode": "fitPage"}}""",
                )
            file.writeText(
                """{"version": 1, "projects": {${projects.entries.joinToString(
                    ",",
                ) { """"${project(dir, it.key)}": ${it.value}""".replace("\\", "\\\\") }}}}""",
            )

            val settings = AppSettings(file)

            for (name in projects.keys - "good") assertNull(settings.viewOf(project(dir, name)), name)
            assertEquals(SavedView(9, SavedZoom(ZoomMode.FIT_PAGE)), settings.viewOf(project(dir, "good")))
            assertFalse(dir.resolve("settings.json.bad").exists())
        }

    @Test
    fun aFailedWriteIsNotAnErrorAndTheViewIsStillRemembered() =
        withDir { dir ->
            val blocker = dir.resolve("not-a-folder").also { it.writeText("a file where the config folder should be") }
            val settings = AppSettings(blocker.resolve("korrektor/settings.json"))

            settings.rememberView(project(dir, "a"), fixed) // must not throw

            assertEquals(fixed, settings.viewOf(project(dir, "a")))
            assertEquals(0, settings.writeCount)
        }

    // --- what stays and what goes -------------------------------------------------------------------------

    @Test
    fun fieldsThisVersionDoesNotKnowSurviveAWrite() =
        withDir { dir ->
            val file = dir.resolve("settings.json")
            val key =
                project(dir, "a")
                    .toAbsolutePath()
                    .normalize()
                    .toString()
                    .replace("\\", "\\\\")
            file.writeText(
                """{"version": 1, "theme": "dark", "projects": {"$key": {"page": 2, "note": "keep",""" +
                    """ "zoom": {"mode": "fitWidth", "dpi": 96}}}}""",
            )

            AppSettings(file).rememberView(project(dir, "a"), fixed)

            val json = readJson(file)
            assertEquals("\"dark\"", json["theme"].toString())
            val entry = (json["projects"] as JsonObject).values.single() as JsonObject
            assertEquals("\"keep\"", entry["note"].toString())
            assertEquals("""{"mode":"fixed","dpi":96,"scale":1.5}""", entry["zoom"].toString())
            assertEquals("12", entry["page"].toString())
        }

    @Test
    fun aFitModeDropsTheOldScale() =
        withDir { dir ->
            val file = dir.resolve("settings.json")
            val settings = AppSettings(file)
            settings.rememberView(project(dir, "a"), fixed)

            settings.rememberView(project(dir, "a"), fitPage)

            val entry = (readJson(file)["projects"] as JsonObject).values.single() as JsonObject
            assertEquals("""{"mode":"fitPage"}""", entry["zoom"].toString())
        }

    @Test
    fun onlyTheMostRecentProjectsAreKept() =
        withDir { dir ->
            val settings = AppSettings(dir.resolve("settings.json"), maxProjects = 5)
            for (i in 1..5) settings.rememberView(project(dir, "p$i"), SavedView(i, SavedZoom(ZoomMode.FIT_WIDTH)))
            // p1 is recent again
            settings.rememberView(project(dir, "p1"), SavedView(10, SavedZoom(ZoomMode.FIT_WIDTH)))

            settings.rememberView(project(dir, "p6"), fitWidth) // one too many: the oldest, p2, goes

            val restarted = AppSettings(dir.resolve("settings.json"), maxProjects = 5)
            assertNull(restarted.viewOf(project(dir, "p2")))
            for (name in listOf(
                "p1",
                "p3",
                "p4",
                "p5",
                "p6",
            )) {
                assertTrue(restarted.viewOf(project(dir, name)) != null, name)
            }
            assertEquals(5, (readJson(dir.resolve("settings.json"))["projects"] as JsonObject).size)
        }

    @Test
    fun theDefaultLimitIs200() = assertEquals(200, AppSettings.MAX_PROJECTS)

    // --- where the file is --------------------------------------------------------------------------------

    @Test
    fun theFileIsInTheConfigFolderOfTheSystem() {
        fun path(
            os: String,
            env: Map<String, String> = emptyMap(),
        ) = AppSettings.defaultFile(os, "/home/anna", env::get).toString().replace('\\', '/')

        assertEquals(
            "C:/Users/anna/AppData/Roaming/korrektor/settings.json",
            path(
                "Windows 11",
                mapOf(
                    "APPDATA" to "C:/Users/anna/AppData/Roaming",
                ),
            ),
        )
        assertEquals("/home/anna/AppData/Roaming/korrektor/settings.json", path("Windows 10"))
        assertEquals("/home/anna/Library/Application Support/korrektor/settings.json", path("Mac OS X"))
        assertEquals("/home/anna/.config/korrektor/settings.json", path("Linux"))
        // An absolute path looks different on every system, so it is made for this one.
        val xdg =
            Path
                .of("")
                .toAbsolutePath()
                .root
                .resolve("xdg")
                .toString()
        val expected = xdg.replace(Path.of("").fileSystem.separator, "/") + "/korrektor/settings.json"
        assertEquals(expected, path("Linux", mapOf("XDG_CONFIG_HOME" to xdg)))
        assertEquals(
            "/home/anna/.config/korrektor/settings.json",
            path(
                "Linux",
                mapOf(
                    "XDG_CONFIG_HOME" to "relative/path",
                ),
            ),
        )
    }
}
