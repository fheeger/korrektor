package korrektor

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import korrektor.format.Metadata
import korrektor.format.Project
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeBytes
import kotlin.test.Test

/** The core flow of the app shell, with a fake folder chooser: start screen, open a project, errors. */
@OptIn(ExperimentalTestApi::class)
class AppUiTest {
    private fun withDir(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("appui")
        try {
            block(dir)
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    private fun project(dir: Path): Path {
        val pdf = dir.resolve("book.pdf").also { it.writeBytes("%PDF-1.4\n".toByteArray()) }
        val metadata = Metadata("Abhandlung über den kleinen Krieg", "Ewald", "1785")
        return Project.create(dir.resolve("book.korrektur"), pdf, emptyList(), metadata).folder
    }

    @Test
    fun theStartScreenShowsTheAppNameAndBothButtons() =
        runComposeUiTest {
            setContent { App(AppModel(), { null }) }
            onNodeWithText("korrektor").assertExists()
            onNodeWithText("New project…").assertExists()
            onNodeWithText("Open project…").assertExists()
        }

    @Test
    fun openingAProjectShowsItsTitleAndMetadata() =
        withDir { dir ->
            val folder = project(dir)
            runComposeUiTest {
                val model = AppModel()
                setContent { App(model, { folder }) }

                onNodeWithText("Open project…").performClick()

                onNodeWithText("Abhandlung über den kleinen Krieg").assertExists()
                onNodeWithText("Author: Ewald").assertExists()
                onNodeWithText("Year: 1785").assertExists()
                onNodeWithText("Open another project…").assertExists()
            }
        }

    @Test
    fun aFolderThatIsNotAProjectShowsADismissibleError() =
        withDir { dir ->
            runComposeUiTest {
                setContent { App(AppModel(), { dir }) }

                onNodeWithText("Open project…").performClick()
                onNodeWithText("has no project.json", substring = true).assertExists()
                // Nothing was opened.
                onNodeWithText("New project…").assertExists()

                onNodeWithText("Dismiss").performClick()
                onNodeWithText("has no project.json", substring = true).assertDoesNotExist()
            }
        }

    @Test
    fun aBadFolderLeavesTheOpenProjectOpen() =
        withDir { dir ->
            val folder = project(dir)
            val bad = Files.createDirectory(dir.resolve("empty"))
            runComposeUiTest {
                val folders = ArrayDeque(listOf(folder, bad))
                setContent { App(AppModel(), { folders.removeFirst() }) }

                onNodeWithText("Open project…").performClick()
                onNodeWithText("Open another project…").performClick()

                onNodeWithText("has no project.json", substring = true).assertExists()
                onNodeWithText("Abhandlung über den kleinen Krieg").assertExists()
            }
        }

    @Test
    fun cancellingTheDialogDoesNothing() =
        runComposeUiTest {
            setContent { App(AppModel(), { null }) }

            onNodeWithText("Open project…").performClick()

            onNodeWithText("New project…").assertExists()
            onNodeWithText("Dismiss").assertDoesNotExist()
        }
}
