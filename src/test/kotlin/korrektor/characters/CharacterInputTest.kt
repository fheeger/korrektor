package korrektor.characters

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(InternalComposeUiApi::class)
class CharacterInputTest {
    private val inserted = mutableListOf<String>()
    private val field = CharacterTarget { inserted += it }
    private val input = CharacterInput().apply { focused(field) }

    private fun down(
        key: Key,
        ctrl: Boolean = false,
        shift: Boolean = false,
        alt: Boolean = false,
    ) = input.onKey(
        KeyEvent(key, KeyEventType.KeyDown, isCtrlPressed = ctrl, isShiftPressed = shift, isAltPressed = alt),
    )

    private fun leader() = down(Key.K, ctrl = true)

    @Test
    fun everyCharacterCanBeTyped() {
        for (character in SpecialCharacters.all) {
            inserted.clear()
            if (character.afterLeader) assertTrue(leader())
            val combo = character.combo
            assertTrue(down(combo.key, combo.ctrl, combo.shift), character.shortcut)
            assertEquals(listOf(character.text), inserted, character.shortcut)
            assertFalse(input.waiting)
        }
    }

    @Test
    fun ctrlMayStillBeHeldForTheSecondKey() {
        leader()
        down(Key.R, ctrl = true)
        assertEquals(listOf("ꝛ"), inserted)
    }

    @Test
    fun modifierKeysDoNotEndTheWait() {
        leader()
        assertFalse(down(Key.ShiftLeft, shift = true))
        assertFalse(down(Key.CtrlLeft, ctrl = true))
        assertTrue(input.waiting)
        down(Key.A, shift = true)
        assertEquals(listOf("Aͤ"), inserted)
    }

    @Test
    fun escapeAndKeysWithoutACharacterCancelAndAreUsedUp() {
        for (key in listOf(Key.Escape, Key.J, Key.Enter, Key.DirectionDown)) {
            leader()
            assertTrue(down(key), "$key")
            assertFalse(input.waiting)
        }
        assertEquals(emptyList(), inserted)
        assertFalse(down(Key.Escape), "after the wait Escape is anybody's key again")
    }

    @Test
    fun anAltGrKeyIsNeverACharacterOfOurs() {
        assertFalse(down(Key.S, ctrl = true, shift = true, alt = true))
        leader()
        assertTrue(down(Key.R, ctrl = true, alt = true), "it ends the wait")
        assertEquals(emptyList(), inserted)
    }

    @Test
    fun otherKeysAreLeftAlone() {
        assertFalse(down(Key.S))
        assertFalse(down(Key.S, shift = true))
        assertFalse(down(Key.S, ctrl = true))
        assertFalse(down(Key.K))
        assertEquals(emptyList(), inserted)
    }

    @Test
    fun withoutAFieldNothingHappens() {
        input.blurred(field)
        assertFalse(input.ready)
        assertTrue(down(Key.S, ctrl = true, shift = true))
        assertTrue(leader())
        assertFalse(input.waiting)
        input.insert(SpecialCharacters.all.first())
        assertEquals(emptyList(), inserted)
    }

    @Test
    fun anotherFieldEndsTheWait() {
        leader()
        input.focused(CharacterTarget {})
        assertFalse(input.waiting)
        input.blurred(field)
        assertTrue(input.ready, "the old field going away does not take the new one")
    }

    @Test
    fun insertingReplacesTheSelectionAndPutsTheCursorBehindIt() {
        val backwards = TextFieldValue("abcdef", TextRange(4, 1))
        assertEquals(TextFieldValue("auͤef", TextRange(3)), backwards.inserting("uͤ"))
        assertEquals(TextFieldValue("abſ", TextRange(3)), TextFieldValue("ab", TextRange(2)).inserting("ſ"))
    }
}
