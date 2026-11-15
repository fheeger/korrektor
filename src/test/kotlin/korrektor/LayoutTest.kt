package korrektor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import korrektor.format.Metadata
import korrektor.format.Project
import korrektor.settings.AppSettings
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LayoutTest {
    private fun withDir(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("layout")
        try {
            block(dir)
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    private fun project(
        dir: Path,
        name: String = "book",
    ): Path {
        val pdf = dir.resolve("$name.pdf").also { it.writeBytes("%PDF-1.4\n".toByteArray()) }
        return Project.create(dir.resolve("$name.korrektur"), pdf, emptyList(), Metadata(name)).folder
    }

    // --- the state ----------------------------------------------------------------------------------------

    @Test
    fun theDividerStartsAt55PercentAndCannotLeaveItsRange() {
        val layout = LayoutState(AppSettings.inMemory())
        assertEquals(0.55f, layout.splitFraction)

        layout.moveDivider(0.05f)
        assertEquals(0.2f, layout.splitFraction)
        layout.moveDivider(0.95f)
        assertEquals(0.8f, layout.splitFraction)
        layout.moveDivider(0.4f)
        assertEquals(0.4f, layout.splitFraction)
    }

    @Test
    fun theSavedPositionComesBackAndAnInvalidOneMeansTheDefault() =
        withDir { dir ->
            val file = dir.resolve("settings.json")
            val first = LayoutState(AppSettings(file))
            first.moveDivider(0.35f)
            first.save()

            assertEquals(0.35f, LayoutState(AppSettings(file)).splitFraction, 1e-6f)

            for (value in listOf("0.05", "0.95", "\"wide\"", "null", "-1", "7")) {
                file.writeText("""{"version": 1, "ui": {"split": $value}}""")
                assertEquals(0.55f, LayoutState(AppSettings(file)).splitFraction, "for $value")
            }
            file.writeText("""{"version": 1, "ui": 5}""")
            assertEquals(0.55f, LayoutState(AppSettings(file)).splitFraction)
        }

    @Test
    fun savingKeepsTheViewsAndOtherSettingsAndWritesNothingForNoChange() =
        withDir { dir ->
            val file = dir.resolve("settings.json")
            file.writeText("""{"version": 1, "theme": "dark", "ui": {"split": 0.3, "other": true}, "projects": {}}""")
            val settings = AppSettings(file)
            val layout = LayoutState(settings)
            layout.save() // the same value as in the file
            assertEquals(0, settings.writeCount, "nothing changed")

            layout.moveDivider(0.45f)
            layout.save()

            val text = file.toFile().readText()
            assertTrue("\"theme\": \"dark\"" in text && "\"other\": true" in text && "\"projects\"" in text, text)
            assertEquals(1, settings.writeCount)
            assertEquals(0.45, AppSettings(file).splitFraction()!!, 1e-6)
        }

    @Test
    fun everyProjectUsesTheSamePosition() =
        withDir { dir ->
            val model = AppModel(settings = AppSettings(dir.resolve("settings.json")))
            model.layout.moveDivider(0.3f)

            model.openProject(project(dir, "a"))
            assertEquals(0.3f, model.layout.splitFraction)
            model.openProject(project(dir, "b"))
            assertEquals(0.3f, model.layout.splitFraction)
        }

    // --- the divider on the screen -------------------------------------------------------------------------

    @Test
    fun draggingTheDividerResizesBothPanelsAndStopsAtTheLimits() =
        runComposeUiTest {
            val layout = LayoutState(AppSettings.inMemory()).also { it.moveDivider(0.5f) }
            var ended = 0
            setContent {
                SplitPane(
                    layout,
                    { ended++ },
                    left = { Box(Modifier.fillMaxSize()) },
                    modifier = Modifier.size(1008.dp, 300.dp),
                    right = { Box(Modifier.fillMaxSize()) },
                )
            }

            // 1008 dp wide with an 8 dp divider: 1000 dp for the panels.
            fun widths() =
                onNodeWithTag("scan panel").getBoundsInRoot().width.value to
                    onNodeWithTag("text panel").getBoundsInRoot().width.value
            assertEquals(500f, widths().first, 1f)
            assertEquals(500f, widths().second, 1f)

            onNodeWithTag("divider").performMouseInput {
                moveTo(center)
                press()
                moveBy(Offset(150f, 0f)) // pixels; the density of the test is 1
                release()
            }
            waitForIdle()

            assertEquals(0.65f, layout.splitFraction, 0.01f)
            assertEquals(650f, widths().first, 3f)
            assertEquals(350f, widths().second, 3f)
            assertEquals(1, ended, "the end of the drag is reported once")

            onNodeWithTag("divider").performMouseInput {
                moveTo(center)
                press()
                moveBy(Offset(-2000f, 0f))
                release()
            }
            waitForIdle()
            assertEquals(0.2f, layout.splitFraction, "not beyond the limit")
        }

    @Test
    fun theOpenProjectShowsTheScanAndTheTextSideBySideAndTheDividerIsSavedWhenTheDragEnds() =
        withDir { dir ->
            val folder = project(dir)
            val file = dir.resolve("config/settings.json")
            val model = AppModel(settings = AppSettings(file))
            runComposeUiTest {
                setContent { App(model, { folder }, { null }) }
                onNodeWithText("Open project…").performClick()
                onNodeWithTag("scan panel").assertExists()
                onNodeWithTag("text panel").assertExists()
                val scan = onNodeWithTag("scan panel").getBoundsInRoot()
                val text = onNodeWithTag("text panel").getBoundsInRoot()
                assertTrue(scan.left < text.left && scan.width > text.width, "the scan is on the left and gets 55 %")
                assertTrue(
                    scan.height > 300.dp && text.height == scan.height,
                    "both fill the height below the header: $scan",
                )

                onNodeWithTag("divider").performMouseInput {
                    moveTo(center)
                    press()
                    moveBy(Offset(-120f, 0f))
                    release()
                }
                waitForIdle()

                assertTrue(model.layout.splitFraction < 0.55f)
                waitUntil(timeoutMillis = 5_000) { file.exists() } // saved on the IO threads after the drag
                val saved = AppSettings(file).splitFraction()!!
                assertEquals(model.layout.splitFraction.toDouble(), saved, 1e-4) // saved to four decimals
            }
        }
}
