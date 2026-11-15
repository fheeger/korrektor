package korrektor.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import korrektor.format.Metadata
import korrektor.format.Project
import korrektor.importer.PdfImport
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HistoricFontsTest {
    private val abhandlung = Path.of("samples", "Abhandlung_über_den_kleinen_Krieg.pdf")
    private val clausewitz = Path.of("samples", "Clausewitz_Strategie.pdf")

    private fun String.codePointList() = codePoints().toArray().toList()

    private fun hex(codePoint: Int) = "U+%04X".format(codePoint)

    // --- coverage -----------------------------------------------------------------------------------------

    @Test
    fun everyCharacterThatOldGermanPrintNeedsHasAGlyph() {
        val required =
            "ſꝛßäöüÄÖÜůåéáàóíúęÿœæ" +
                "ͤͣͥͦͧ" + // combining small e, a, i, o, u
                "=-–—­„“‚‘»«·•" +
                "0123456789.,;:!?()[]/&*+†" +
                "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ" +
                "ꝛꝑꝓꝗ" + // ꝛ and the abbreviation letters
                "ſtﬅﬆ" // the long s ligatures
        val missing = required.codePointList().filterNot { HistoricFonts.hasGlyph(it) }
        assertEquals(emptyList(), missing.map(::hex), "characters without a glyph")
    }

    private fun charactersOfTheText(pdf: Path): Set<Int> {
        val dir = Files.createTempDirectory("fontcoverage")
        try {
            val project = Project.create(dir.resolve("b.korrektur"), pdf, emptyList(), Metadata("B"))
            PdfImport.run(project)
            return (1..project.pages.size)
                .flatMap { project.readOriginalPage(it).regions }
                .flatMap { it.lines }
                .flatMap { it.text.codePointList() }
                .toSet()
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    private fun Int.isLatinPrint(): Boolean =
        Character.UnicodeScript.of(this) == Character.UnicodeScript.LATIN ||
            Character.UnicodeScript.of(this) == Character.UnicodeScript.INHERITED &&
            this < 0x0370 ||
            this < 0x0100 ||
            this in 0x2000..0x206F

    private fun checkSample(
        pdf: Path,
        name: String,
    ) {
        val characters = charactersOfTheText(pdf)
        val lacking = characters.filterNot { HistoricFonts.hasGlyph(it) }.sorted()
        // What the texts need: Latin letters, combining marks and the punctuation and digits of Latin print. A symbol
        // like the Devanagari danda or a white square is OCR noise, like other scripts, and comes from a system font.
        val needed = lacking.filter { it.isLatinPrint() }
        println(
            "$name: ${characters.size} distinct characters, ${lacking.size} without a glyph in Junicode: " +
                lacking.joinToString(" ") { hex(it) + "=" + String(Character.toChars(it)) },
        )
        assertEquals(
            emptyList(),
            needed.map(::hex),
            "$name: Latin letters, digits, punctuation or combining marks without a glyph",
        )
    }

    @Test
    fun everyLatinCharacterOfTheAbhandlungHasAGlyph() {
        assumeTrue(abhandlung.exists(), "the sample PDF is not available")
        checkSample(abhandlung, "Abhandlung")
    }

    @Test
    fun everyLatinCharacterOfTheClausewitzHasAGlyph() {
        assumeTrue(clausewitz.exists(), "the second sample PDF is not available")
        checkSample(clausewitz, "Clausewitz")
    }

    // --- rendering ----------------------------------------------------------------------------------------

    /** The picture of [text] in the panel's font, black on white, 120 x 60 pixels. */
    private fun render(text: String): BufferedImage {
        lateinit var image: BufferedImage
        runComposeUiTest {
            setContent {
                Box(Modifier.size(120.dp, 60.dp).background(Color.White)) {
                    Text(text, color = Color.Black, fontSize = 40.sp, fontFamily = HistoricFonts.family)
                }
            }
            image = onRoot().captureToImage().toAwtImage()
        }
        return image
    }

    private fun BufferedImage.inkRows(): IntRange? {
        val rows = (0 until height).filter { y -> (0 until width).any { x -> (getRGB(x, y) and 0xFF) < 128 } }
        return if (rows.isEmpty()) null else rows.first()..rows.last()
    }

    private fun BufferedImage.inkCount() =
        (0 until height).sumOf { y ->
            (0 until width).count { x ->
                (
                    getRGB(x, y) and
                        0xFF
                ) <
                    128
            }
        }

    @Test
    fun theSpecialCharactersAreDrawnAsInk() {
        for (text in listOf("ſ", "ꝛ", "ß", "uͤ")) {
            assertTrue(render(text).inkCount() > 50, "'$text' has ink")
        }
    }

    @Test
    fun theCombiningSmallELiesOverTheUAndTheLongSDiffersFromTheShortS() {
        val u = render("u").inkRows()!!
        val ue = render("uͤ").inkRows()!!
        assertTrue(ue.first < u.first, "the e over the u reaches higher than the u: $ue against $u")
        assertTrue(render("ſ").inkCount() != render("s").inkCount(), "the long s is not drawn as a short s")
    }
}
