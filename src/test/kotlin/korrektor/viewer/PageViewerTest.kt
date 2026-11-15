package korrektor.viewer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PageViewerTest {
    @Test
    fun thePageIsRequestedFittedToTheWidthAndShownWhenItIsThere() =
        runComposeUiTest {
            val source = FakePageSource(PageSize(200.0, 300.0))
            setContent { PageViewer(source, page = 3) }

            waitUntil { source.requests.isNotEmpty() }
            waitUntil { runCatching { onNodeWithContentDescription("Page 3").assertExists() }.isSuccess }
            val request = source.requests.first()
            assertEquals(3, request.page)
            assertEquals(0 to 0, request.x to request.y)
            // The test window is 1024 pixels wide: 200 points become 1024 pixels, 300 points 1536.
            assertEquals(1024, request.width)
            assertEquals(1536, request.height)
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
            waitUntil { runCatching { onNodeWithContentDescription("Page 1").assertExists() }.isSuccess }
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
}
