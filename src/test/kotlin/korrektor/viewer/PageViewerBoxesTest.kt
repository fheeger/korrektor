package korrektor.viewer

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import korrektor.format.Box
import korrektor.format.Line
import korrektor.format.Metadata
import korrektor.format.PageInfo
import korrektor.format.PageLayer
import korrektor.format.Project
import korrektor.format.Region
import korrektor.format.RegionType
import korrektor.format.Word
import java.nio.file.Files
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class PageViewerBoxesTest {
    private fun ComposeUiTest.waitForPage(number: Int) =
        waitUntil { runCatching { onNodeWithContentDescription("Page $number").assertExists() }.isSuccess }

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil { runCatching { onNodeWithContentDescription(text).assertExists() }.isSuccess }

    /** The page of the fake source is 200 x 300 points: 5.12 pixels per point in the 1024 pixel wide test window. */
    private val boxes =
        PageBoxes(
            lines = listOf(Box(10.0, 10.0, 100.0, 12.0), Box(10.0, 290.0, 100.0, 8.0)), // the second is far below
            words =
                listOf(
                    Box(10.0, 10.0, 30.0, 12.0),
                    Box(45.0, 10.0, 30.0, 12.0),
                    Box(80.0, 10.0, 30.0, 12.0),
                    Box(10.0, 290.0, 30.0, 8.0), // below the viewport: 290 points are 1485 pixels
                ),
        )

    @Test
    fun theOverlayIsOffUntilTheToggleIsOnAndThenDrawsWhatIsInView() =
        runComposeUiTest {
            val source = FakePageSource()
            setContent { PageViewer(source, boxes = { boxes }) }
            waitForPage(1)

            onNodeWithContentDescription("Word boxes: 3, line boxes: 1", substring = true).assertDoesNotExist()
            onNodeWithText("Word boxes").performClick()
            waitForText("Word boxes: 3, line boxes: 1")

            onNodeWithText("Word boxes").assertIsSelected()
            onNodeWithText("Word boxes").performClick()
            waitForIdle()
            onNodeWithContentDescription("Word boxes: 3, line boxes: 1").assertDoesNotExist()
        }

    @Test
    fun scrollingBringsTheOtherBoxesIntoViewAndTheFirstOnesOut() =
        runComposeUiTest {
            val source = FakePageSource()
            val state = ViewerState()
            setContent { PageViewer(source, state = state, boxes = { boxes }) }
            waitForPage(1)
            runOnIdle { state.showWordBoxes = true }
            waitForText("Word boxes: 3, line boxes: 1")

            runOnIdle { state.scrollBy(0.0, 10_000.0) } // to the bottom of the page

            waitForText("Word boxes: 1, line boxes: 1")
        }

    @Test
    fun aPageWithoutTextShowsNoBoxesAndNoError() =
        runComposeUiTest {
            val source = FakePageSource()
            setContent { PageViewer(source, boxes = { PageBoxes.NONE }) }
            waitForPage(1)

            onNodeWithText("Word boxes").performClick()

            waitForText("Word boxes: 0, line boxes: 0")
            onNodeWithText("The page cannot be shown", substring = true).assertDoesNotExist()
        }

    @Test
    fun boxesThatCannotBeReadAreNotAnErrorEither() =
        runComposeUiTest {
            val source = FakePageSource()
            setContent { PageViewer(source, boxes = { error("the page file is damaged") }) }
            waitForPage(1)

            onNodeWithText("Word boxes").performClick()

            waitForText("Word boxes: 0, line boxes: 0")
            onNodeWithText("The page cannot be shown", substring = true).assertDoesNotExist()
            onNodeWithContentDescription("Page 1").assertExists()
        }

    @Test
    fun theBoxesAreThoseOfTheOriginalLayerOfTheProjectNotOfTheCorrectedOne() {
        val dir = Files.createTempDirectory("boxesproject")
        try {
            val pdf = dir.resolve("book.pdf").also { Files.write(it, "%PDF-1.4\n".toByteArray()) }
            val project = Project.create(dir.resolve("b.korrektur"), pdf, listOf(PageInfo(1)), Metadata("B"))

            fun layer(words: Int) =
                PageLayer(
                    1,
                    200.0,
                    300.0,
                    listOf(
                        Region(
                            "r1",
                            RegionType.TEXT,
                            1,
                            null,
                            listOf(
                                Line(
                                    "l1",
                                    Box(10.0, 10.0, 100.0, 12.0),
                                    null,
                                    List(words) {
                                        Word(
                                            "w$it",
                                            Box(
                                                10.0 + 20 * it,
                                                10.0,
                                                15.0,
                                                12.0,
                                            ),
                                        )
                                    },
                                ),
                            ),
                        ),
                    ),
                )
            project.writeOriginalPage(1, layer(words = 5))
            project.writePage(1, layer(words = 2)) // the user corrected it: two words
            runComposeUiTest {
                val source = FakePageSource()
                setContent { ProjectViewer(project, ViewerState(1), pageSources = { source }) }
                waitForPage(1)

                onNodeWithText("Word boxes").performClick()

                waitForText("Word boxes: 5, line boxes: 1")
            }
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }
}
