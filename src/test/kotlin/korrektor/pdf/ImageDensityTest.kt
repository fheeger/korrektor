package korrektor.pdf

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageDensityTest {
    private val sample = Path.of("samples", "Abhandlung_über_den_kleinen_Krieg.pdf")

    private fun withPage(
        spec: TestPage,
        block: (PdfPage) -> Unit,
    ) {
        val dir = Files.createTempDirectory("density")
        try {
            val file = dir.resolve("a.pdf")
            TestPdfs.write(file, listOf(spec))
            PdfDocument.open(file).use { doc -> doc.page(0).use(block) }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    private fun rgb(
        image: java.awt.image.BufferedImage,
        x: Int,
        y: Int,
    ) = image.getRGB(x, y) and 0xFFFFFF

    @Test
    fun aPageWithoutAPictureHasNoDensity() = withPage(TestPage()) { assertNull(it.largestImageDensity()) }

    @Test
    fun theDensityIsThePixelsPerPointOfThePicture() =
        withPage(TestPage(200f, 300f, pictures = listOf(TestPicture(20f, 20f, 100f, 100f, 400)))) { page ->
            assertEquals(4.0, assertNotNull(page.largestImageDensity()), 1e-6)
        }

    @Test
    fun theLargestPictureCounts() =
        withPage(
            TestPage(
                200f,
                300f,
                pictures = listOf(TestPicture(0f, 0f, 50f, 50f, 100), TestPicture(60f, 60f, 100f, 100f, 300)),
            ),
        ) { page ->
            assertEquals(3.0, assertNotNull(page.largestImageDensity()), 1e-6)
        }

    @Test
    fun aRotatedPageHasTheSameDensity() =
        withPage(
            TestPage(200f, 300f, rotation = 90, pictures = listOf(TestPicture(20f, 20f, 100f, 100f, 400))),
        ) { page ->
            assertEquals(4.0, assertNotNull(page.largestImageDensity()), 1e-6)
        }

    @Test
    fun atTheDensityEveryPixelOfThePictureIsOnePixelOfTheScreen() =
        withPage(TestPage(200f, 300f, pictures = listOf(TestPicture(20f, 20f, 100f, 100f, 400)))) { page ->
            val density = assertNotNull(page.largestImageDensity())

            // The picture's top-left corner is at x = 20 points and y = 300 - 120 = 180 points from the top.
            val image = page.renderRegion(density, PixelRegion(80, 720, 40, 40))

            for (y in 0 until 40) {
                for (x in 0 until 40) {
                    val expected = if ((x + y) % 2 == 0) 0x000000 else 0xFFFFFF
                    assertEquals(expected, rgb(image, x, y), "pixel $x,$y is one pixel of the picture")
                }
            }
        }

    @Test
    fun theSampleScanHasMorePixelsThanTheScreenAtActualSize() {
        assumeTrue(sample.exists(), "the sample PDF is not available")
        PdfDocument.open(sample).use { doc ->
            doc.page(5).use { page ->
                val density = assertNotNull(page.largestImageDensity())
                assertTrue(density > 96.0 / 72.0, "the scan is finer than the screen at actual size: $density")
            }
        }
    }
}
