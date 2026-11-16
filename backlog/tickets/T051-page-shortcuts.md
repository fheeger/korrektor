# T051 Page shortcuts: next, previous and go to page

**Type:** task · **Status:** done · **Story:** S06 · **Task:** S06.3

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
- **How the field is reached:** `ViewerState.focusPageField()` counts a request (`pageFieldRequests`) and the field's effect takes the focus and selects its content when the count changes. The viewer state is the place for such view requests (like `showWordBoxes`); no new holder was needed. The field now holds a `TextFieldValue` instead of a `String`, to select its content.
- **How the page typed gets to the editor:** `ScanLinks` got two functions, `onGoToPage` (the page typed; the viewer goes there itself if there is none) and `onLeavePageField` (Escape). `App` connects them to `ProjectKeys.goToPage` and `ProjectKeys.leavePageField`. The decision of ticket T051's text ("`ScanLinks` or a small new holder") went to `ScanLinks`, because the field already gets it.
- **Enter with the number of the page shown** puts the cursor on its first line (nothing else changes), so Ctrl+G, Enter is a way back to the text.
- **Escape** puts the page's own number back and the cursor back in the line it was in (the first line if none was active).
- **Ctrl+Up/Down use the arrival of T050 (`FIRST`):** the cursor is on the first line of the new page in both directions, as the story says for the page shortcuts. From a page without lines Ctrl+Up goes to the previous page.
- A wrong number (no number, 0, beyond the last page, empty) is undone and the field keeps the focus, as before.

## Progress
Done. `ProjectKeys` (next/previous page, Ctrl+G, `goToPage`, `leavePageField`), `ViewerState.focusPageField`, the page field in `PageViewer`, `ScanLinks`. Tests: `PageShortcutsUiTest` (9, the whole window with real keys: both directions, the ends, leaving an empty page, Ctrl+G focus and selection, Enter, wrong numbers, Enter with the current page, Escape, the shortcuts from the field). The existing page-field tests of S04 pass unchanged.
