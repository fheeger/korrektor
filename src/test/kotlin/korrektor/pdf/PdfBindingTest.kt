package korrektor.pdf

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PdfBindingTest {
    private val samplePdf = Path.of("samples", "Abhandlung_über_den_kleinen_Krieg.pdf")

    private fun withDir(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("pdfbinding")
        try {
            block(dir)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    private fun PdfTextPage.string(): String {
        val sb = StringBuilder()
        for (i in 0 until charCount) {
            val c = char(i)
            if (!c.generated) sb.appendCodePoint(c.unicode) // a surrogate half becomes one UTF-16 unit
        }
        return sb.toString()
    }

    // --- opening and sizes ----------------------------------------------------------------------------

    @Test
    fun aGeneratedPdfOpensWithItsPagesAndSizes() =
        withDir { dir ->
            val file = dir.resolve("a.pdf")
            TestPdfs.write(
                file,
                listOf(TestPage(200f, 300f), TestPage(150f, 100f), TestPage(200f, 300f, rotation = 90)),
            )

            PdfDocument.open(file).use { doc ->
                assertEquals(3, doc.pageCount)
                doc.page(0).use {
                    assertEquals(200.0, it.width, 0.01)
                    assertEquals(300.0, it.height, 0.01)
                    assertEquals(0, it.rotation)
                    assertEquals(PdfRect(0.0, 0.0, 200.0, 300.0), it.bounds)
                }
                doc.page(1).use {
                    assertEquals(150.0, it.width, 0.01)
                    assertEquals(100.0, it.height, 0.01)
                }
                doc.page(2).use {
                    // Rotated by 90 degrees: shown 300 wide and 200 high; the bounds stay in the unrotated page.
                    assertEquals(300.0, it.width, 0.01)
                    assertEquals(200.0, it.height, 0.01)
                    assertEquals(90, it.rotation)
                    assertEquals(PdfRect(0.0, 0.0, 200.0, 300.0), it.bounds)
                }
            }
        }

    @Test
    fun aPathWithUmlautsAndSpacesOpens() =
        withDir { dir ->
            val file = dir.resolve("Über den Krieg.pdf")
            TestPdfs.write(file, listOf(TestPage()))
            PdfDocument.open(file).use { assertEquals(1, it.pageCount) }
        }

    @Test
    fun aPageThatDoesNotExistIsRefused() =
        withDir { dir ->
            val file = dir.resolve("a.pdf")
            TestPdfs.write(file, listOf(TestPage()))
            PdfDocument.open(file).use { doc ->
                assertFailsWith<IllegalArgumentException> { doc.page(1) }
                assertFailsWith<IllegalArgumentException> { doc.page(-1) }
            }
        }

    // --- text -----------------------------------------------------------------------------------------

    @Test
    fun theCharactersOfKnownTextComeBackInOrderWithBoxes() =
        withDir { dir ->
            val file = dir.resolve("a.pdf")
            TestPdfs.write(file, listOf(TestPage(texts = listOf(TestText("Hello world", 50f, 200f, 12f)))))

            PdfDocument.open(file).use { doc ->
                val text = doc.page(0).text()
                assertEquals("Hello world", text.string())

                val h = text.char(0)
                assertEquals('H'.code, h.unicode)
                assertEquals(50.0, h.box.left, 1.0, "left edge of the H")
                assertTrue(
                    h.box.bottom <= 201.0 && h.box.top >= 207.0,
                    "the H spans the baseline up to its height: ${h.box}",
                )
                assertEquals(12.0, h.fontSize, 0.01)
                assertTrue(!h.generated)

                val w = text.char(6)
                assertEquals('w'.code, w.unicode)
                assertTrue(w.box.left > text.char(5).box.left, "boxes run from left to right")
                assertTrue(
                    text.char(10).box.right in 85.0..110.0,
                    "the text is about 60 points wide: ${text.char(10).box}",
                )
            }
        }

    @Test
    fun specialCharactersComeBackAsTheRightCodePoints() =
        withDir { dir ->
            val file = dir.resolve("a.pdf")
            // Codes 0x80.. stand for: long s, u, combining small e above, a character outside the BMP.
            val mapping = mapOf(0x80 to "ſ", 0x81 to "u", 0x82 to "ͤ", 0x83 to "😀")
            val page =
                TestPage(
                    texts = listOf(TestText("", 20f, 100f, 14f, codes = listOf(0x80, 0x81, 0x82, 0x83))),
                    toUnicode = mapping,
                )
            TestPdfs.write(file, listOf(page))

            PdfDocument.open(file).use { doc ->
                assertEquals("ſuͤ😀", doc.page(0).text().string())
            }
        }

    @Test
    fun invisibleTextIsReadLikeVisibleText() =
        withDir { dir ->
            val file = dir.resolve("a.pdf")
            TestPdfs.write(file, listOf(TestPage(texts = listOf(TestText("Hidden", 30f, 150f, 10f, invisible = true)))))
            PdfDocument.open(file).use { assertEquals("Hidden", it.page(0).text().string()) }
        }

    @Test
    fun aPageWithoutTextHasNoCharacters() =
        withDir { dir ->
            val file = dir.resolve("a.pdf")
            TestPdfs.write(file, listOf(TestPage()))
            PdfDocument.open(file).use { assertEquals(0, it.page(0).text().charCount) }
        }

    // --- errors ---------------------------------------------------------------------------------------

    @Test
    fun aMissingFileIsAnErrorThatSaysSo() =
        withDir { dir ->
            val e = assertFailsWith<PdfException> { PdfDocument.open(dir.resolve("missing.pdf")) }
            assertTrue("does not exist" in e.message!!, e.message)
        }

    @Test
    fun aTextFileIsNotAPdf() =
        withDir { dir ->
            val file = dir.resolve("notes.pdf").also { it.writeText("just some text, not a PDF") }
            val e = assertFailsWith<PdfException> { PdfDocument.open(file) }
            assertTrue("not a PDF file" in e.message!!, e.message)
        }

    @Test
    fun aDamagedPdfIsReportedAsDamaged() =
        withDir { dir ->
            val file =
                dir
                    .resolve(
                        "broken.pdf",
                    ).also { it.writeText("%PDF-1.4\nthis is not a real PDF structure at all") }
            val e = assertFailsWith<PdfException> { PdfDocument.open(file) }
            assertTrue("damaged" in e.message!!, e.message)
        }

    @Test
    fun aPasswordProtectedPdfNeedsItsPassword() =
        withDir { dir ->
            val file = dir.resolve("secret.pdf")
            TestPdfs.write(
                file,
                listOf(TestPage(texts = listOf(TestText("Secret", 20f, 100f)))),
                userPassword = "open-sesame",
            )

            val e = assertFailsWith<PdfException> { PdfDocument.open(file) }
            assertTrue("needs a password" in e.message!!, e.message)
            assertFailsWith<PdfException> { PdfDocument.open(file, "wrong") }
            PdfDocument.open(file, "open-sesame").use { assertEquals("Secret", it.page(0).text().string()) }
        }

    // --- closing --------------------------------------------------------------------------------------

    @Test
    fun closingIsSafeAndUsingAClosedObjectFailsClearly() =
        withDir { dir ->
            val file = dir.resolve("a.pdf")
            TestPdfs.write(file, listOf(TestPage(texts = listOf(TestText("Text", 20f, 100f)))))

            val doc = PdfDocument.open(file)
            val page = doc.page(0)
            val text = page.text()
            text.close()
            text.close()
            assertFailsWith<IllegalStateException> { text.char(0) }

            doc.close() // closes the page too
            doc.close()
            page.close()
            assertFailsWith<IllegalStateException> { page.text() }
            assertFailsWith<IllegalStateException> { page.render(50) }
            assertFailsWith<IllegalStateException> { doc.page(0) }
        }

    @Test
    fun openingAndClosingManyTimesWorks() =
        withDir { dir ->
            val file = dir.resolve("a.pdf")
            TestPdfs.write(file, listOf(TestPage(texts = listOf(TestText("Loop", 20f, 100f)))))
            repeat(300) {
                PdfDocument.open(file).use { doc -> doc.page(0).use { it.text().charCount } }
            }
        }

    @Test
    fun severalThreadsAtOnceDoNotCrashIt() =
        withDir { dir ->
            val file = dir.resolve("a.pdf")
            TestPdfs.write(file, listOf(TestPage(texts = listOf(TestText("Concurrent", 20f, 100f)))))
            val pool = Executors.newFixedThreadPool(8)
            try {
                val results =
                    (1..8).map {
                        pool.submit<Boolean> {
                            repeat(40) {
                                PdfDocument.open(file).use { doc ->
                                    assertEquals("Concurrent", doc.page(0).text().string())
                                }
                            }
                            true
                        }
                    }
                assertTrue(results.all { it.get(60, TimeUnit.SECONDS) })
            } finally {
                pool.shutdownNow()
            }
        }

    // --- rendering ------------------------------------------------------------------------------------

    @Test
    fun aPageRendersAtTheRequestedWidthWithBlackTextOnWhite() =
        withDir { dir ->
            val file = dir.resolve("a.pdf")
            TestPdfs.write(file, listOf(TestPage(200f, 300f, texts = listOf(TestText("MMMM", 20f, 140f, 60f)))))

            PdfDocument.open(file).use { doc ->
                val image = doc.page(0).render(100)
                assertEquals(100, image.width)
                assertEquals(150, image.height, "the proportions of the page are kept")
                assertEquals(0xFFFFFF, image.getRGB(1, 1) and 0xFFFFFF, "the margin is white")
                val dark =
                    (0 until image.height).sumOf { y ->
                        (0 until image.width).count { x ->
                            brightness(image.getRGB(x, y)) <
                                100
                        }
                    }
                assertTrue(dark > 100, "black text is visible: $dark dark pixels")
                assertTrue(dark < image.width * image.height / 2, "most of the page is white")
            }
        }

    private fun brightness(rgb: Int) = ((rgb shr 16 and 0xFF) + (rgb shr 8 and 0xFF) + (rgb and 0xFF)) / 3

    // --- the sample -----------------------------------------------------------------------------------

    @Test
    fun theSamplePdfHas165Pages() {
        assumeTrue(samplePdf.exists(), "the sample PDF is not available")
        PdfDocument.open(samplePdf).use { doc ->
            assertEquals(165, doc.pageCount)
            doc.page(5).use { page ->
                assertTrue(
                    abs(page.width - 305.52) < 0.1 && abs(page.height - 489.6) < 0.1,
                    "page 6 is 305.52 x 489.6: ${page.width} x ${page.height}",
                )
                assertTrue(page.text().charCount > 500, "page 6 has a text layer")
            }
        }
    }
}
