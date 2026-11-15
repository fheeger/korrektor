# T043 Split, join, add and delete lines in the panel

**Type:** task · **Status:** ready for development · **Story:** S05 · **Task:** S05.9

## What needs doing
The user-facing side of T037: how lines are split, joined, added and deleted in the text panel.
- **Enter** in a row splits the line at the cursor (`editor.splitLine`); the cursor goes to the start of the new second line. Enter at the end of a line therefore adds an empty line below, Enter at the start pushes the whole text down.
- **Backspace at the start of a row** (cursor at 0, nothing selected) joins the line with the previous one (`joinWithPrevious`); the cursor goes to the junction. **Delete at the end of a row** joins the next line into this one (`joinWithNext`), the cursor stays. At the first line (Backspace) and the last line (Delete) nothing happens.
- **Add and delete as commands:** two buttons above the panel, "Add line" (a new empty line below the active line, the cursor in it) and "Delete line" (removes the active line; disabled with no active line). On a page without lines "Add line" creates the first one.
- **A line without a box** is shown like the others (it only has no place on the scan); a small marker in the number column tells which lines have no box (a dot), so the user knows that they will not be highlighted.
- After each operation the active line and the cursor are where the story says (above); the list scrolls to keep it in view.
- Keyboard shortcuts for these commands (S06) and undo (S08) are not part of this ticket.

## Acceptance criteria
1. Enter in the middle of a line gives two lines whose texts are the two halves, the cursor at the start of the second; at the end it adds an empty line; the new line has no box and no `origin`.
2. Backspace at the start of line n joins it with line n-1: the text is appended without a separator, the cursor is at the junction; on the first line nothing changes. Delete at the end does the same with the next line; on the last line nothing changes.
3. "Add line" adds an empty line below the active line (or the first line on a page without lines) and puts the cursor in it; "Delete line" removes the active line and the cursor goes to the previous one (the next if it was the first); with no active line it is disabled.
4. Lines without a box have the marker; lines with a box do not.
5. Rows keep their identity: after a split or join the other rows keep their text fields (focus and cursor of a row that was not changed stay; UI test).
6. Nothing is written to the project; the original layer is unchanged.

## Approach
- `onPreviewKeyEvent` on the text field for Enter, Backspace and Delete with the cursor positions; the commands call `BookEditor` and set a `focusRequest`.
- UI tests drive the keys with `performKeyInput` and check the layer and the focus.

## Dependencies
T042.

## Decisions
None yet.

## Progress
Not started.
