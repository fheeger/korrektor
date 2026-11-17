# T079 Change the status: Ctrl+Enter, menu and button

**Type:** task · **Status:** done · **Story:** S09 · **Task:** S09.3, S09.6 (keys of this ticket)

## What needs doing
- **`AppAction.MARK_CORRECTED`: Ctrl+Enter** marks the page shown as corrected and opens the next page (the cursor on its first line); on the last page it only marks. **`MARK_UNCHECKED`: Ctrl+Shift+Enter** marks it as unchecked and stays.
- **A "Page" menu** (after "Go") with both entries; the entries and keys are in the table of shortcuts, so the list on F1 has them.
- **The button** in the status bar: "Mark as corrected" or, on a corrected page, "Mark as unchecked"; it stays on the page.
- A status change is one undo step (T077).

## Acceptance criteria
1. Ctrl+Enter on an unchecked page 3: page 3 is corrected and page 4 is shown, the cursor in its first line; Ctrl+Z gives back page 3 as unchecked (shown).
2. Ctrl+Enter on the last page marks it and stays; on a corrected page it keeps it corrected and goes on.
3. Ctrl+Shift+Enter marks the page unchecked.
4. The button changes the status in both directions and its text follows.
5. The menu entries and the F1 list show both actions; every action has a handler (existing test).
6. A corrected page stays corrected when text is edited (no automatic change).
7. The keys work with the focus in a line, in the page field and on a button.

## Approach
`Shortcuts.kt`, `ProjectKeys.kt`, `Menus.kt`, `Main.kt` (menu state), `StatusBar.kt`, README. Tests: `ShortcutsTest`, `MenusTest`, `ShortcutListTest`, `StatusUiTest` (whole window, real key events).

## Dependencies
T077, T078.

## Decisions
- **Ctrl+Enter is "corrected and next"** as the story says; the plain "mark and stay" is the button.
- **Ctrl+Shift+Enter for "unchecked"** so that the menu and the list show a key for every entry, as the table requires.

## Progress
Done. `MARK_CORRECTED` (Ctrl+Enter, also the NumPad one) and `MARK_UNCHECKED` (Ctrl+Shift+Enter) in the table, the new *Page* menu, `ProjectKeys.markCorrected`/`toggleStatus`, the button in `StatusBar`, README. Tests: `StatusUiTest` (6 more: keys, last page, button, editing keeps the status, the key in the page field), `ShortcutsTest`, `MenusTest`. Build green.
