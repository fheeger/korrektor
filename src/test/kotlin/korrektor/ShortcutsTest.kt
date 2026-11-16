package korrektor

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShortcutsTest {
    // The public way to make a KeyEvent without a window is marked internal: fine for a test of pure key logic.
    @OptIn(InternalComposeUiApi::class)
    private fun event(
        key: Key,
        ctrl: Boolean = false,
        shift: Boolean = false,
        alt: Boolean = false,
        meta: Boolean = false,
    ) = KeyEvent(
        key,
        KeyEventType.KeyDown,
        isCtrlPressed = ctrl,
        isShiftPressed = shift,
        isAltPressed = alt,
        isMetaPressed = meta,
    )

    /** The key going up again, with Ctrl as it is while the shortcut is released. */
    @OptIn(InternalComposeUiApi::class)
    private fun release(key: Key) = KeyEvent(key, KeyEventType.KeyUp, isCtrlPressed = true)

    @Test
    fun noTwoActionsShareAKeyCombination() {
        val combos = Shortcuts.all.flatMap { it.combos }
        assertEquals(combos.size, combos.toSet().size, "a combination is used twice")
        assertEquals(AppAction.entries.toSet(), Shortcuts.all.map { it.action }.toSet(), "every action has a shortcut")
        assertEquals(
            Shortcuts.all.size,
            Shortcuts.all
                .map { it.action }
                .toSet()
                .size,
            "an action is listed twice",
        )
    }

    @Test
    fun everyListedCombinationResolvesToItsAction() {
        for (shortcut in Shortcuts.all) {
            for (combo in shortcut.combos) {
                assertEquals(
                    shortcut.action,
                    Shortcuts.resolve(event(combo.key, combo.ctrl, combo.shift)),
                    "${shortcut.action} on ${combo.text}",
                )
            }
        }
    }

    @Test
    fun theStoryKeysAreThere() {
        assertEquals(AppAction.PREVIOUS_LINE, Shortcuts.resolve(event(Key.DirectionUp)))
        assertEquals(AppAction.NEXT_LINE, Shortcuts.resolve(event(Key.DirectionDown)))
        assertEquals(AppAction.FIRST_LINE, Shortcuts.resolve(event(Key.MoveHome, ctrl = true)))
        assertEquals(AppAction.LAST_LINE, Shortcuts.resolve(event(Key.MoveEnd, ctrl = true)))
        assertEquals(AppAction.NEXT_PAGE, Shortcuts.resolve(event(Key.DirectionDown, ctrl = true)))
        assertEquals(AppAction.PREVIOUS_PAGE, Shortcuts.resolve(event(Key.DirectionUp, ctrl = true)))
        assertEquals(AppAction.GO_TO_PAGE, Shortcuts.resolve(event(Key.G, ctrl = true)))
        assertEquals(AppAction.ZOOM_IN, Shortcuts.resolve(event(Key.Plus, ctrl = true)))
        assertEquals(AppAction.ZOOM_IN, Shortcuts.resolve(event(Key.Equals, ctrl = true)))
        assertEquals(AppAction.ZOOM_IN, Shortcuts.resolve(event(Key.Equals, ctrl = true, shift = true)))
        assertEquals(AppAction.ZOOM_IN, Shortcuts.resolve(event(Key.NumPadAdd, ctrl = true)))
        assertEquals(AppAction.ZOOM_OUT, Shortcuts.resolve(event(Key.Minus, ctrl = true)))
        assertEquals(AppAction.ZOOM_OUT, Shortcuts.resolve(event(Key.NumPadSubtract, ctrl = true)))
        assertEquals(AppAction.FIT_PAGE, Shortcuts.resolve(event(Key.Zero, ctrl = true)))
        assertEquals(AppAction.FIT_WIDTH, Shortcuts.resolve(event(Key.One, ctrl = true)))
        assertEquals(AppAction.SHOW_SHORTCUTS, Shortcuts.resolve(event(Key.F1)))
    }

    @Test
    fun theSameKeyWithAnotherModifierIsNotAShortcut() {
        assertNull(Shortcuts.resolve(event(Key.DirectionDown, ctrl = true, shift = true)), "Ctrl+Shift+Down selects")
        assertNull(Shortcuts.resolve(event(Key.DirectionDown, shift = true)), "Shift+Down")
        assertNull(Shortcuts.resolve(event(Key.DirectionDown, alt = true)), "Alt+Down")
        assertNull(Shortcuts.resolve(event(Key.DirectionDown, ctrl = true, alt = true)), "AltGr+Down")
        assertNull(Shortcuts.resolve(event(Key.DirectionDown, ctrl = true, meta = true)), "Meta")
        assertNull(Shortcuts.resolve(event(Key.MoveHome)), "Home alone stays with the field")
        assertNull(Shortcuts.resolve(event(Key.G)), "a G typed in a line")
        assertNull(Shortcuts.resolve(event(Key.Zero)), "a 0 typed in a line")
        assertNull(Shortcuts.resolve(event(Key.F1, ctrl = true)))
        assertNull(Shortcuts.resolve(event(Key.Plus)), "a + typed in a line")
    }

    @Test
    fun aKeyThatGoesUpIsNotAShortcut() {
        assertNull(
            Shortcuts.resolve(
                release(Key.DirectionDown),
            ),
        )
        assertNull(
            Shortcuts.resolve(
                release(Key.F1),
            ),
        )
    }

    @Test
    fun keysThatAreNotListedAreNotShortcuts() {
        for (key in listOf(Key.Enter, Key.Backspace, Key.Delete, Key.A, Key.Escape, Key.PageDown, Key.Tab)) {
            assertNull(Shortcuts.resolve(event(key)), "$key")
            assertNull(Shortcuts.resolve(event(key, ctrl = true)), "Ctrl+$key")
        }
    }

    @Test
    fun theShownCombinationIsTheFirstOneAndReadsLikeAMenuEntry() {
        assertEquals("Ctrl+Down", Shortcuts.of(AppAction.NEXT_PAGE).shown.text)
        assertEquals("Down", Shortcuts.of(AppAction.NEXT_LINE).shown.text)
        assertEquals("Ctrl+Home", Shortcuts.of(AppAction.FIRST_LINE).shown.text)
        assertEquals("F1", Shortcuts.of(AppAction.SHOW_SHORTCUTS).shown.text)
        assertTrue(Shortcuts.all.all { it.label.isNotBlank() && it.description.isNotBlank() })
    }
}
