package korrektor.viewer

import korrektor.pdf.PixelRegion
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.function.ThrowingSupplier
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ViewerStateTest {
    private val actual = ViewerState.ACTUAL_SIZE

    /** A page of 200 x 300 points in a viewport of [width] x [height] pixels. */
    private fun state(
        width: Int = 400,
        height: Int = 300,
        native: Double? = null,
    ) = ViewerState().also {
        it.setViewport(width, height)
        it.setPage(PageSize(200.0, 300.0), native)
    }

    // --- fit modes ----------------------------------------------------------------------------------------

    @Test
    fun fitWidthMakesThePageAsWideAsTheViewport() {
        val state = state(400, 300)
        assertEquals(ZoomMode.FIT_WIDTH, state.mode, "the start mode")
        assertEquals(2.0, state.scale, 1e-9)
        assertEquals(400 to 600, state.contentWidth to state.contentHeight)
    }

    @Test
    fun fitPageMakesTheWholePageVisibleAndTouchesTheLimitingSide() {
        val state = state(400, 300)

        state.fitPage()

        assertEquals(1.0, state.scale, 1e-9, "the height limits: 300 points in 300 pixels")
        assertEquals(200 to 300, state.contentWidth to state.contentHeight)
        state.setViewport(300, 900)
        assertEquals(1.5, state.scale, 1e-9, "the width limits now: 200 points in 300 pixels")
    }

    @Test
    fun aFitModeFollowsTheViewportAndAFixedZoomStays() {
        val state = state(400, 300)
        state.setViewport(800, 300)
        assertEquals(4.0, state.scale, 1e-9)

        state.zoomTo(2.0)
        state.setViewport(100, 100)

        assertEquals(2.0, state.scale, 1e-9)
        assertEquals(ZoomMode.FIXED, state.mode)
    }

    @Test
    fun aFitModeFollowsThePage() {
        val state = state(400, 300)
        state.setPage(PageSize(100.0, 100.0), null)
        assertEquals(4.0, state.scale, 1e-9)
    }

    // --- steps --------------------------------------------------------------------------------------------

    @Test
    fun zoomingInAndOutMovesAlongTheStepsOfActualSize() {
        val state = state(400, 300) // fit width: 2.0 = 150 %
        assertEquals(150, state.percent)

        state.zoomIn()
        assertEquals(200, state.percent)
        assertEquals(2.0 * actual, state.scale, 1e-9)
        state.zoomOut()
        assertEquals(150, state.percent)
        state.zoomOut()
        assertEquals(125, state.percent)
        state.zoomOut()
        assertEquals(100, state.percent)
        assertEquals(actual, state.scale, 1e-9)
    }

    @Test
    fun zoomingOutStopsAtTheSmallestStep() {
        val state = state(400, 300)

        repeat(20) { state.zoomOut() }

        assertEquals(25, state.percent)
        assertEquals(0.25 * actual, state.scale, 1e-9)
    }

    @Test
    fun theResolutionOfTheScanIsAStopOnTheWayAndNotTheEnd() {
        val state = state(400, 300, native = 5.0) // 375 % of actual size, between 300 % and 400 %
        state.zoomTo(3.0 * actual)
        assertEquals(300, state.percent)

        state.zoomIn()
        assertEquals(5.0, state.scale, 1e-9, "one screen pixel per scan pixel")
        state.zoomIn()
        assertEquals(400, state.percent, "and it goes on")
        state.zoomOut()
        state.zoomOut()
        assertEquals(300, state.percent)
    }

    @Test
    fun aPageWithoutAScanHasNoSuchStop() {
        val state = state(400, 300, native = null)
        state.zoomTo(2.0 * actual)

        state.zoomIn()

        assertEquals(300, state.percent)
    }

    @Test
    fun theHighestZoomIs3200PercentWhateverTheScan() {
        for (native in listOf(null, 0.5, 5.0, 100.0)) {
            val state = state(400, 300, native = native)

            repeat(40) { state.zoomIn() }

            assertEquals(3200, state.percent, "native $native")
            assertEquals(32.0 * actual, state.scale, 1e-9)
            state.zoomBy(10.0, 0.0, 0.0)
            assertEquals(3200, state.percent, "also not with the wheel")
        }
    }

    @Test
    fun zoomingInFromAFitScaleBetweenTwoStepsGoesToTheNextStepAbove() {
        val state = state(2000, 300, native = 2.0) // fit width is 10 pixels per point: 750 %
        assertEquals(750, state.percent)

        state.zoomIn()
        assertEquals(800, state.percent)
        state.zoomOut()
        assertEquals(600, state.percent)
    }

    // --- the anchor ---------------------------------------------------------------------------------------

    @Test
    fun zoomingAroundAnAnchorKeepsThePagePointUnderIt() {
        val state = state(100, 100)
        state.zoomTo(2.0, 0.0, 0.0)
        state.scrollBy(50.0, 50.0)
        assertEquals(50.0 to 50.0, state.offsetX to state.offsetY)
        // Under the anchor (30, 40) is the page point ((50 + 30) / 2, (50 + 40) / 2) = (40, 45) points.

        state.zoomBy(2.0, 30.0, 40.0)

        assertEquals(4.0, state.scale, 1e-9)
        assertEquals(40.0 * 4 - 30.0, state.offsetX, 1e-9)
        assertEquals(45.0 * 4 - 40.0, state.offsetY, 1e-9)
    }

    @Test
    fun zoomingOutAtAnEdgeStopsAtTheEdgeOfThePage() {
        val state = state(100, 100)
        state.zoomTo(2.0, 0.0, 0.0)
        state.scrollBy(1000.0, 1000.0) // the far corner: 400 x 600 pixels in 100 x 100

        state.zoomBy(0.5, 50.0, 50.0)

        assertEquals(1.0, state.scale, 1e-9)
        assertEquals(100.0, state.offsetX, 1e-9, "200 pixels wide: at most 100 pixels scrolled")
        assertEquals(200.0, state.offsetY, 1e-9, "300 pixels high: at most 200 pixels scrolled")
    }

    @Test
    fun aPageSmallerThanTheViewportIsCentred() {
        val state = ViewerState()
        state.setViewport(400, 400)
        state.setPage(PageSize(100.0, 100.0), null)
        state.zoomTo(1.0)

        assertEquals(150.0, state.originX, 1e-9)
        assertEquals(150.0, state.originY, 1e-9)
        state.scrollBy(10.0, 10.0)
        assertEquals(0.0 to 0.0, state.offsetX to state.offsetY, "it cannot be moved")

        // The page point under the screen centre is (50, 50); after zooming to 2 it is still there.
        state.zoomTo(2.0, 200.0, 200.0)
        assertEquals(100.0, state.originX, 1e-9)
        assertEquals(200.0, 100.0 + 50.0 * 2, 1e-9)
    }

    @Test
    fun anAnchorOutsideThePageStillGivesAValidPosition() {
        val state = state(100, 100)
        state.zoomTo(1.0, 0.0, 0.0)
        state.zoomBy(1.5, 99.0, 99.0)
        assertTrue(state.offsetX in 0.0..(state.contentWidth - 100.0))
        assertTrue(state.offsetY in 0.0..(state.contentHeight - 100.0))
    }

    // --- region and percentage ----------------------------------------------------------------------------

    @Test
    fun theVisibleRegionIsTheViewportPartOfThePageAtTheCurrentScale() {
        val state = state(100, 100)
        state.zoomTo(2.0, 0.0, 0.0)
        state.scrollBy(30.0, 60.0)

        assertEquals(PixelRegion(30, 60, 100, 100), state.visibleRegion)
    }

    @Test
    fun aPageSmallerThanTheViewportHasAWholePageRegion() {
        val state = ViewerState()
        state.setViewport(400, 400)
        state.setPage(PageSize(100.0, 100.0), null)
        state.zoomTo(1.0)
        assertEquals(PixelRegion(0, 0, 100, 100), state.visibleRegion)
    }

    @Test
    fun withoutAPageOrAViewportThereIsNothingToShow() {
        assertNull(ViewerState().visibleRegion)
        val noViewport = ViewerState().also { it.setPage(PageSize(200.0, 300.0), null) }
        assertNull(noViewport.visibleRegion)
        // and nothing breaks when the buttons are used
        noViewport.zoomIn()
        noViewport.fitPage()
    }

    // --- the wheel ----------------------------------------------------------------------------------------

    @Test
    fun ctrlWheelZoomsByAboutTenPercentPerNotchAroundThePointer() {
        val state = state(100, 100)
        state.zoomTo(2.0, 0.0, 0.0)
        state.scrollBy(50.0, 50.0)
        // Under (30, 40) is the page point (40, 45), as in the anchor test.

        state.wheel(ctrl = true, x = 30.0, y = 40.0, deltaY = -1.0)

        assertEquals(2.2, state.scale, 1e-9, "one notch up zooms in by 10 %")
        assertEquals(40.0 * 2.2 - 30.0, state.offsetX, 1e-9)
        assertEquals(45.0 * 2.2 - 40.0, state.offsetY, 1e-9)

        state.wheel(ctrl = true, x = 30.0, y = 40.0, deltaY = 1.0)
        assertEquals(2.0, state.scale, 1e-9, "and one notch down zooms out by the same factor")
    }

    @Test
    fun theWheelWithoutCtrlScrollsAndDoesNotZoom() {
        val state = state(100, 100)
        state.zoomTo(2.0, 0.0, 0.0)

        state.wheel(ctrl = false, x = 10.0, y = 10.0, deltaY = 1.0)

        assertEquals(2.0, state.scale, 1e-9)
        assertEquals(80.0, state.offsetY, 1e-9, "80 pixels per notch")
        assertEquals(0.0, state.offsetX, 1e-9)
    }

    @Test
    fun aSidewaysWheelDeltaScrollsSideways() {
        val state = state(100, 100)
        state.zoomTo(2.0, 0.0, 0.0)

        state.wheel(ctrl = false, x = 10.0, y = 10.0, deltaX = 1.0)

        assertEquals(80.0, state.offsetX, 1e-9)
        assertEquals(0.0, state.offsetY, 1e-9)
    }

    // --- panning ------------------------------------------------------------------------------------------

    @Test
    fun scrollingStopsAtTheEdgesOfThePage() {
        val state = state(100, 100)
        state.zoomTo(2.0, 0.0, 0.0) // 400 x 600 pixels in 100 x 100

        state.scrollBy(-50.0, -50.0)
        assertEquals(0.0 to 0.0, state.offsetX to state.offsetY, "not beyond the top left")

        state.scrollBy(10_000.0, 10_000.0)
        assertEquals(300.0 to 500.0, state.offsetX to state.offsetY, "not beyond the bottom right")
    }

    @Test
    fun aPageSmallerThanTheViewportInOneDirectionCannotBeMovedThere() {
        val state = ViewerState()
        state.setViewport(100, 100)
        state.setPage(PageSize(50.0, 300.0), null)
        state.zoomTo(1.0) // 50 pixels wide, 300 high

        state.scrollBy(40.0, 40.0)

        assertEquals(0.0, state.offsetX, 1e-9, "centred sideways")
        assertEquals(25.0, state.originX, 1e-9)
        assertEquals(40.0, state.offsetY, 1e-9, "but it moves up and down")
        assertTrue(state.canPan)
    }

    @Test
    fun aPageThatFitsCannotBePanned() {
        val state = ViewerState()
        state.setViewport(400, 400)
        state.setPage(PageSize(100.0, 100.0), null)
        state.zoomTo(1.0)
        assertFalse(state.canPan)
    }

    @Test
    fun theScrollbarsShowAndMoveThePosition() {
        val state = state(100, 100)
        state.zoomTo(2.0, 0.0, 0.0) // 400 x 600
        val vertical = ViewerScrollbarAdapter(state, vertical = true)
        val horizontal = ViewerScrollbarAdapter(state, vertical = false)

        assertEquals(600.0 to 100.0, vertical.contentSize to vertical.viewportSize)
        assertEquals(400.0 to 100.0, horizontal.contentSize to horizontal.viewportSize)
        runBlocking { vertical.scrollTo(250.0) }
        runBlocking { horizontal.scrollTo(120.0) }

        assertEquals(250.0, state.offsetY, 1e-9)
        assertEquals(120.0, state.offsetX, 1e-9)
        assertEquals(250.0, vertical.scrollOffset, 1e-9, "and the other way round")
        runBlocking { vertical.scrollTo(100_000.0) }
        assertEquals(500.0, state.offsetY, 1e-9, "dragging the scrollbar to the end stops at the end")
    }

    // --- what to render -----------------------------------------------------------------------------------

    @Test
    fun aLargePageIsRequestedAsTheVisiblePartAndAMarginOfOneViewport() {
        val state = state(100, 100)
        state.zoomTo(2.0, 0.0, 0.0) // 400 x 600
        state.scrollBy(150.0, 250.0) // visible x 150..250, y 250..350

        val request = state.nextRequest(null, wholePageLimit = 0)!!

        assertEquals(2.0, request.scale, 1e-9)
        assertEquals(PixelRegion(50, 150, 300, 300), request.region, "100 pixels more on every side")
    }

    @Test
    fun theMarginStopsAtTheEdgesOfThePage() {
        val state = state(100, 100)
        state.zoomTo(2.0, 0.0, 0.0)

        assertEquals(PixelRegion(0, 0, 200, 200), state.nextRequest(null, wholePageLimit = 0)!!.region)
        state.scrollBy(1000.0, 1000.0)
        assertEquals(PixelRegion(200, 400, 200, 200), state.nextRequest(null, wholePageLimit = 0)!!.region)
    }

    @Test
    fun movingInsideTheRequestedRegionAsksForNothingNew() {
        val state = state(100, 100)
        state.zoomTo(2.0, 0.0, 0.0)
        state.scrollBy(150.0, 250.0)
        val have = state.nextRequest(null, wholePageLimit = 0) // x 50..350, y 150..450

        state.scrollBy(90.0, -90.0) // visible x 240..340, y 160..260: inside

        assertEquals(have, state.nextRequest(have, wholePageLimit = 0))
    }

    @Test
    fun leavingTheRequestedRegionOrChangingTheScaleAsksForANewPicture() {
        val state = state(100, 100)
        state.zoomTo(2.0, 0.0, 0.0)
        state.scrollBy(150.0, 250.0)
        val have = state.nextRequest(null, wholePageLimit = 0)!! // y 150..450

        state.scrollBy(0.0, 120.0) // visible y 370..470 reaches beyond 450
        val moved = state.nextRequest(have, wholePageLimit = 0)!!
        assertNotEquals(have, moved)
        assertTrue(moved.region.y + moved.region.height >= 470)

        state.zoomTo(3.0)
        assertNotEquals(moved.scale, state.nextRequest(moved, wholePageLimit = 0)!!.scale)
    }

    @Test
    fun aPageThatBecomesLargerThanTheLimitFallsBackToTheMarginRule() {
        val state = state(100, 100)
        state.zoomTo(1.0, 0.0, 0.0) // 200 x 300 = 60,000 pixels
        assertEquals(PixelRegion(0, 0, 200, 300), state.nextRequest(null, wholePageLimit = 100_000)!!.region)

        state.zoomTo(2.0, 0.0, 0.0) // 400 x 600 = 240,000 pixels: over the limit

        assertEquals(PixelRegion(0, 0, 200, 200), state.nextRequest(null, wholePageLimit = 100_000)!!.region)
    }

    @Test
    fun aHugeViewportGetsASmallerMarginInsteadOfAnImageThatIsTooLarge() {
        val state = ViewerState()
        state.setViewport(7000, 4000)
        state.setPage(PageSize(10_000.0, 10_000.0), null)
        state.zoomTo(1.0)

        val region = state.nextRequest(null)!!.region

        assertTrue(region.width.toLong() * region.height <= 30_000_000L, "$region")
        assertTrue(region.width >= 7000 && region.height >= 4000, "it still covers the viewport")
    }

    // --- pages --------------------------------------------------------------------------------------------

    @Test
    fun nextAndPreviousMoveByOnePageAndStopAtTheEnds() {
        val state = ViewerState(pageCount = 3)
        assertEquals(1, state.page)
        assertFalse(state.hasPrevious)
        assertFalse(state.previous(), "there is no page 0")
        assertEquals(1, state.page)

        assertTrue(state.next())
        assertTrue(state.next())
        assertEquals(3, state.page)
        assertFalse(state.hasNext)
        assertFalse(state.next(), "there is no page 4")
        assertEquals(3, state.page)
        assertTrue(state.previous())
        assertEquals(2, state.page)
    }

    @Test
    fun aPageNumberTypedByTheUserIsCheckedAgainstThePageCount() {
        val state = ViewerState(pageCount = 165)

        assertEquals(42, state.pageOf(" 42 "))
        assertEquals(165, state.pageOf("165"))
        assertEquals(1, state.pageOf("1"))
        for (wrong in listOf("0", "-3", "166", "4.5", "abc", "", "  ", "1 2")) {
            assertNull(state.pageOf(wrong), "'$wrong'")
        }
    }

    @Test
    fun aBookWithoutPagesStillHasAPageOne() {
        val state = ViewerState(pageCount = 0)
        assertEquals(1, state.pageCount)
        assertFalse(state.hasNext)
    }

    @Test
    fun changingThePageForgetsTheOldPagesSizeAndPositionButKeepsTheZoomMode() {
        val state = state(100, 100)
        state.zoomTo(2.0, 0.0, 0.0)
        state.scrollBy(50.0, 50.0)
        val bookState = ViewerState(pageCount = 2)
        bookState.setViewport(400, 300)
        bookState.setPage(PageSize(200.0, 300.0), 4.0)
        bookState.fitPage()

        bookState.goTo(2)

        assertNull(bookState.pageSize, "nothing is drawn until the new page's size is known")
        assertNull(bookState.visibleRegion)
        assertEquals(ZoomMode.FIT_PAGE, bookState.mode)
        assertEquals(0.0 to 0.0, bookState.offsetX to bookState.offsetY)
        bookState.setPage(PageSize(100.0, 100.0), null)
        assertEquals(3.0, bookState.scale, 1e-9, "fit page follows the new size: 300 pixels for 100 points")
    }

    @Test
    fun goingToTheSamePageChangesNothing() {
        val state = ViewerState(pageCount = 3)
        state.setViewport(100, 100)
        state.setPage(PageSize(200.0, 300.0), null)
        state.zoomTo(2.0, 0.0, 0.0)
        state.scrollBy(10.0, 10.0)

        assertTrue(state.goTo(1))

        assertEquals(10.0, state.offsetX, 1e-9)
        assertEquals(PageSize(200.0, 300.0), state.pageSize)
    }

    // --- found in the review of S04 ------------------------------------------------------------------------

    @Test
    fun aViewportOfMoreThan30MillionPixelsIsRequestedWithoutMarginAndDoesNotHang() {
        val state = ViewerState()
        state.setViewport(7616, 4100) // 31.2 million pixels: an 8K screen with the window maximised
        state.setPage(PageSize(300.0, 480.0), null)

        val request = assertTimeoutPreemptively(Duration.ofSeconds(5), ThrowingSupplier { state.nextRequest(null) })

        assertEquals(PixelRegion(0, 0, 7616, 4100), request!!.region, "just the visible part: no margin fits")
    }

    @Test
    fun aTinyPageDoesNotMakeTheScaleOrAZoomThrow() {
        val state = ViewerState(pageCount = 2)
        state.setViewport(900, 900)
        state.setPage(PageSize(300.0, 480.0), 1.0 * ViewerState.ACTUAL_SIZE)
        state.zoomTo(1.0 * ViewerState.ACTUAL_SIZE) // fixed at 100 %

        state.goTo(2)
        // The floor, a tenth of the fit-page scale, is 1.5 here: above the limit of the page's scan.
        state.setPage(PageSize(60.0, 60.0), 1.0 * ViewerState.ACTUAL_SIZE)

        assertTrue(state.scale >= state.minScale, "reading the scale works: ${state.scale}")
        state.zoomTo(0.5) // and so does zooming
        state.zoomIn()
        state.zoomOut()
        assertTrue(state.scale >= state.minScale)
    }

    // --- T035: the limit was the scan's resolution -----------------------------------------------------------

    /** Clausewitz: the scan is 2.77 pixels per point (208 % of actual size) and fit width gives 321 %. */
    private fun clausewitz(): ViewerState {
        val width = 233.6 // points
        return ViewerState(pageCount = 99).also {
            it.setViewport((4.2844 * width).toInt() + 1, 800) // 321 % of actual size is 4.284 pixels per point
            it.setPage(PageSize(width, 360.0), 2.7733)
        }
    }

    @Test
    fun aCoarseScanCanBeZoomedInBeyondItsResolutionFromFitWidth() {
        val state = clausewitz()
        assertTrue(state.percent in 320..322, "fit width: ${state.percent} %")

        state.zoomIn()
        assertEquals(400, state.percent)
        repeat(30) { state.zoomIn() }

        assertEquals(3200, state.percent, "up to the limit, not stuck at the scan's 208 %")
    }

    @Test
    fun afterZoomingOutTheZoomCanGoBackInPastTheScansResolution() {
        val state = clausewitz()
        repeat(3) { state.zoomOut() }
        assertTrue(state.percent < 208, "zoomed out: ${state.percent} %")

        repeat(30) { state.zoomIn() }

        assertEquals(3200, state.percent)
    }

    // --- T036: smooth scrolling ------------------------------------------------------------------------------

    @Test
    fun aPageOfUpTo16MillionPixelsIsRequestedWholeAndScrollingAsksForNothingNew() {
        val state = state(100, 100)
        state.zoomTo(2.0, 0.0, 0.0) // 400 x 600 pixels

        val first = state.nextRequest(null)!!

        assertEquals(PixelRegion(0, 0, 400, 600), first.region, "the whole page, not just what is visible")
        for ((dx, dy) in listOf(0.0 to 0.0, 300.0 to 0.0, 0.0 to 500.0, 300.0 to 500.0)) {
            state.scrollBy(-1000.0, -1000.0)
            state.scrollBy(dx, dy)
            assertEquals(first, state.nextRequest(first), "at $dx, $dy")
        }
    }
}
