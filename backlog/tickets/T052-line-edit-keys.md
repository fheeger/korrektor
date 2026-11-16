# T052 Editing keys: Backspace on an empty line, and the full set by keyboard

**Type:** task · **Status:** ready for development · **Story:** S06 · **Task:** S06.4

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
(filled in while building)

## Progress
Not started.
