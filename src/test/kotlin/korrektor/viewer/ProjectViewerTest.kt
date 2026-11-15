package korrektor.viewer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import korrektor.format.Metadata
import korrektor.format.Project
import korrektor.pdf.TestPage
import korrektor.pdf.TestPdfs
import korrektor.pdf.TestRect
import java.awt.Color
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ProjectViewerTest {
    private fun withProject(block: (Project) -> Unit) {
        val dir = Files.createTempDirectory("projectviewer")
        try {
            val pdf = dir.resolve("book.pdf")
            TestPdfs.write(pdf, listOf(TestPage(200f, 300f, rects = listOf(TestRect(0f, 0f, 100f, 100f, Color.RED)))))
            block(Project.create(dir.resolve("book.korrektur"), pdf, emptyList(), Metadata("Book")))
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.waitForPage(number: Int) =
        waitUntil { runCatching { onNodeWithContentDescription("Page $number").assertExists() }.isSuccess }

    @Test
    fun theProjectScreenShowsTheFirstPageFromTheProjectsOwnCopyOfThePdf() =
        withProject { project ->
            val opened = mutableListOf<Path>()
            val source = FakePageSource()
            runComposeUiTest {
                setContent {
                    ProjectViewer(project, ViewerState(1), pageSources = {
                        opened.add(it)
                        source
                    })
                }

                waitForPage(1)
                assertEquals(listOf(project.sourcePdf), opened)
                assertEquals(project.folder, project.sourcePdf.parent, "the copy inside the project")
            }
        }

    @Test
    fun theSourceIsClosedWhenTheProjectScreenLeavesTheWindow() =
        withProject { project ->
            val source = FakePageSource()
            runComposeUiTest {
                var shown by mutableStateOf(true)
                setContent {
                    if (shown) {
                        ProjectViewer(project, ViewerState(1), pageSources = { source })
                    }
                }
                waitUntil { source.requests.isNotEmpty() }
                assertFalse(source.closed)

                shown = false
                waitForIdle()

                assertTrue(source.closed)
            }
        }

    @Test
    fun theRealSourceShowsThePageAndLetsGoOfThePdfSoTheFileCanBeDeleted() =
        withProject { project ->
            runComposeUiTest {
                var shown by mutableStateOf(true)
                setContent { if (shown) ProjectViewer(project, ViewerState(1)) }
                waitForPage(1)

                shown = false
                waitForIdle()
                // The render thread closes the PDF a moment after the screen is gone; Windows refuses to delete
                // a file that is still open.
                val deadline = System.nanoTime() + WAIT_NANOS
                var deleted = false
                while (!deleted && System.nanoTime() < deadline) {
                    project.sourcePdf.toFile().setWritable(true)
                    deleted = project.sourcePdf.toFile().delete()
                    if (!deleted) Thread.sleep(POLL_MILLIS)
                }
                assertTrue(deleted, "the PDF is not held open any more")
            }
        }

    private companion object {
        const val WAIT_NANOS = 5_000_000_000L
        const val POLL_MILLIS = 50L
    }
}
