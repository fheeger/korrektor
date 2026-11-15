package korrektor.viewer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PageViewerTest {
    private fun ComposeUiTest.waitForPage(number: Int) =
        waitUntil { runCatching { onNodeWithContentDescription("Page $number").assertExists() }.isSuccess }

    // --- the picture --------------------------------------------------------------------------------------

    @Test
    fun theVisiblePartIsRequestedAtTheScaleThatFitsTheWidth() =
        runComposeUiTest {
            val source = FakePageSource(PageSize(200.0, 300.0))
            val state = ViewerState()
            setContent { PageViewer(source, page = 3, state = state) }

            waitForPage(3)
            val request = source.requests.first()
            assertEquals(3, request.page)
            assertEquals(0 to 0, request.x to request.y)
            // The test window is 1024 pixels wide: 200 points become 1024 pixels; the page is taller than the viewport.
            assertEquals(1024, request.width)
            assertEquals(state.viewportHeight, request.height)
            assertEquals(1024 / 200.0, request.scale, 1e-9)
        }

    @Test
    fun whileThePictureIsNotThereTheViewerSaysSoAndTheUiStaysFree() =
        runComposeUiTest {
            val gate = CompletableDeferred<Unit>()
            val source = FakePageSource(gate = gate)
            setContent { PageViewer(source, page = 1) }

            waitUntil { source.requests.isNotEmpty() }
            onNodeWithText("Loading…").assertExists()
            onNodeWithContentDescription("Page 1").assertDoesNotExist()

            gate.complete(Unit)
            waitForPage(1)
            onNodeWithText("Loading…").assertDoesNotExist()
        }

    @Test
    fun aFailureIsShownAsAMessage() =
        runComposeUiTest {
            val source = FakePageSource(failure = IllegalStateException("the scan is damaged"))
            setContent { PageViewer(source, page = 1) }

            val message = "The page cannot be shown: the scan is damaged"
            waitUntil { runCatching { onNodeWithText(message).assertExists() }.isSuccess }
            onNodeWithContentDescription("Page 1").assertDoesNotExist()
        }

    @Test
    fun anotherPageIsRequestedWhenThePageChanges() =
        runComposeUiTest {
            val source = FakePageSource()
            var page by mutableIntStateOf(1)
            setContent { PageViewer(source, page) }
            waitUntil { source.requests.size == 1 }

            page = 2

            waitUntil { source.requests.size == 2 }
            assertEquals(listOf(1, 2), source.requests.map { it.page })
            assertFalse(source.closed, "the viewer does not close a source it did not open")
            assertTrue(source.requests.all { it.width == 1024 })
        }

    // --- the toolbar --------------------------------------------------------------------------------------

    @Test
    fun theToolbarShowsTheZoomAndTheActiveFitMode() =
        runComposeUiTest {
            val source = FakePageSource(PageSize(200.0, 300.0))
            setContent { PageViewer(source, page = 1) }
            waitForPage(1)

            // 1024 pixels for 200 points is 5.12 pixels per point; actual size is 1.333: 384 %.
            onNodeWithContentDescription("Zoom").assertExists()
            onNodeWithText("384 %").assertExists()
            onNodeWithText("Fit width").assertIsSelected()

            onNodeWithText("Fit page").performClick()
            waitForIdle()
            onNodeWithText("Fit page").assertIsSelected()
        }

    @Test
    fun zoomInAsksForANewPictureAtTheNewScaleAndShowsThePercentage() =
        runComposeUiTest {
            val source = FakePageSource(PageSize(200.0, 300.0))
            val state = ViewerState()
            setContent { PageViewer(source, page = 1, state = state) }
            waitForPage(1)

            onNodeWithContentDescription("Zoom in").performClick()

            waitUntil { source.requests.size >= 2 }
            val request = source.requests.last()
            assertEquals(4.0 * ViewerState.ACTUAL_SIZE, request.scale, 1e-9, "the next step above 384 % is 400 %")
            assertEquals(state.scale, request.scale, 1e-9)
            onNodeWithText("400 %").assertExists()
        }

    @Test
    fun fitPageAsksForAPictureOfTheWholePage() =
        runComposeUiTest {
            val source = FakePageSource(PageSize(200.0, 300.0))
            val state = ViewerState()
            setContent { PageViewer(source, page = 1, state = state) }
            waitForPage(1)

            onNodeWithText("Fit page").performClick()

            waitUntil { source.requests.size >= 2 }
            val request = source.requests.last()
            assertEquals(state.viewportHeight / 300.0, request.scale, 1e-9)
            assertEquals(request.height, state.viewportHeight, "the page touches the bottom of the viewport")
            assertTrue(request.width < state.viewportWidth, "and is narrower than the viewport")
        }

    @Test
    fun theOldPictureStaysUntilTheNewOneIsThere() =
        runComposeUiTest {
            val source = FakePageSource()
            setContent { PageViewer(source, page = 1) }
            waitForPage(1)
            source.gate = CompletableDeferred() // the next picture takes its time

            onNodeWithContentDescription("Zoom in").performClick()

            waitUntil { source.requests.size >= 2 }
            onNodeWithContentDescription("Page 1").assertExists()
            onNodeWithText("Loading…").assertDoesNotExist()
        }

    @Test
    fun fastZoomingRendersOnlyTheNewestScaleToTheEnd() =
        runComposeUiTest {
            val source = FakePageSource()
            val state = ViewerState()
            setContent { PageViewer(source, page = 1, state = state) }
            waitForPage(1)
            val gate = CompletableDeferred<Unit>()
            source.gate = gate
            source.completed.clear()

            repeat(3) {
                onNodeWithContentDescription("Zoom out").performClick()
                waitForIdle()
            }
            val last = state.scale
            waitUntil { source.requests.size >= 4 }
            gate.complete(Unit)

            waitUntil { source.completed.isNotEmpty() }
            assertEquals(listOf(last), source.completed.map { it.scale }, "the older requests were cancelled")
        }

    // --- the wheel ----------------------------------------------------------------------------------------

    @Test
    fun thePlainWheelScrollsThePageUnderThePointer() =
        runComposeUiTest {
            val source = FakePageSource()
            val state = ViewerState()
            setContent { PageViewer(source, page = 1, state = state) }
            waitForPage(1)
            val before = state.scale

            onNodeWithContentDescription("Page 1").performMouseInput {
                moveTo(center)
                scroll(1f) // one notch down
            }
            waitForIdle()

            assertEquals(before, state.scale, 1e-9, "the plain wheel does not zoom")
            assertNotEquals(0.0, state.offsetY, "it scrolls")
        }
}
