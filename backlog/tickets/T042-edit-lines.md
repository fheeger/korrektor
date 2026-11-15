# T042 Edit the text of a line

**Type:** task · **Status:** done · **Story:** S05 · **Task:** S05.3

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
- **The row's field is a `BasicTextField`** with the font of the panel (single line, so it scrolls itself sideways for a long line); the Material text field is too high for a row. The line number is a separate text; clicking it puts the focus in the field.
- **The row follows the editor:** if `line.text` differs from the field's text during composition, the field takes the editor's text and keeps the cursor if it still fits. A typed change updates the field and the editor in the same step, so the two agree again at the next composition. All texts round-trip exactly through the words of a line (`split(' ')` and back), so no change can disagree with its own result.
- **Focus requests** go through a map of wishes in the list (`line id` to character index): the list scrolls to the row, the row, once composed, sets its cursor, asks for the focus and removes the wish. A wish for a row that is not composed yet waits until it is. An index beyond the end goes to the end.
- **Skipping:** the rows get one `RowActions` object and a small `RowState` (number, active, wish) instead of lambdas made per row, so a row whose line is the same object is not recomposed when another line is typed in (the editor copies only the changed line).
- **The tests of T041** read the text through `EditableText` now; they pass otherwise unchanged.
- **Undo** inside a field (Ctrl+Z) is not provided by `BasicTextField` with a `TextFieldValue` and is S08's business.
- **README:** says plainly that corrections are lost when the project is closed (until S07).

## Progress
Done. Editable rows in `TextPanel`, the wishes for the cursor, the README note. Tests (`EditLinesTest`, 10): typing changes the line in the editor at once and nothing else, the characters are stored as typed (`ſ ꝛ uͤ`, double and surrounding spaces, `=`, `-`, the empty text), the same number of words keeps the word boxes and another number drops them (the line keeps its box), pasted line breaks become spaces, a row follows the editor when its text changes from outside (a join), the row with the focus is the active line, a focus request puts the cursor where asked (also beyond the end), an added line gets the cursor, going to another page and back shows the edit, and editing in the whole app leaves the project folder byte for byte as it was. `./gradlew build` is green.
