package korrektor.pdf

import org.apache.pdfbox.cos.COSDictionary
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.common.PDStream
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.font.encoding.WinAnsiEncoding
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Path

/** A piece of text on a generated page, at a position in PDF coordinates (origin bottom-left). */
class TestText(
    val text: String,
    val x: Float,
    val y: Float,
    val size: Float = 12f,
    /** Drawn in render mode 3 (invisible), as the text layer of an OCR PDF. */
    val invisible: Boolean = false,
    /** If set, these single-byte codes are shown instead of [text]; the page's `toUnicode` maps them. */
    val codes: List<Int>? = null,
)

/** A filled rectangle on a generated page, in PDF coordinates (origin bottom-left), with an RGB colour. */
class TestRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val color: Color,
)

/**
 * A picture on a generated page, like a scan: [pixels] x [pixels] image pixels in a black and white checkerboard of
 * single pixels, drawn on the rectangle at ([x], [y]) of [width] x [height] points (PDF coordinates).
 */
class TestPicture(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val pixels: Int,
)

/**
 * Where a generated page's boxes are. [originX] and [originY] are the lower-left corner of the media box (text
 * positions are in the page's own coordinates, so they include it). [cropBox] is left, bottom, right, top in the
 * same coordinates, if the visible area is smaller than the media box.
 */
class TestArea(
    val originX: Float = 0f,
    val originY: Float = 0f,
    val cropBox: List<Float>? = null,
)

/**
 * A generated page. [toUnicode] maps single-byte codes to the text PDF readers extract for them.
 * A test fixture: every property is an independent option of the page, so the list is long on purpose.
 */
@Suppress("LongParameterList")
class TestPage(
    val width: Float = 200f,
    val height: Float = 300f,
    val rotation: Int = 0,
    val texts: List<TestText> = emptyList(),
    val toUnicode: Map<Int, String> = emptyMap(),
    val area: TestArea = TestArea(),
    val rects: List<TestRect> = emptyList(),
    val pictures: List<TestPicture> = emptyList(),
)

/** Small PDFs for tests, made with PDFBox. */
object TestPdfs {
    /**
     * A book of [count] pages of 300 x 480 points with two lines of text each, for tests of size. If [blankEvery] is
     * positive, the first page and every [blankEvery]th after it have no text. With [scanPixels] every page also has
     * the same picture of that many pixels across it, like a scan: JPEG compressed, drawn on the whole page, stored
     * once in the file. Made fresh in a temp folder by the tests that need it, never stored.
     */
    fun book(
        path: Path,
        count: Int,
        blankEvery: Int = 0,
        scanPixels: Pair<Int, Int>? = null,
    ) {
        PDDocument().use { doc ->
            val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
            val scan = scanPixels?.let { (w, h) -> JPEGFactory.createFromImage(doc, scanImage(w, h)) }
            repeat(count) { i ->
                val page = PDPage(PDRectangle(300f, 480f))
                doc.addPage(page)
                PDPageContentStream(doc, page).use { content ->
                    scan?.let { content.drawImage(it, 0f, 0f, 300f, 480f) }
                    if (blankEvery <= 0 || i % blankEvery != 0) {
                        content.beginText()
                        content.setFont(font, 11f)
                        content.newLineAtOffset(40f, 400f)
                        content.showText("Page ${i + 1} of the long book, first line")
                        content.newLineAtOffset(0f, -14f)
                        content.showText("second line with some more words on it")
                        content.endText()
                    }
                }
            }
            doc.save(path.toFile())
        }
    }

    /** Paper with many small dark marks, so that the JPEG has something to decode. */
    private fun scanImage(
        width: Int,
        height: Int,
    ): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val random = java.util.Random(SCAN_SEED)
        image.createGraphics().apply {
            color = Color(250, 245, 230)
            fillRect(0, 0, width, height)
            color = Color(30, 25, 20)
            repeat(SCAN_MARKS) {
                fillRect(
                    random.nextInt(width),
                    random.nextInt(height),
                    random.nextInt(MAX_MARK) + 1,
                    random.nextInt(MAX_MARK) + 1,
                )
            }
            dispose()
        }
        return image
    }

    private const val SCAN_SEED = 7L
    private const val SCAN_MARKS = 20_000
    private const val MAX_MARK = 12

    /** Writes a PDF with [pages]. With [userPassword] it is encrypted and needs that password to open. */
    fun write(
        path: Path,
        pages: List<TestPage>,
        userPassword: String? = null,
        /** Indices of pages that get a picture and no text, like a scan without OCR. */
        picturePages: Set<Int> = emptySet(),
        /** Indices of pages whose content is cut off in the middle of an operator. */
        brokenPages: Set<Int> = emptySet(),
    ) {
        PDDocument().use { doc ->
            for ((index, spec) in pages.withIndex()) {
                val page = PDPage(PDRectangle(spec.area.originX, spec.area.originY, spec.width, spec.height))
                spec.area.cropBox?.let { page.cropBox = PDRectangle(it[0], it[1], it[2] - it[0], it[3] - it[1]) }
                page.rotation = spec.rotation
                doc.addPage(page)
                draw(doc, page, spec)
                if (index in picturePages) drawPicture(doc, page)
                if (index in brokenPages) breakContent(doc, page)
            }
            if (userPassword != null) {
                val policy = StandardProtectionPolicy("owner-secret", userPassword, AccessPermission())
                policy.encryptionKeyLength = 128
                doc.protect(policy)
            }
            doc.save(path.toFile())
        }
    }

    /** Black and white pixels like a chessboard with one pixel per square; the pixel (0, 0) is black. */
    private fun checkerboard(
        doc: PDDocument,
        pixels: Int,
    ) = LosslessFactory.createFromImage(
        doc,
        BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_RGB).also { image ->
            for (y in 0 until pixels) {
                for (x in 0 until pixels) {
                    image.setRGB(
                        x,
                        y,
                        if ((x + y) % 2 ==
                            0
                        ) {
                            0x000000
                        } else {
                            0xFFFFFF
                        },
                    )
                }
            }
        },
    )

    /** A grey square, as the scan of a page would be. */
    private fun drawPicture(
        doc: PDDocument,
        page: PDPage,
    ) {
        val image = BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            color = Color.GRAY
            fillRect(0, 0, 40, 40)
            dispose()
        }
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true).use {
            it.drawImage(LosslessFactory.createFromImage(doc, image), 20f, 20f, 100f, 100f)
        }
    }

    /** Replaces the content of the page by something that stops half way: text, then an operator without its end. */
    private fun breakContent(
        doc: PDDocument,
        page: PDPage,
    ) {
        val stream = PDStream(doc)
        stream.createOutputStream(COSName.FLATE_DECODE).use {
            it.write(
                "BT /F1 12 Tf 20 100 Td (Broken) Tj ET q 1 0 0 1 10 [ (unterminated << /X".toByteArray(
                    Charsets.US_ASCII,
                ),
            )
        }
        page.setContents(stream)
    }

    private fun draw(
        doc: PDDocument,
        page: PDPage,
        spec: TestPage,
    ) {
        val plain: PDFont = PDType1Font(Standard14Fonts.FontName.HELVETICA)
        val exotic = if (spec.toUnicode.isEmpty()) null else fontWithToUnicode(doc, spec.toUnicode)
        PDPageContentStream(doc, page).use { content ->
            for (picture in spec.pictures) {
                content.drawImage(
                    checkerboard(doc, picture.pixels),
                    picture.x,
                    picture.y,
                    picture.width,
                    picture.height,
                )
            }
            for (rect in spec.rects) {
                content.setNonStrokingColor(rect.color)
                content.addRect(rect.x, rect.y, rect.width, rect.height)
                content.fill()
            }
            for (text in spec.texts) {
                content.beginText()
                content.setFont(if (text.codes != null) exotic ?: plain else plain, text.size)
                content.setRenderingMode(if (text.invisible) RenderingMode.NEITHER else RenderingMode.FILL)
                content.newLineAtOffset(text.x, text.y)
                if (text.codes != null) {
                    content.appendRawCommands("<" + text.codes.joinToString("") { "%02X".format(it) } + "> Tj\n")
                } else {
                    content.showText(text.text)
                }
                content.endText()
            }
        }
    }

    /** Helvetica with a ToUnicode map, so single-byte codes can stand for any text, like `ſ` or an emoji. */
    private fun fontWithToUnicode(
        doc: PDDocument,
        mapping: Map<Int, String>,
    ): PDFont {
        val entries =
            mapping.entries.joinToString("\n") { (code, text) ->
                val utf16 = text.map { "%04X".format(it.code) }.joinToString("")
                "<%02X> <%s>".format(code, utf16)
            }
        val cmap =
            """
            /CIDInit /ProcSet findresource begin
            12 dict begin
            begincmap
            /CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def
            /CMapName /Adobe-Identity-UCS def
            /CMapType 2 def
            1 begincodespacerange
            <00> <FF>
            endcodespacerange
            ${mapping.size} beginbfchar
            $entries
            endbfchar
            endcmap
            CMapName currentdict /CMap defineresource pop
            end
            end
            """.trimIndent()
        val stream = PDStream(doc)
        stream.createOutputStream(COSName.FLATE_DECODE).use { it.write(cmap.toByteArray(Charsets.US_ASCII)) }
        val dictionary = COSDictionary()
        dictionary.setItem(COSName.TYPE, COSName.FONT)
        dictionary.setItem(COSName.SUBTYPE, COSName.TYPE1)
        dictionary.setName(COSName.BASE_FONT, "Helvetica")
        dictionary.setItem(COSName.ENCODING, COSName.WIN_ANSI_ENCODING)
        dictionary.setItem(COSName.TO_UNICODE, stream)
        check(WinAnsiEncoding.INSTANCE != null)
        return PDType1Font(dictionary)
    }
}
