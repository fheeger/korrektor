package korrektor.editor

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import java.awt.Font as AwtFont

/**
 * The font of the text panel: Junicode (SIL Open Font License, see `docs/third-party.md`), which is made for
 * historical texts and has `ſ`, `ꝛ`, the combining small letters like the `e` over `uͤ`, and the other characters of
 * old German print. The default fonts of the three systems differ in what they cover, so the app brings its own. A
 * character it does not have (OCR noise from other scripts) is drawn from the system's fonts by the text engine.
 */
object HistoricFonts {
    private const val RESOURCE = "/fonts/Junicode-Regular.ttf"

    private val data: ByteArray by lazy {
        checkNotNull(HistoricFonts::class.java.getResourceAsStream(RESOURCE)) { "The font $RESOURCE is missing" }
            .use { it.readBytes() }
    }

    /** The font family for the text panel. */
    val family: FontFamily by lazy {
        FontFamily(Font("Junicode-Regular", data, FontWeight.Normal, FontStyle.Normal))
    }

    private val awtFont: AwtFont by lazy { AwtFont.createFont(AwtFont.TRUETYPE_FONT, data.inputStream()) }

    /** Whether the font itself has a glyph for [codePoint] (not a stand-in from another font). */
    fun hasGlyph(codePoint: Int): Boolean = awtFont.canDisplay(codePoint)
}
