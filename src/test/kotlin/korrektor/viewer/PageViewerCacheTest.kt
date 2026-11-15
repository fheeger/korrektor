package korrektor.viewer

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The viewer with a [PageCache]: what is rendered, when, and what is cancelled. */
@OptIn(ExperimentalTestApi::class)
class PageViewerCacheTest {
    private fun ComposeUiTest.waitForPage(number: Int) =
        waitUntil { runCatching { onNodeWithContentDescription("Page $number").assertExists() }.isSuccess }

    private fun pagesRendered(source: FakePageSource) = source.requests.map { it.page }.toSet()

    @Test
    fun onlyThePageShownAndItsNeighboursAreRendered() =
        runComposeUiTest {
            val source = FakePageSource()
            val state = ViewerState(pageCount = 1000).also { it.goTo(5) }
            setContent { PageViewer(PageCache(source), state = state) }
            waitForPage(5)

            waitUntil { pagesRendered(source) == setOf(4, 5, 6) }
            waitForIdle()

            assertEquals(setOf(4, 5, 6), pagesRendered(source), "not page 7, 20 or 1000")
            assertEquals(3, source.requests.size, "each once")
        }

    @Test
    fun theFirstAndLastPageHaveOnlyOneNeighbour() =
        runComposeUiTest {
            val source = FakePageSource()
            val state = ViewerState(pageCount = 3)
            setContent { PageViewer(PageCache(source), state = state) }
            waitForPage(1)
            waitUntil { pagesRendered(source) == setOf(1, 2) }
            waitForIdle()
            assertEquals(setOf(1, 2), pagesRendered(source))
        }

    @Test
    fun goingToANeighbourIsAHitInTheCache() =
        runComposeUiTest {
            val source = FakePageSource()
            val state = ViewerState(pageCount = 10).also { it.goTo(5) }
            setContent { PageViewer(PageCache(source), state = state) }
            waitForPage(5)
            waitUntil { pagesRendered(source) == setOf(4, 5, 6) }
            waitForIdle()
            val before = source.requests.size

            runOnIdle { state.next() }
            waitForPage(6)
            waitForIdle()

            // Page 6 came from the cache; only its own neighbour, page 7, was rendered for the next step.
            waitUntil { 7 in pagesRendered(source) }
            assertEquals(1, source.requests.count { it.page == 6 }, "page 6 was rendered once, not again")
            assertTrue(source.requests.size <= before + 1, "only page 7 is new")
        }

    @Test
    fun aZoomCancelsTheNeighboursThatWereStillBeingRendered() =
        runComposeUiTest {
            val source = FakePageSource()
            val state = ViewerState(pageCount = 10).also { it.goTo(5) }
            setContent { PageViewer(PageCache(source), state = state) }
            waitForPage(5)
            waitUntil { pagesRendered(source) == setOf(4, 5, 6) }
            waitForIdle()
            val oldScale = state.scale
            // From now on the neighbours wait; the page shown does not.
            source.onlyPages = setOf(4, 6, 3, 7)
            source.gate = CompletableDeferred()
            source.completed.clear()

            runOnIdle { state.zoomIn() }
            waitUntil { source.requests.any { it.page == 6 && it.scale != oldScale } }
            val newScale = state.scale
            runOnIdle { state.zoomIn() } // another zoom while the neighbours at the first new scale are waiting
            waitUntil { source.requests.any { it.page == 6 && it.scale == state.scale } }
            source.gate.complete(Unit)

            waitUntil { source.completed.any { it.page == 6 && it.scale == state.scale } }
            assertTrue(
                source.completed.none { it.page in setOf(4, 6) && it.scale == newScale },
                "the neighbours at the scale that was zoomed away from were cancelled",
            )
        }

    @Test
    fun withoutACacheNothingIsRenderedInAdvance() =
        runComposeUiTest {
            val source = FakePageSource() // a source that does not remember: prefetching would be wasted work
            val state = ViewerState(pageCount = 10).also { it.goTo(5) }
            setContent { PageViewer(source, state = state) }
            waitForPage(5)
            waitForIdle()
            assertEquals(setOf(5), pagesRendered(source))
        }
}
