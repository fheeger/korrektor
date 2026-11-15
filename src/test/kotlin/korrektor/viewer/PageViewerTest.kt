package korrektor.viewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ScrollWheel
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
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
    fun theWholePageIsRequestedAtTheScaleThatFitsTheWidth() =
        runComposeUiTest {
            val source = FakePageSource(PageSize(200.0, 300.0))
            val state = ViewerState(pageCount = 5).also { it.goTo(3) }
            setContent { PageViewer(source, state = state) }

            waitForPage(3)
            val request = source.requests.first()
            assertEquals(3, request.page)
            assertEquals(0 to 0, request.x to request.y)
            // The test window is 1024 pixels wide: 200 points become 1024 pixels and 300 points 1536. The page is
            // taller than the viewport but small enough to be rendered whole.
            assertEquals(1024, request.width)
            assertEquals(1536, request.height)
            assertEquals(1024 / 200.0, request.scale, 1e-9)
        }

    @Test
    fun whileThePictureIsNotThereTheViewerSaysSoAndTheUiStaysFree() =
        runComposeUiTest {
            val gate = CompletableDeferred<Unit>()
            val source = FakePageSource(gate = gate)
            setContent { PageViewer(source) }

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
            setContent { PageViewer(source) }

            val message = "The page cannot be shown: the scan is damaged"
            waitUntil { runCatching { onNodeWithText(message).assertExists() }.isSuccess }
            onNodeWithContentDescription("Page 1").assertDoesNotExist()
        }

    @Test
    fun anotherPageIsRequestedWhenThePageChanges() =
        runComposeUiTest {
            val source = FakePageSource()
            val state = ViewerState(pageCount = 2)
            setContent { PageViewer(source, state = state) }
            waitUntil { source.requests.size == 1 }

            runOnIdle { state.goTo(2) }

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
            setContent { PageViewer(source) }
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
            setContent { PageViewer(source, state = state) }
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
            setContent { PageViewer(source, state = state) }
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
            setContent { PageViewer(source) }
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
            setContent { PageViewer(source, state = state) }
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
            setContent { PageViewer(source, state = state) }
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

    // --- panning ------------------------------------------------------------------------------------------

    @Test
    fun draggingThePageMovesIt() =
        runComposeUiTest {
            val source = FakePageSource()
            val state = ViewerState()
            setContent { PageViewer(source, state = state) }
            waitForPage(1)

            onNodeWithContentDescription("Page 1").performMouseInput {
                moveTo(center)
                press()
                moveBy(Offset(0f, -100f)) // dragging up shows lower parts of the page
                release()
            }
            waitForIdle()

            assertTrue(state.offsetY in 90.0..100.0, "moved by the drag, less the touch slop: ${state.offsetY}")
        }

    @Test
    fun theScrollbarsAreThereOnlyInTheDirectionsInWhichThePageIsLarger() =
        runComposeUiTest {
            val source = FakePageSource()
            val state = ViewerState()
            setContent { PageViewer(source, state = state) }
            waitForPage(1)

            // Fit width: as wide as the viewport, much taller.
            onNodeWithTag("Vertical scrollbar").assertExists()
            onNodeWithTag("Horizontal scrollbar").assertDoesNotExist()

            runOnIdle { state.zoomTo(state.scale * 2) }
            waitForIdle()
            onNodeWithTag("Horizontal scrollbar").assertExists()
        }

    @Test
    fun movingInsideTheRenderedRegionRendersNothingAndLeavingItRendersAgain() =
        runComposeUiTest {
            val source = FakePageSource()
            val state = ViewerState()
            setContent { PageViewer(source, state = state) }
            waitForPage(1)
            runOnIdle { state.zoomTo(state.scale * 8, 0.0, 0.0) } // far above 16 million pixels: no whole page
            waitUntil { source.requests.size >= 2 }
            waitForIdle()
            val first = source.requests.size

            runOnIdle { state.scrollBy(0.0, 100.0) } // inside the margin
            waitForIdle()
            assertEquals(first, source.requests.size, "no new picture")

            runOnIdle { state.scrollBy(0.0, 3000.0) } // beyond it
            waitUntil { source.requests.size > first }
            val request = source.requests.last()
            assertTrue(request.y <= state.offsetY && request.y + request.height >= state.offsetY + state.viewportHeight)
        }

    // --- navigation ---------------------------------------------------------------------------------------

    @Test
    fun theToolbarShowsThePageAndTheButtonsStopAtTheEnds() =
        runComposeUiTest {
            val source = FakePageSource()
            val state = ViewerState(pageCount = 3)
            setContent { PageViewer(source, state = state) }
            waitForPage(1)

            onNodeWithText("Page 1 of 3").assertExists()
            onNodeWithContentDescription("Previous page").assertIsNotEnabled()
            onNodeWithContentDescription("Next page").assertIsEnabled()

            onNodeWithContentDescription("Next page").performClick()
            waitForPage(2)
            onNodeWithText("Page 2 of 3").assertExists()
            onNodeWithContentDescription("Previous page").assertIsEnabled()

            onNodeWithContentDescription("Next page").performClick()
            waitForPage(3)
            onNodeWithContentDescription("Next page").assertIsNotEnabled()
            onNodeWithContentDescription("Previous page").performClick()
            waitForPage(2)
            assertEquals(2, state.page)
        }

    @Test
    fun typingAPageNumberAndPressingEnterGoesThere() =
        runComposeUiTest {
            val source = FakePageSource()
            val state = ViewerState(pageCount = 10)
            setContent { PageViewer(source, state = state) }
            waitForPage(1)

            onNodeWithContentDescription("Go to page").performTextReplacement("7")
            onNodeWithContentDescription("Go to page").performKeyInput { pressKey(Key.Enter) }

            waitForPage(7)
            assertEquals(7, state.page)
            onNodeWithText("Page 7 of 10").assertExists()
        }

    @Test
    fun aWrongPageNumberIsRejectedAndTheFieldShowsTheCurrentPageAgain() =
        runComposeUiTest {
            val source = FakePageSource()
            val state = ViewerState(pageCount = 10)
            setContent { PageViewer(source, state = state) }
            waitForPage(1)

            for (wrong in listOf("0", "11", "abc", "")) {
                onNodeWithContentDescription("Go to page").performTextReplacement(wrong)
                onNodeWithContentDescription("Go to page").performKeyInput { pressKey(Key.Enter) }
                waitForIdle()
                assertEquals(1, state.page, "'$wrong' is not a page")
                onNodeWithContentDescription("Go to page").assert(hasText("1"))
            }
        }

    @Test
    fun aPageSwitchCancelsTheRenderOfTheOldPageAndAsksForTheNewOne() =
        runComposeUiTest {
            val source = FakePageSource()
            val state = ViewerState(pageCount = 3)
            setContent { PageViewer(source, state = state) }
            waitForPage(1)
            val gate = CompletableDeferred<Unit>()
            source.gate = gate
            source.completed.clear()
            runOnIdle { state.goTo(2) }
            waitUntil { source.requests.any { it.page == 2 } }

            gate.complete(Unit)

            waitUntil { source.completed.isNotEmpty() }
            assertEquals(listOf(2), source.completed.map { it.page })
        }

    @Test
    fun fitWidthFollowsTheNewPagesSizeAndAFixedZoomStaysWithThePageAtItsTop() =
        runComposeUiTest {
            val source = FakePageSource(sizes = mapOf(1 to PageSize(200.0, 300.0), 2 to PageSize(100.0, 400.0)))
            val state = ViewerState(pageCount = 2)
            setContent { PageViewer(source, state = state) }
            waitForPage(1)
            assertEquals(1024 / 200.0, state.scale, 1e-9)

            runOnIdle { state.goTo(2) }
            waitForPage(2)
            waitUntil { source.requests.last().page == 2 }
            assertEquals(1024 / 100.0, state.scale, 1e-9, "fit width: the new page fills the width too")
            assertEquals(1024, source.requests.last().width)

            runOnIdle {
                state.zoomTo(2.0, 0.0, 0.0)
                state.scrollBy(0.0, 100.0)
                state.goTo(1)
            }
            waitForPage(1)
            waitUntil { state.pageSize == PageSize(200.0, 300.0) }
            assertEquals(2.0, state.scale, 1e-9, "a fixed zoom stays")
            assertEquals(0.0, state.offsetY, 1e-9, "and the page starts at its top")
        }

    @Test
    fun aSidewaysWheelDeltaScrollsTheZoomedPageSideways() =
        runComposeUiTest {
            val source = FakePageSource()
            val state = ViewerState()
            setContent { PageViewer(source, state = state) }
            waitForPage(1)
            runOnIdle { state.zoomTo(state.scale * 2, 0.0, 0.0) } // wider than the window now

            // Compose Desktop reports Shift+wheel, a tilt wheel and a touchpad swipe as a horizontal delta.
            onNodeWithContentDescription("Page 1").performMouseInput {
                moveTo(center)
                scroll(1f, ScrollWheel.Horizontal)
            }
            waitForIdle()

            assertNotEquals(0.0, state.offsetX, "scrolled sideways")
            assertEquals(0.0, state.offsetY, 1e-9, "and not up or down")
        }

    @Test
    fun scrollingToTheBottomOfAFitWidthPageNeverWaitsForAPicture() =
        runComposeUiTest {
            val source = FakePageSource()
            val state = ViewerState()
            setContent { PageViewer(source, state = state) }
            waitForPage(1)
            val first = source.requests.size

            runOnIdle { state.scrollBy(0.0, 10_000.0) } // to the bottom of the page
            waitForIdle()

            assertEquals(first, source.requests.size, "the whole page is already there")
            val request = source.requests.first()
            assertEquals(1536, request.height, "the first picture was the whole page: 300 points at 5.12 pixels")
            assertEquals(0, request.y)
        }
}
