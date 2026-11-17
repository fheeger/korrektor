# T080 Jump to the next or previous unchecked page

**Type:** task · **Status:** ready for development · **Story:** S09 · **Task:** S09.4, S09.6 (keys of this ticket)

## What needs doing
- **`AppAction.NEXT_UNCHECKED`: Ctrl+Shift+Down** and **`PREVIOUS_UNCHECKED`: Ctrl+Shift+Up** open the next (previous) page whose status is unchecked, with the cursor on its first line. No wrap-around; with no such page nothing happens (the menu entry is grey).
- Entries in the "Page" menu (enabled by `MenuState`) and on the F1 list.
- `BookEditor.uncheckedAfter(page, count)` and `uncheckedBefore(page)`: the pages are in memory, so no page file is read.

## Acceptance criteria
1. With pages 2, 3 and 5 corrected in a 6-page book, from page 1 Ctrl+Shift+Down opens page 4, again 6; from 6 Ctrl+Shift+Up opens 4 and then 1.
2. With none after the page, the key does nothing and the menu entry is disabled.
3. The page being corrected counts as the current page, not as a target: after Ctrl+Enter on page 4 the next unchecked after 4 is 6.
4. It uses the statuses in memory, so a status that is not saved yet counts.
5. Ctrl+Shift+Down is used up (the text field never sees it).

## Approach
`PageStatuses.kt`, `Shortcuts.kt`, `ProjectKeys.kt`, `Menus.kt`, `Main.kt`, README. Tests: unit for the two functions (also 1000 pages, timing), `ShortcutsTest`, `MenusTest`, window test.

## Dependencies
T077, T079.

## Decisions
- A page with a status this version does not know is not "unchecked": the jump skips it.

## Progress
