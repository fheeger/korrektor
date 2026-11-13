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
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode
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

/** A generated page. [toUnicode] maps single-byte codes to the text PDF readers extract for them. */
class TestPage(
    val width: Float = 200f,
    val height: Float = 300f,
    val rotation: Int = 0,
    val texts: List<TestText> = emptyList(),
    val toUnicode: Map<Int, String> = emptyMap(),
)

/** Small PDFs for tests, made with PDFBox. */
object TestPdfs {
    /** Writes a PDF with [pages]. With [userPassword] it is encrypted and needs that password to open. */
    fun write(
        path: Path,
        pages: List<TestPage>,
        userPassword: String? = null,
    ) {
        PDDocument().use { doc ->
            for (spec in pages) {
                val page = PDPage(PDRectangle(spec.width, spec.height))
                page.rotation = spec.rotation
                doc.addPage(page)
                draw(doc, page, spec)
            }
            if (userPassword != null) {
                val policy = StandardProtectionPolicy("owner-secret", userPassword, AccessPermission())
                policy.encryptionKeyLength = 128
                doc.protect(policy)
            }
            doc.save(path.toFile())
        }
    }

    private fun draw(
        doc: PDDocument,
        page: PDPage,
        spec: TestPage,
    ) {
        val plain: PDFont = PDType1Font(Standard14Fonts.FontName.HELVETICA)
        val exotic = if (spec.toUnicode.isEmpty()) null else fontWithToUnicode(doc, spec.toUnicode)
        PDPageContentStream(doc, page).use { content ->
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
