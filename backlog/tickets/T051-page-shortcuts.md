# T051 Page shortcuts: next, previous and go to page

**Type:** task · **Status:** ready for development · **Story:** S06 · **Task:** S06.3

## What needs doing
- **Ctrl+Down / Ctrl+Up** open the next or previous page, with the cursor on the first line of the new page (so that the next keystroke edits). On the ends of the book nothing happens. They use the arrival mechanism of T050 (`FIRST`).
- **Ctrl+G** moves the focus to the "Go to page" field of the viewer and selects its content. Enter goes to the page and puts the cursor on the first line (the focus returns to the text). A wrong number is undone and the field keeps the focus. Escape returns the focus to the text without going anywhere.
- The viewer's field needs a `FocusRequester` that the screen can trigger: `ScanLinks` or a small new holder passed to `ProjectViewer` (decide while building).

## Acceptance criteria
1. Ctrl+Down/Ctrl+Up change the page by one and put the cursor on the first line. On page 1 and the last page they do nothing (UI test).
2. Ctrl+G focuses and selects the field (UI test: the typed digits replace the old number). Enter with a valid number opens that page with the cursor in its first line. An invalid number leaves page and field as they were.
3. Escape in the field returns the focus to the text without changing the page.
4. The shortcuts work from a text row and from the go-to field alike.
5. The existing mouse behaviour of the field (Enter, a wrong number is undone) is unchanged.

## Approach
- `PageViewer.kt` (field focus), `App.kt` (actions), UI tests in `PageShortcutsUiTest`.

## Dependencies
T049, T050.

## Decisions
(filled in while building)

## Progress
Not started.
