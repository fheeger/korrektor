package korrektor

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/** What the keyboard can do besides typing. What each action does is decided where [KeyActions] is implemented. */
enum class AppAction {
    UNDO,
    REDO,
    PREVIOUS_LINE,
    NEXT_LINE,
    FIRST_LINE,
    LAST_LINE,
    PREVIOUS_PAGE,
    NEXT_PAGE,
    GO_TO_PAGE,
    MARK_CORRECTED,
    MARK_UNCHECKED,
    ZOOM_IN,
    ZOOM_OUT,
    FIT_PAGE,
    FIT_WIDTH,
    SHOW_SHORTCUTS,
}

/** One key with its modifiers. Exactly these are held: Ctrl+Shift+Down is not [Ctrl]+Down. */
data class KeyCombo(
    val key: Key,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
) {
    /** The combination as shown in menus and lists, for example "Ctrl+Down". */
    val text: String
        get() =
            listOfNotNull("Ctrl".takeIf { ctrl }, "Shift".takeIf { shift }, KEY_NAMES[key] ?: key.toString())
                .joinToString("+")
}

/**
 * An action with its menu [label], a short [description] for the list on F1, and its key combinations. The first of
 * [combos] is the one shown; the others are alternatives (the same action on another keyboard layout).
 */
data class Shortcut(
    val action: AppAction,
    val label: String,
    val description: String,
    val combos: List<KeyCombo>,
) {
    /** The combination shown for this action. */
    val shown: KeyCombo get() = combos.first()
}

/**
 * The single table of keyboard shortcuts. The key handler, the menus and the list on F1 are all made from it, so
 * that they cannot disagree. Enter, Backspace and Delete are not here: they are the keys of the text field in a line.
 *
 * Conflicts with the keys of the text field (it would use Ctrl+Home and Ctrl+End to jump to the start and end of the
 * line's text, which Home and End still do) and with Windows were checked when the table was made (T049): nothing
 * else of the field uses these combinations. AltGr counts as Ctrl+Alt on Windows, and as [resolve] does not accept
 * Alt, characters typed with AltGr (such as `@` or `€`) are never taken for a shortcut.
 */
object Shortcuts {
    private val table =
        listOf(
            Shortcut(
                AppAction.UNDO,
                "Undo",
                "Takes back the last change, on whatever page it was",
                listOf(KeyCombo(Key.Z, ctrl = true)),
            ),
            Shortcut(
                AppAction.REDO,
                "Redo",
                "Brings back the change that was taken back last",
                listOf(KeyCombo(Key.Y, ctrl = true), KeyCombo(Key.Z, ctrl = true, shift = true)),
            ),
            Shortcut(
                AppAction.PREVIOUS_LINE,
                "Previous line",
                "Moves the cursor to the line above",
                listOf(KeyCombo(Key.DirectionUp)),
            ),
            Shortcut(
                AppAction.NEXT_LINE,
                "Next line",
                "Moves the cursor to the line below",
                listOf(KeyCombo(Key.DirectionDown)),
            ),
            Shortcut(
                AppAction.FIRST_LINE,
                "First line",
                "Moves the cursor to the first line of the page",
                listOf(KeyCombo(Key.MoveHome, ctrl = true)),
            ),
            Shortcut(
                AppAction.LAST_LINE,
                "Last line",
                "Moves the cursor to the last line of the page",
                listOf(KeyCombo(Key.MoveEnd, ctrl = true)),
            ),
            Shortcut(
                AppAction.PREVIOUS_PAGE,
                "Previous page",
                "Opens the previous page",
                listOf(KeyCombo(Key.DirectionUp, ctrl = true)),
            ),
            Shortcut(
                AppAction.NEXT_PAGE,
                "Next page",
                "Opens the next page",
                listOf(KeyCombo(Key.DirectionDown, ctrl = true)),
            ),
            Shortcut(
                AppAction.GO_TO_PAGE,
                "Go to page…",
                "Moves to the page number field",
                listOf(KeyCombo(Key.G, ctrl = true)),
            ),
            Shortcut(
                AppAction.MARK_CORRECTED,
                "Mark corrected and go on",
                "Marks the page as corrected and opens the next page",
                listOf(KeyCombo(Key.Enter, ctrl = true), KeyCombo(Key.NumPadEnter, ctrl = true)),
            ),
            Shortcut(
                AppAction.MARK_UNCHECKED,
                "Mark as unchecked",
                "Marks the page as unchecked again",
                listOf(
                    KeyCombo(Key.Enter, ctrl = true, shift = true),
                    KeyCombo(Key.NumPadEnter, ctrl = true, shift = true),
                ),
            ),
            Shortcut(
                AppAction.ZOOM_IN,
                "Zoom in",
                "Zooms the scan in",
                // The plus key of a US layout is Shift+=; the plain Ctrl+Plus is the one shown.
                listOf(
                    KeyCombo(Key.Plus, ctrl = true),
                    KeyCombo(Key.Equals, ctrl = true),
                    KeyCombo(Key.Equals, ctrl = true, shift = true),
                    KeyCombo(Key.NumPadAdd, ctrl = true),
                ),
            ),
            Shortcut(
                AppAction.ZOOM_OUT,
                "Zoom out",
                "Zooms the scan out",
                listOf(KeyCombo(Key.Minus, ctrl = true), KeyCombo(Key.NumPadSubtract, ctrl = true)),
            ),
            Shortcut(
                AppAction.FIT_PAGE,
                "Fit page",
                "Shows the whole page",
                listOf(KeyCombo(Key.Zero, ctrl = true), KeyCombo(Key.NumPad0, ctrl = true)),
            ),
            Shortcut(
                AppAction.FIT_WIDTH,
                "Fit width",
                "Makes the page as wide as the panel",
                listOf(KeyCombo(Key.One, ctrl = true), KeyCombo(Key.NumPad1, ctrl = true)),
            ),
            Shortcut(AppAction.SHOW_SHORTCUTS, "Keyboard shortcuts", "Shows this list", listOf(KeyCombo(Key.F1))),
        )

    /**
     * The keys of the text field in a line that change the lines: what the user types, with what they do. They are
     * not shortcuts (the line handles them, see `lineKey`) but the list on F1 shows them with the others.
     */
    val editorKeys: List<Pair<String, String>> =
        listOf(
            "Enter" to "Splits the line at the cursor; at the end of a line it adds an empty line below",
            "Backspace" to "At the start of a line: joins it with the line above (an empty line is deleted)",
            "Delete" to "At the end of a line: joins the line below into it",
        )

    /** Every shortcut, in the order of menus and list. */
    val all: List<Shortcut> get() = table

    /** The shortcut of [action]. */
    fun of(action: AppAction): Shortcut = table.first { it.action == action }

    private val byCombo: Map<KeyCombo, AppAction> =
        table.flatMap { s -> s.combos.map { it to s.action } }.toMap()

    /**
     * The action for a key press, or null. Only a key going down counts, and only with exactly the modifiers of the
     * combination: Alt and Meta are never accepted (see the note above on AltGr), Shift only where the table says so.
     */
    fun resolve(event: KeyEvent): AppAction? =
        if (event.type != KeyEventType.KeyDown || event.makesACharacter()) {
            null
        } else {
            byCombo[KeyCombo(event.key, event.isCtrlPressed, event.isShiftPressed)]
        }

    /**
     * Whether the key is one that does nothing and must not reach the text: Ctrl+S, which people press out of habit.
     * Everything is saved by itself (S07), so there is nothing to ask for and nothing to say. The same rule for the
     * modifiers as [resolve]: Ctrl+Shift+S and an AltGr+S are other keys.
     */
    fun isIgnored(event: KeyEvent): Boolean =
        event.type == KeyEventType.KeyDown &&
            !event.makesACharacter() &&
            KeyCombo(event.key, event.isCtrlPressed, event.isShiftPressed) in IGNORED
}

private val IGNORED = setOf(KeyCombo(Key.S, ctrl = true))

private val KEY_NAMES =
    mapOf(
        Key.DirectionUp to "Up",
        Key.DirectionDown to "Down",
        Key.MoveHome to "Home",
        Key.MoveEnd to "End",
        Key.Plus to "Plus",
        Key.Equals to "=",
        Key.NumPadAdd to "NumPad +",
        Key.Minus to "Minus",
        Key.NumPadSubtract to "NumPad −",
        Key.Zero to "0",
        Key.One to "1",
        Key.NumPad0 to "NumPad 0",
        Key.NumPad1 to "NumPad 1",
        Key.G to "G",
        Key.Z to "Z",
        Key.Y to "Y",
        Key.Enter to "Enter",
        Key.NumPadEnter to "NumPad Enter",
        Key.F1 to "F1",
    )

/**
 * Whether Alt, Meta or AltGr is held: none of them is part of a shortcut. On Windows the JDK may report AltGr as Ctrl
 * plus the AltGraph modifier, without Alt, so that AltGr+0 (`}` on a German keyboard) would look like Ctrl+0; such a
 * key is a character, never a shortcut.
 */
private fun KeyEvent.makesACharacter(): Boolean =
    isAltPressed || isMetaPressed || (nativeKeyEvent as? java.awt.event.KeyEvent)?.isAltGraphDown == true

/**
 * What the shortcuts do. A key that is a shortcut is used up whether or not there is something to do (Ctrl+Down on
 * the last page does nothing), so that the text field never acts on it.
 */
fun interface KeyActions {
    fun perform(action: AppAction)
}

/**
 * The one key handler for the open project: it sits above everything, and (as the preview of a key event goes from
 * the outside in) sees the shortcut before the line, the page field or a button can, wherever the focus is. A key
 * that is a shortcut is used up: the focused element never sees it.
 */
fun Modifier.shortcutKeys(actions: KeyActions): Modifier =
    onPreviewKeyEvent { event ->
        val action = Shortcuts.resolve(event)
        if (action != null) actions.perform(action)
        action != null || Shortcuts.isIgnored(event)
    }
