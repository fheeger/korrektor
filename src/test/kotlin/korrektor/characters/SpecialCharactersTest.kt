package korrektor.characters

import korrektor.Shortcuts
import korrektor.editor.HistoricFonts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpecialCharactersTest {
    private fun codePoints(text: String) = text.codePoints().toArray().joinToString(" ") { "%04X".format(it) }

    @Test
    fun theTableOfTheTicketIsThere() {
        // Code points and keys as agreed in T108, so that no look-alike or precomposed form slips in.
        val expected =
            """
            017F Ctrl+Shift+S
            0061 0364 Ctrl+Shift+A
            006F 0364 Ctrl+Shift+O
            0075 0364 Ctrl+Shift+U
            0041 0364 Ctrl+K, Shift+A
            004F 0364 Ctrl+K, Shift+O
            0055 0364 Ctrl+K, Shift+U
            0075 0366 Ctrl+K, V
            A75B Ctrl+K, R
            00E6 Ctrl+Shift+E
            00C6 Ctrl+K, Shift+E
            0153 Ctrl+Shift+W
            0152 Ctrl+K, Shift+W
            00EB Ctrl+K, E
            00CB Ctrl+K, Y
            00EF Ctrl+K, I
            00CF Ctrl+K, Shift+I
            2014 Ctrl+Shift+M
            2013 Ctrl+K, N
            2E17 Ctrl+Shift+H
            201E Ctrl+K, 1
            201C Ctrl+K, 2
            201D Ctrl+K, 3
            201A Ctrl+K, 4
            2018 Ctrl+K, 5
            2019 Ctrl+K, 6
            00BB Ctrl+K, 7
            00AB Ctrl+K, 8
            203A Ctrl+K, 9
            2039 Ctrl+K, 0
            00A7 Ctrl+K, P
            2020 Ctrl+K, D
            2021 Ctrl+K, Shift+D
            00A3 Ctrl+K, L
            2114 Ctrl+K, B
            00B0 Ctrl+K, G
            00D7 Ctrl+K, X
            00BD Ctrl+K, H
            00BC Ctrl+K, Q
            00BE Ctrl+K, T
            """.trimIndent()
        assertEquals(expected, SpecialCharacters.all.joinToString("\n") { "${codePoints(it.text)} ${it.shortcut}" })
    }

    @Test
    fun noKeysAreUsedTwice() {
        val all = SpecialCharacters.all
        assertEquals(all.size, all.map { it.shortcut }.toSet().size, "two characters share a shortcut")
        assertEquals(all.size, all.map { it.name }.toSet().size, "two characters share a name")
        val taken = Shortcuts.all.flatMap { it.combos }.toSet()
        for (character in all.filterNot { it.afterLeader }) {
            assertTrue(character.combo !in taken, "${character.shortcut} is a shortcut already")
            assertTrue(character.combo != LEADER)
        }
        assertTrue(LEADER !in taken, "the leader is a shortcut already")
    }

    @Test
    fun theTextFontHasEveryCharacter() {
        for (character in SpecialCharacters.all) {
            character.text.codePoints().forEach {
                assertTrue(HistoricFonts.hasGlyph(it), "no glyph for ${codePoints(character.text)}")
            }
        }
    }

    @Test
    fun theMenuEntrySaysWhatItInsertsAndHow() {
        assertEquals("ſ   long s   (Ctrl+Shift+S)", SpecialCharacters.all.first().menuText)
    }
}
