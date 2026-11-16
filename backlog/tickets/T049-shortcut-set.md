# T049 The shortcut set and one place that handles it

**Type:** task · **Status:** ready for development · **Story:** S06 · **Task:** S06.1

## What needs doing
- **`Shortcuts`** (package `korrektor`): one table of all actions the keyboard can trigger: an id, a menu label, the key with its modifiers, and a short description. The table is the single source for the key handler, the menus (T054) and the F1 list (T054).
- The set (from the story): Up, Down, Ctrl+Home, Ctrl+End (lines), Ctrl+Down, Ctrl+Up, Ctrl+G (pages), Ctrl+Plus, Ctrl+Minus, Ctrl+0, Ctrl+1 (zoom), F1 (shortcut list). Enter, Backspace and Delete are text-editor keys of the row. They are listed as information in the F1 list, but they are not in the handler table.
- Ctrl+Plus also answers to Ctrl+= (the plus key of a US layout needs Shift) and Ctrl+NumPad+; Ctrl+Minus also to Ctrl+NumPad−.
- **`Shortcuts.resolve(event)`**: the action for a key event (key down only, exact modifiers: Ctrl+Shift+Down is not Ctrl+Down), or null.
- **One key handler** at the root of the project screen (`onPreviewKeyEvent`), so that the shortcuts work wherever the focus is (a row, the go-to field, a button). The handler only maps events to actions and calls an action interface. What an action does is in the following tickets.
- **Conflict check**, written down in the ticket's Decisions: each key against standard Windows and text-editing keys (the field's own Ctrl+Home/End, Ctrl+Up/Down, Ctrl+G, Ctrl+0/1, Ctrl+Plus/Minus), and what is done about it.

## Acceptance criteria
1. No two actions share a key combination (unit test over the table).
2. `resolve` returns the action for each listed combination and the alternates, and null for the same key with another modifier (Shift, Alt, Meta added), for key-up events and for unlisted keys.
3. A shortcut pressed in a text row, in the go-to field and with the focus on a button reaches the action interface exactly once (UI test with a recording interface).
4. The conflict check is in the ticket and every conflict has a resolution.

## Approach
- `Shortcuts.kt` with an `enum class AppAction`, a data class for the entry and `resolve`. A `KeyActions` interface (the action methods) that `App` implements with the editor and the viewer; tests use a recording implementation.
- Unit tests: `ShortcutsTest`. UI test: `ShortcutsUiTest` with `runComposeUiTest` and a recording `KeyActions`.

## Dependencies
None (the actions themselves are in T050 to T054).

## Decisions
(filled in while building: the conflict check)

## Progress
Not started.
