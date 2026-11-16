# T052 Editing keys: Backspace on an empty line, and the full set by keyboard

**Type:** task · **Status:** done · **Story:** S06 · **Task:** S06.4

Enter (split), Backspace at the start (join with the line above) and Delete at the end (join with the line below) exist since S05 (T043). What the story adds is the empty line, and the check that all five behaviours hold together.

## What needs doing
- **Backspace on an empty line deletes it,** and the cursor goes to the end of the line above (that is what a join does already for a line that is not first). On an empty *first* line it deletes the line and the cursor goes to the start of the next one. If it is the only line of the page, it is deleted too and the panel shows the page's note (confirm while building and record it in Decisions).
- **Enter at the end of a line adds a new empty line** below with the cursor in it (a split at the end already does this; the test pins it down).
- Delete on an empty line is a join with the next one (unchanged).
- Backspace/Delete with a selection are left to the field (they delete the selection).
- All five behaviours are checked with real key events in UI tests. Ctrl+Backspace and Ctrl+Delete (word deletion) stay with the field.

## Acceptance criteria
1. Each of the five behaviours of the story works with key events in the row (UI test, one test per behaviour).
2. Backspace on an empty line deletes it and moves the cursor as described; the empty first line case too.
3. Backspace at the start of the first non-empty line does nothing (unchanged).
4. Ctrl+Backspace, Ctrl+Delete, and Backspace/Delete with a selection are not taken over.
5. Enter at the end of a line gives an empty line with a new id, no box, and the cursor in it.

## Approach
- `PageEdits.joinWithPrevious` (or a new `deleteEmptyLine`) and `lineKey` in `TextPanel.kt`. Unit tests in `PageEditsTest`, UI tests in `EditKeysUiTest`.

## Dependencies
T049 (the handler order: the row's keys first, then the shortcuts).

## Decisions
- **An empty line is deleted, not joined,** when Backspace is pressed at its start (`deleteLine` instead of `joinWithPrevious`). A join would have been right for a line above, but an erased line may still carry a box, which the join would add to the line above, and an empty first line has nothing to join to. `deleteLine` already puts the cursor at the end of the line above, or at the start of the next one for the first line.
- **The only line of a page is deleted too,** and the page then shows its note (or "No lines on this page."). Add line / Enter-free ways to get a line back: the "Add line" button. The text panel takes the focus when the page has no lines (the row that had it is gone), so the shortcuts keep working (tested).
- **Delete on an empty line** joins the next line into it (unchanged): the line keeps its id, the next one goes.
- **Enter at the end** adds an empty line with a new id and no box, the cursor in it (already so since T043; now pinned down by a test).
- **Ctrl+Backspace, Ctrl+Delete, and Backspace/Delete with a selection** are not matched by the line keys (they ignore Ctrl/Alt/Meta and a non-empty selection) and stay with the field (tested).

## Progress
Done. `lineKey` in `TextPanel.kt`, and the panel's focus after the last line is deleted. Tests: `EditKeysUiTest` (11, real key events: Backspace on an empty middle, first, last and only line, Backspace on a line with text, Delete on an empty line, Enter at the end and in the middle, selections, Ctrl+Backspace/Ctrl+Delete). The tests of T043 (`LineOperationsUiTest`) pass unchanged.
