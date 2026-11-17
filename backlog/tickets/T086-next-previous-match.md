# T086 Next and previous match: Enter, F3, Shift+F3

**Type:** task · **Status:** ready for development · **Story:** S10 · **Task:** S10.3, S10.7 (the keys of this ticket)

## What needs doing
- `AppAction.NEXT_MATCH` (F3) and `PREVIOUS_MATCH` (Shift+F3): go to the next (previous) result after the current one, wrapping around at the end of the list, also when the focus is in the text. Enter in the search field is the same as F3, Shift+Enter as Shift+F3.
- "Go to" a match = the page is opened, the cursor goes to the start of the match, the scan highlights the line, and the result is marked in the list (and the list scrolls to it). With no results nothing happens.
- Where the match is: the next match after the cursor if there is no current one (the first result at or after the place of the cursor in the page shown, else the first of the book).
- If the focus is in the search field, it stays there (Enter again goes on): `BookEditor.focusHeld` is true while the field has the focus, and the text panel then does not take the focus when the page changes or a cursor is wanted. A click on a result and F3 from the text move the focus to the text.
- Menu entries (Edit) that are grey without results, and the F1 list.

## Acceptance criteria
1. With three matches on pages 2, 2 and 5: F3 from page 1 goes to the first, then the second, then page 5, then wraps to the first; Shift+F3 goes back.
2. After F3 the cursor is in the line at the match, and the highlight is on the line.
3. Enter in the search field goes to the next match, the focus stays in the field, so a second Enter goes to the one after; the text is not changed.
4. F3 from the text moves the cursor and the text keeps the focus.
5. The menu entries are disabled with no results; the keys are used up (the text field never sees F3).

## Approach
`SearchModel.next/previous/goTo`, `Shortcuts.kt`, `Menus.kt`, `ProjectKeys.kt`, `TextPanel.kt` (the focus rule), `BookEditor` (`focusHeld`, a way to show a place on any page: `showPlace`). Unit tests for the order and the wrap, window test with real keys.

## Dependencies
T085.

## Decisions

## Progress
