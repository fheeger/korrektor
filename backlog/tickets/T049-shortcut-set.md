# T049 The shortcut set and one place that handles it

**Type:** task · **Status:** done · **Story:** S06 · **Task:** S06.1

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
- **Conflict check** (what each key does in a single-line text field or in Windows, and what is done):
  - Up, Down: nothing in a single-line field. Taken over. Shift+Up/Down (selection) are not matched and stay with the field.
  - Ctrl+Home, Ctrl+End: the field would jump to the start/end of the text. Taken over on purpose (the story asks for first/last line). Home and End alone still do that job.
  - Ctrl+Up, Ctrl+Down: nothing in a single-line field. (On macOS the system uses them; decided with N3.)
  - Ctrl+G: nothing in the field; "find next" in some apps, not here.
  - Ctrl+0, Ctrl+1, Ctrl+Plus, Ctrl+Minus, Ctrl+=: nothing in the field, nothing in Windows itself (browsers use them for zoom, so they are familiar).
  - F1: Windows' conventional help key.
  - **AltGr** is Ctrl+Alt on Windows (German layout: `@`, `€`, `{`, `[`). Alt is never accepted, so these characters are never taken for a shortcut. Tested.
  - Ctrl+Shift+Up/Down and Ctrl+Shift+Home/End (select to the start/end) are not matched (the modifiers must be exactly Ctrl), so selecting still works. The one exception is Ctrl+Shift+= for zoom in (the plus of a US layout).
  - Ctrl+C/V/X/Z/A and the other standard editing keys are not in the table and stay with the field.
- **The handler sits on the project's root column** (`Modifier.shortcutKeys` in `App`), only while a project is open: the start screen and the dialogs have no shortcuts. A key goes through it before the focused element (preview phase), so it works from a row, from the go-to field and from a button; a shortcut is used up, whether or not it did anything.
- **`KeyActions` is a one-method interface** (`perform(action)`), `App` takes it as a parameter (default: nothing happens) and T050 to T054 give the actions their effect. Tests pass a recording implementation.
- **Zoom in has four combinations** (Ctrl+Plus, Ctrl+=, Ctrl+Shift+=, Ctrl+NumPad+), zoom out two, fit page and fit width two each (the number row and the numpad).
- **Found while testing: a key reaches the handler only if some element has the focus.** With no focus owner the event goes nowhere. So the app must make sure that something is focused after a page change (T050 puts the cursor into a line of the new page; for a page without lines it needs another target). To be handled in T050 and T051.

## Progress
Done. `Shortcuts.kt` (table, `resolve`, `KeyActions`, `Modifier.shortcutKeys`), wired into `App`. Tests: `ShortcutsTest` (7: no duplicates, every action has a shortcut, every combination resolves, the story keys, other modifiers, key up, unlisted keys, the shown text) and `ShortcutsUiTest` (5: once from a row, the go-to field and a button, used up, non-shortcuts left to the field).
