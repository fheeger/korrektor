package korrektor.characters

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import korrektor.KeyCombo
import korrektor.makesACharacter

/** A text field that special characters can be inserted into: [insert] puts a text at its cursor. */
class CharacterTarget(
    val insert: (String) -> Unit,
)

/**
 * Where special characters go (the text field that has the focus, if it takes them) and the keys that insert them,
 * including the wait for the second key after [LEADER].
 */
class CharacterInput {
    private var target by mutableStateOf<CharacterTarget?>(null)

    /** Whether [LEADER] was pressed and the next key picks the character. */
    var waiting by mutableStateOf(false)
        private set

    // A key press is followed by a "typed" event with its character. If the press was used here, the character must
    // not reach the text field either.
    private var swallowTyped = false

    /** Whether a field that takes characters has the focus. */
    val ready: Boolean get() = target != null

    /** [field] got the focus. */
    fun focused(field: CharacterTarget) {
        if (target !== field) {
            target = field
            waiting = false
        }
    }

    /** [field] lost the focus or is gone. */
    fun blurred(field: CharacterTarget) {
        if (target === field) {
            target = null
            waiting = false
        }
    }

    /** Puts [character] into the focused field; nothing happens without one. */
    fun insert(character: SpecialCharacter) {
        waiting = false
        target?.insert?.invoke(character.text)
    }

    /** Handles a key event before anything else sees it. Returns whether it is used up. */
    fun onKey(event: KeyEvent): Boolean =
        when (event.type) {
            KeyEventType.KeyDown -> {
                swallowTyped = if (waiting) secondKey(event) else firstKey(event)
                swallowTyped
            }
            KeyEventType.KeyUp -> {
                swallowTyped = false
                false
            }
            // Neither down nor up: the event that types a character.
            else -> swallowTyped
        }

    private fun firstKey(event: KeyEvent): Boolean {
        if (event.makesACharacter()) return false
        val combo = KeyCombo(event.key, event.isCtrlPressed, event.isShiftPressed)
        val character = DIRECT[combo]
        if (combo == LEADER) {
            waiting = ready
        } else if (character != null) {
            insert(character)
        }
        return combo == LEADER || character != null
    }

    // Any key ends the wait. Ctrl may still be held from the leader, so it does not matter here. A key without a
    // character (Escape too) is used up as well: it types nothing.
    private fun secondKey(event: KeyEvent): Boolean {
        if (event.key in MODIFIER_KEYS) return false
        waiting = false
        if (!event.makesACharacter()) {
            AFTER_LEADER[KeyCombo(event.key, shift = event.isShiftPressed)]?.let(::insert)
        }
        return true
    }
}

private val DIRECT: Map<KeyCombo, SpecialCharacter> =
    SpecialCharacters.all.filterNot { it.afterLeader }.associateBy { it.combo }

private val AFTER_LEADER: Map<KeyCombo, SpecialCharacter> =
    SpecialCharacters.all.filter { it.afterLeader }.associateBy { it.combo }

private val MODIFIER_KEYS =
    setOf(
        Key.ShiftLeft,
        Key.ShiftRight,
        Key.CtrlLeft,
        Key.CtrlRight,
        Key.AltLeft,
        Key.AltRight,
        Key.MetaLeft,
        Key.MetaRight,
        Key.CapsLock,
        Key(java.awt.event.KeyEvent.VK_ALT_GRAPH),
    )
