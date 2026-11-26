package korrektor.characters

import androidx.compose.ui.input.key.Key
import korrektor.KeyCombo

/** The groups of the Insert menu and of the list on F1, in their order. */
enum class CharacterGroup(
    val title: String,
) {
    HISTORIC("Historic"),
    LETTERS("Letters"),
    DASHES("Dashes"),
    QUOTES("Quotes"),
    SYMBOLS("Symbols"),
}

/**
 * A character the keyboard may not have: the [text] that is inserted, exactly these code points (V1: nothing is
 * normalized), and its shortcut. With [afterLeader] the [combo] is the second key, pressed after [LEADER].
 */
data class SpecialCharacter(
    val group: CharacterGroup,
    val text: String,
    val name: String,
    val combo: KeyCombo,
    val afterLeader: Boolean,
) {
    /** The shortcut as shown, for example "Ctrl+Shift+S" or "Ctrl+K, Shift+A". */
    val shortcut: String get() = if (afterLeader) "${LEADER.text}, ${combo.text}" else combo.text
}

/** The key that is pressed before the second key of the characters that have no combination of their own. */
val LEADER = KeyCombo(Key.K, ctrl = true)

/** A character with Ctrl+Shift+[key]. */
private fun direct(
    text: String,
    name: String,
    key: Key,
) = Entry(text, name, KeyCombo(key, ctrl = true, shift = true), false)

/** A character with [LEADER] and then [key]. */
private fun then(
    text: String,
    name: String,
    key: Key,
    shift: Boolean = false,
) = Entry(text, name, KeyCombo(key, shift = shift), true)

private class Entry(
    val text: String,
    val name: String,
    val combo: KeyCombo,
    val afterLeader: Boolean,
)

private fun group(
    group: CharacterGroup,
    vararg entries: Entry,
) = entries.map { SpecialCharacter(group, it.text, it.name, it.combo, it.afterLeader) }

/**
 * The single table of the special characters (T108): the Insert menu, the keys and the list on F1 are made from it.
 * Combining characters are written as escapes, so that it can be seen what is inserted.
 */
object SpecialCharacters {
    private val historic =
        group(
            CharacterGroup.HISTORIC,
            direct("ſ", "long s", Key.S),
            direct("aͤ", "a with small e", Key.A),
            direct("oͤ", "o with small e", Key.O),
            direct("uͤ", "u with small e", Key.U),
            then("Aͤ", "A with small e", Key.A, shift = true),
            then("Oͤ", "O with small e", Key.O, shift = true),
            then("Uͤ", "U with small e", Key.U, shift = true),
            then("uͦ", "u with small o", Key.V),
            then("ꝛ", "r rotunda", Key.R),
        )

    private val letters =
        group(
            CharacterGroup.LETTERS,
            direct("æ", "ae", Key.E),
            then("Æ", "capital AE", Key.E, shift = true),
            direct("œ", "oe", Key.W),
            then("Œ", "capital OE", Key.W, shift = true),
            then("ë", "e with diaeresis", Key.E),
            then("Ë", "capital E with diaeresis", Key.Y),
            then("ï", "i with diaeresis", Key.I),
            then("Ï", "capital I with diaeresis", Key.I, shift = true),
        )

    private val dashes =
        group(
            CharacterGroup.DASHES,
            direct("—", "em dash", Key.M),
            then("–", "en dash", Key.N),
            direct("⸗", "double hyphen", Key.H),
        )

    private val quotes =
        group(
            CharacterGroup.QUOTES,
            then("„", "low double quote", Key.One),
            then("“", "left double quote", Key.Two),
            then("”", "right double quote", Key.Three),
            then("‚", "low single quote", Key.Four),
            then("‘", "left single quote", Key.Five),
            then("’", "right single quote, apostrophe", Key.Six),
            then("»", "right guillemet", Key.Seven),
            then("«", "left guillemet", Key.Eight),
            then("›", "single right guillemet", Key.Nine),
            then("‹", "single left guillemet", Key.Zero),
        )

    private val symbols =
        group(
            CharacterGroup.SYMBOLS,
            then("§", "section sign", Key.P),
            then("†", "dagger", Key.D),
            then("‡", "double dagger", Key.D, shift = true),
            then("£", "pound sterling", Key.L),
            then("℔", "pound weight", Key.B),
            then("°", "degree", Key.G),
            then("×", "multiplication sign", Key.X),
            then("½", "one half", Key.H),
            then("¼", "one quarter", Key.Q),
            then("¾", "three quarters", Key.T),
        )

    /** Every character, in the order of the menu and the list. */
    val all: List<SpecialCharacter> = historic + letters + dashes + quotes + symbols
}
