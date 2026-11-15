# T042 Edit the text of a line

**Type:** task · **Status:** ready for development · **Story:** S05 · **Task:** S05.3

## What needs doing
- The rows of the text panel become text fields (one line each, no wrapping). Typing, deleting, pasting and moving the cursor work as in any text field. Each change calls `editor.setLineText(lineId, text)` (T038, T037), so the corrected layer in memory always has what the user sees.
- **The cursor and selection** belong to the row (local state); the text belongs to the editor. A row follows the editor's text if it changes from outside (a join, a split, later an undo): the row's state is keyed on the line's text version, not only on its id.
- **The active line** is the row that has the focus: gaining focus calls `editor.activate`. When `editor.focusRequest` names a line and an index, that row gets the focus and the cursor goes to the index (used by T043 and T045).
- Pasting multi-line text puts it into the one line without line breaks (the line breaks become spaces); Enter is left to T043.
- **Nothing is written** to the project (S07 does that). The original OCR layer is never changed.
- The text field has no spell checking and no auto-correction of any kind; what is typed is stored.

## Acceptance criteria
1. Typing in a row changes `editor.layer`'s line text at once and marks the page changed; the other lines are unchanged.
2. The characters typed are stored as typed, including `ſ`, `ꝛ`, `uͤ` and two spaces (UI test with text input).
3. Changing a line to the same number of words keeps its word boxes; a different number drops them (T037 rule, seen through the editor).
4. Going to another page and back shows the edited text; the project folder is byte for byte unchanged after editing (the original and the corrected files).
5. The focused row is the active line; a `focusRequest` puts the cursor at the asked place.
6. Pasted text with line breaks becomes one line.

## Approach
- `BasicTextField` with a `TextFieldValue` per row and a `VisualTransformation.None`; the `onValueChange` strips `\\n` and `\\r` to spaces.
- UI tests with `performTextInput`, `performTextReplacement`, focus checks; one test with the real project and a byte comparison.

## Dependencies
T041.

## Decisions
None yet.

## Progress
Not started.
