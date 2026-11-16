# T063 Ctrl+S does nothing

**Type:** task · **Status:** ready for development · **Story:** S07 · **Task:** S07.6

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
(filled in while building)

## Progress
Not started.
