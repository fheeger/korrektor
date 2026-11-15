package korrektor.pdf

import korrektor.importer.PageGeometry
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RenderRegionTest {
    private val red = Color(255, 0, 0)
    private val blue = Color(0, 0, 255)

    private fun withPage(
        spec: TestPage,
        block: (PdfPage) -> Unit,
    ) {
        val dir = Files.createTempDirectory("renderregion")
        try {
            val file: Path = dir.resolve("a.pdf")
            TestPdfs.write(file, listOf(spec))
            PdfDocument.open(file).use { doc -> doc.page(0).use(block) }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    private fun BufferedImage.rgb(
        x: Int,
        y: Int,
    ) = getRGB(x, y) and 0xFFFFFF

    private fun same(
        a: BufferedImage,
        b: BufferedImage,
        message: String,
    ) {
        assertEquals(a.width to a.height, b.width to b.height, "$message: size")
        val differing = (0 until a.height).sumOf { y -> (0 until a.width).count { x -> a.rgb(x, y) != b.rgb(x, y) } }
        assertEquals(0, differing, "$message: pixels that differ")
    }

    /** Straight edges on whole pixels at the scales used below, so that two renderings agree exactly. */
    private val pattern =
        TestPage(
            200f,
            300f,
            rects = listOf(TestRect(20f, 200f, 60f, 40f, red), TestRect(100f, 40f, 80f, 100f, blue)),
        )

    @Test
    fun theWholePageAtScaleOneIsTheSameAsRender() =
        withPage(pattern) { page ->
            same(page.render(200), page.renderRegion(1.0, PixelRegion(0, 0, 200, 300)), "whole page")
        }

    @Test
    fun aPartIsTheSamePartOfTheWholePageAtTheSameScale() =
        withPage(pattern) { page ->
            val scale = 1.5
            val whole = page.renderRegion(scale, PixelRegion(0, 0, 300, 450))
            val part = page.renderRegion(scale, PixelRegion(30, 60, 150, 200))
            val cut = whole.getSubimage(30, 60, 150, 200)
            same(cut, part, "part")
            assertTrue((0 until 200).any { y -> (0 until 150).any { x -> part.rgb(x, y) == 0xFF0000 } }, "red is in it")
        }

    @Test
    fun aPartThatReachesBeyondThePageIsWhiteThere() =
        withPage(pattern) { page ->
            val image = page.renderRegion(1.0, PixelRegion(150, 250, 100, 100))
            assertEquals(0xFFFFFF, image.rgb(99, 99), "beyond both edges")
            assertEquals(0xFFFFFF, image.rgb(10, 80), "beyond the right edge")
        }

    @Test
    fun theColourIsWhereThePageIsDrawnAtAnotherScaleToo() =
        withPage(pattern) { page ->
            val image = page.renderRegion(0.5, PixelRegion(0, 0, 100, 150))
            // The red rectangle is x 20..80, y 200..240 from the bottom: at half size x 10..40, y 30..50 from the top.
            assertEquals(0xFF0000, image.rgb(25, 40))
            assertEquals(0xFFFFFF, image.rgb(5, 40))
            assertEquals(0x0000FF, image.rgb(70, 115))
        }

    @Test
    fun aRotatedPageIsDrawnAsTheUserSeesIt() =
        withPage(TestPage(200f, 300f, rotation = 90, rects = listOf(TestRect(0f, 0f, 40f, 40f, red)))) { page ->
            assertEquals(300.0 to 200.0, page.width to page.height)
            val image = page.renderRegion(1.0, PixelRegion(0, 0, 300, 200))
            val box = PageGeometry.of(page).toFormat(PdfRect(0.0, 0.0, 40.0, 40.0))
            val inside = image.rgb((box.x + box.width / 2).toInt(), (box.y + box.height / 2).toInt())
            assertEquals(0xFF0000, inside, "the corner is where PageGeometry puts it: $box")
            assertEquals(0xFFFFFF, image.rgb(290, 190), "and not in the opposite corner")
        }

    @Test
    fun aCropBoxShowsOnlyTheVisibleAreaWhereGeometryPutsIt() {
        val area = TestArea(cropBox = listOf(50f, 100f, 150f, 250f))
        withPage(TestPage(200f, 300f, area = area, rects = listOf(TestRect(60f, 110f, 20f, 20f, blue)))) { page ->
            assertEquals(100.0 to 150.0, page.width to page.height)
            val image = page.renderRegion(1.0, PixelRegion(0, 0, 100, 150))
            val box = PageGeometry.of(page).toFormat(PdfRect(60.0, 110.0, 80.0, 130.0))
            assertEquals(0x0000FF, image.rgb((box.x + box.width / 2).toInt(), (box.y + box.height / 2).toInt()), "$box")
        }
    }

    @Test
    fun invalidArgumentsFailWithAClearMessage() =
        withPage(pattern) { page ->
            assertTrue(
                "size" in
                    assertFailsWith<IllegalArgumentException> {
                        page.renderRegion(
                            1.0,
                            PixelRegion(0, 0, 0, 10),
                        )
                    }.message!!,
            )
            assertTrue(
                "positive" in
                    assertFailsWith<IllegalArgumentException> {
                        page.renderRegion(
                            0.0,
                            PixelRegion(0, 0, 10, 10),
                        )
                    }.message!!,
            )
            assertTrue(
                "too large" in
                    assertFailsWith<IllegalArgumentException> {
                        page.renderRegion(
                            1.0,
                            PixelRegion(0, 0, 7000, 7000),
                        )
                    }.message!!,
            )
            assertTrue(
                "possible" in
                    assertFailsWith<IllegalArgumentException> {
                        page.renderRegion(
                            100_000.0,
                            PixelRegion(0, 0, 10, 10),
                        )
                    }.message!!,
            )
        }
}
