# T043 Split, join, add and delete lines in the panel

**Type:** task · **Status:** done · **Story:** S05 · **Task:** S05.9

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
- **The keys are read in `onPreviewKeyEvent`** before the text field sees them, and only without Ctrl, Alt or Meta, so Ctrl+Backspace (delete a word) and the like stay with the field. Backspace in the middle of a line, and Delete not at the end, are left to the field (tested).
- **Enter with a selection** removes the selected text first (`setLineText`) and then splits at the start of the selection: the break replaces the selection, as in a text editor. That is two edits in the editor (and two entries for the undo of S08, which can group them).
- **Backspace at the start of the first line and Delete at the end of the last line are used up** (consumed) and change nothing; the editor reports no change, so the page is not marked.
- **"Add line"** adds below the active line, and at the end of the page if there is none; **"Delete line"** removes the active line and is off without one. Both are off while the page is not there (loading, error). On a page without lines "Add line" makes region `r1` with the first line (T037).
- **The marker** is a dot in the number column with the tag `no box <id>`; it says that the line has no place on the scan. It follows the data: a line gets or loses it as soon as its box changes (the second half of a split inside a word has no box; both halves of a split between words have one).
- **The buttons are above the list,** not in the rows; keyboard shortcuts for them are S06.

## Progress
Done. `lineKey`, `LineCommands`, the marker in `TextPanel`. Tests (`LineOperationsUiTest`, 15): Enter in the middle (two halves, no origin on the new line, the cursor at its start, the active line), Enter at the end (an empty line without a box), Enter with a selection, Backspace at the start (join, cursor at the junction), Backspace on the first line (nothing, no change), Backspace in the middle (left to the field), Delete at the end (join, cursor stays), Delete on the last line (nothing), "Add line", "Delete line" (cursor to the previous line, off without an active line), "Add line" on a page without lines, the commands are off while the page is not there, the marker for lines without a box (also for the half of a split), a row that was not changed keeps its focus and cursor when another line is edited, and the layer that was shown before an edit is not changed. The tests fail when Enter is not handled (checked by breaking it). `./gradlew build` is green.
