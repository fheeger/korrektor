# T063 Ctrl+S does nothing

**Type:** task · **Status:** done · **Story:** S07 · **Task:** S07.6

## What needs doing
- Ctrl+S is caught by the key handler of the open project (`Modifier.shortcutKeys`) and used up: nothing happens, no message, no dialog, nothing is typed. It is not in the table of shortcuts (it is not a feature and not in the menus or the F1 list), so the handler knows it as a key to swallow.
- Ctrl+S with Shift or Alt is not swallowed (it may be an AltGr character on some layouts). Same exact-modifier rule as the other shortcuts (including AltGr).
- Ctrl+S does not cause a save either: saving happens by itself.

## Acceptance criteria
1. Ctrl+S in a text row, in the page field and with the focus on a button changes nothing (text, page, status, banner, dialogs) and reaches no action (UI test with a recording `KeyActions`).
2. No `s` is typed into the line.
3. Ctrl+Shift+S and Ctrl+Alt+S are not swallowed.
4. Ctrl+S does not start a save sooner (nothing is written by it).

## Approach
`Shortcuts.kt` (`Shortcuts.isIgnored(event)` shares the modifier rule), `shortcutKeys`; tests in `ShortcutsTest` and `ShortcutsUiTest`.

## Dependencies
None.

## Decisions
- **`Shortcuts.isIgnored(event)`** is the rule for keys that are swallowed (only Ctrl+S today), with the same modifier rule as `resolve` (key down; no Alt, Meta or AltGr; Shift only where listed). `shortcutKeys` uses up an event if it is an action or ignored. Ctrl+S is not in the table of shortcuts, so it is not in the menus, not in the F1 list and has no action.
- **Ctrl+Shift+S and Ctrl+Alt+S are not swallowed:** on some layouts AltGr+S (Ctrl+Alt+S on Windows) types a character (`ſ` on a layout for historical text, for one), and the field must get it.
- **Ctrl+S saves nothing and says nothing,** by design (the story): the timer saves by itself, and a keypress that made a write would make "Saved" look like something the user has to do.
- **What the tests can show:** the test driver sends key events without the characters a real keyboard types, so "no `s` appears" is not proved by the UI test (the field would not type one from a key event anyway). What is proved: the event is used up by the handler (`isIgnored`, which the driver cannot bypass), no action, no message, no dialog, no banner, no change of text or page, no early save. The real keyboard goes on the list for the story report.

## Progress
Done. `Shortcuts.isIgnored`, `shortcutKeys`. Tests: `CtrlSTest` (5: Ctrl+S ignored and not in the table; Ctrl+Shift+S, Ctrl+Alt+S, plain S, key up and other keys not ignored; Ctrl+S in a row, in the page field and on a button changes nothing; no early save).
