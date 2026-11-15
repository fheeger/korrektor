package korrektor.viewer

import korrektor.pdf.PixelRegion
import korrektor.pdf.TestPage
import korrektor.pdf.TestPdfs
import korrektor.pdf.TestPicture
import korrektor.pdf.TestRect
import kotlinx.coroutines.runBlocking
import java.awt.Color
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PdfPageSourceTest {
    private fun withSource(
        pages: List<TestPage>,
        block: (PdfPageSource) -> Unit,
    ) {
        val dir = Files.createTempDirectory("pagesource")
        try {
            val pdf = dir.resolve("a.pdf")
            TestPdfs.write(pdf, pages)
            val source = PdfPageSource(pdf)
            try {
                block(source)
            } finally {
                source.close()
            }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun givesTheSizeOfEveryPageAndPicturesOfTheAskedSize() =
        withSource(
            listOf(
                TestPage(200f, 300f, rects = listOf(TestRect(0f, 0f, 100f, 100f, Color.RED))),
                TestPage(150f, 100f),
            ),
        ) { source ->
            runBlocking {
                assertEquals(PageSize(200.0, 300.0), source.size(1))
                assertEquals(PageSize(150.0, 100.0), source.size(2))
                val picture = source.render(1, 2.0, PixelRegion(0, 0, 400, 600))
                assertEquals(400 to 600, picture.width to picture.height)
                val part = source.render(2, 1.0, PixelRegion(50, 20, 60, 40))
                assertEquals(60 to 40, part.width to part.height)
            }
        }

    @Test
    fun theNativeScaleIsThePixelsOfTheScanPerPointAndMissingWithoutAScan() =
        withSource(
            listOf(
                TestPage(200f, 300f, pictures = listOf(TestPicture(20f, 20f, 100f, 100f, 400))),
                TestPage(200f, 300f),
            ),
        ) { source ->
            runBlocking {
                assertEquals(4.0, source.nativeScale(1)!!, 1e-6)
                assertNull(source.nativeScale(2))
            }
        }

    @Test
    fun aPageThatDoesNotExistIsAnError() =
        withSource(listOf(TestPage())) { source ->
            runBlocking { assertFailsWith<IllegalArgumentException> { source.size(2) } }
        }

    @Test
    fun closingWithoutEverOpeningThePdfIsFine() = withSource(listOf(TestPage())) { /* closed by withSource */ }
}
