package korrektor.viewer

import korrektor.format.Box
import korrektor.importer.PageGeometry
import korrektor.pdf.PdfDocument
import korrektor.pdf.PdfPage
import korrektor.pdf.PdfRect
import korrektor.pdf.TestPage
import korrektor.pdf.TestPdfs
import korrektor.pdf.TestRect
import java.awt.Color
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class WordBoxesTest {
    /** A page of 200 x 300 points in a viewport of 150 x 150 pixels. */
    private fun state(): ViewerState =
        ViewerState().also {
            it.setViewport(150, 150)
            it.setPage(PageSize(200.0, 300.0), null)
        }

    @Test
    fun aBoxIsPlacedWithTheScaleAndTheScrolling() {
        val state = state()
        state.zoomTo(2.0, 0.0, 0.0)
        state.scrollBy(30.0, 60.0)

        // The page pixel of (50, 100) points is (100, 200); the viewport starts at pixel (30, 60).
        assertEquals(ScreenRect(70.0, 140.0, 40.0, 20.0), state.toScreen(Box(50.0, 100.0, 20.0, 10.0)))
    }

    @Test
    fun theTransformFollowsEveryScaleAndPosition() {
        val state = state()
        val box = Box(50.0, 100.0, 20.0, 10.0)
        for (scale in listOf(0.5, 1.0, 1.7, 3.0, 8.0)) {
            state.zoomTo(scale, 0.0, 0.0)
            for (offset in listOf(0.0 to 0.0, 25.0 to 40.0, 1000.0 to 1000.0)) {
                state.scrollBy(-10_000.0, -10_000.0)
                state.scrollBy(offset.first, offset.second)
                val rect = state.toScreen(box)
                assertEquals(state.originX + box.x * state.scale, rect.x, 1e-9, "x at $scale, $offset")
                assertEquals(state.originY + box.y * state.scale, rect.y, 1e-9, "y at $scale, $offset")
                assertEquals(box.width * state.scale, rect.width, 1e-9)
                assertEquals(box.height * state.scale, rect.height, 1e-9)
            }
        }
    }

    @Test
    fun aPageSmallerThanTheViewportShiftsTheBoxesWithItsCentring() {
        val state = ViewerState()
        state.setViewport(400, 400)
        state.setPage(PageSize(100.0, 100.0), null)
        state.zoomTo(1.0)

        // The page is centred: it starts at 150, 150.
        assertEquals(ScreenRect(160.0, 170.0, 20.0, 10.0), state.toScreen(Box(10.0, 20.0, 20.0, 10.0)))
    }

    @Test
    fun onlyBoxesInTheViewportAreKept() {
        val state = state()
        state.zoomTo(1.0, 0.0, 0.0) // 200 x 300 pixels in 150 x 150
        val inside = Box(10.0, 10.0, 30.0, 10.0)
        val partly = Box(140.0, 140.0, 30.0, 30.0) // reaches over the right and bottom edge
        val right = Box(160.0, 10.0, 20.0, 10.0)
        val below = Box(10.0, 200.0, 20.0, 10.0)

        val visible = state.visibleBoxes(listOf(inside, partly, right, below))

        assertEquals(listOf(state.toScreen(inside), state.toScreen(partly)), visible)
        state.scrollBy(0.0, 100.0)
        assertEquals(listOf(state.toScreen(below)), state.visibleBoxes(listOf(inside, right, below)))
        assertFalse(state.visibleBoxes(listOf(inside)).isNotEmpty(), "scrolled out of view")
    }

    @Test
    fun theOverlayIsOffAtTheStartAndNotPartOfTheSavedView() {
        val state = state()
        assertFalse(state.showWordBoxes)
        val before = state.saved()

        state.showWordBoxes = true

        assertEquals(before, state.saved(), "switching the overlay on changes nothing that is remembered")
    }

    /**
     * The box of a word on the page lies on the picture of that word: a red rectangle is drawn on a generated page, its
     * box is computed as the import would (with `PageGeometry`), the picture is rendered for what is visible, and the
     * pixel at the middle of the overlay's rectangle is red, the one just beside it is not.
     */
    @Test
    fun theOverlayRectangleLiesOnThePrintedShapeInThePicture() {
        val dir = Files.createTempDirectory("wordboxes")
        try {
            val pdf = dir.resolve("a.pdf")
            TestPdfs.write(pdf, listOf(TestPage(200f, 300f, rects = listOf(TestRect(60f, 110f, 20f, 20f, Color.RED)))))
            val doc = PdfDocument.open(pdf)
            val page = doc.page(0)
            try {
                val box = PageGeometry.of(page).toFormat(PdfRect(60.0, 110.0, 80.0, 130.0))
                for (scale in listOf(1.0, 2.0, 3.5)) checkOverlayOnPicture(page, box, scale)
            } finally {
                doc.close() // closes the page too
            }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    /** The overlay's rectangle for [box] at [scale] is on the red shape in the picture rendered for what is visible. */
    private fun checkOverlayOnPicture(
        page: PdfPage,
        box: Box,
        scale: Double,
    ) {
        val state = state()
        state.zoomTo(scale, 0.0, 0.0)
        state.scrollBy(box.x * scale - 40, box.y * scale - 40) // the box is in view, not at the corner
        val request = checkNotNull(state.nextRequest(null))
        val picture = page.renderRegion(request.scale, request.region)
        val rect = state.toScreen(box)

        // A screen pixel is a pixel of the picture, which starts at the page pixel (region.x, region.y).
        fun colourAt(
            x: Double,
            y: Double,
        ) = picture.getRGB(
            (x - state.originX - request.region.x).toInt(),
            (y - state.originY - request.region.y).toInt(),
        ) and
            0xFFFFFF

        assertEquals(0xFF0000, colourAt(rect.x + rect.width / 2, rect.y + rect.height / 2), "centre at scale $scale")
        assertEquals(0xFFFFFF, colourAt(rect.x - 3, rect.y + rect.height / 2), "just left of it at scale $scale")
    }
}
