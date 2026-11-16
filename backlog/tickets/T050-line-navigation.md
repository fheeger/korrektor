# T050 Move between lines, also into the next and previous page

**Type:** task · **Status:** done · **Story:** S06 · **Task:** S06.2

## What needs doing
- **`BookEditor` navigation:** `moveLine(delta)` (Up = -1, Down = +1), `firstLine()`, `lastLine()`. Each activates the line and asks the panel to put the cursor into it (`requestFocus`), at the same character index as before where the target is long enough, otherwise at its end. The highlight on the scan, the scrolling of the list and the panning of the scan already follow the active line (S05).
- **Across pages:** Down on the last line asks for the next page, Up on the first line for the previous one. A page change is the viewer's (`ViewerState.goTo`), so the editor reports it through a callback (`onPageChange(delta, arrival)`) that `App` connects to the viewer. After the new page is loaded the cursor is on its first line (coming from above) or on its last line (coming from below): `BookEditor.show` takes an *arrival* (`FIRST`, `LAST`, `NONE`), which it applies when the layer is there.
- On the first page Up on the first line, and on the last page Down on the last line, do nothing.
- A page without lines (T022): it is a stop. The cursor cannot be put anywhere, so the next Down/Up on that page moves nothing further (no line is active); Ctrl+Down/Up (T051) leave it. Confirm while building and record it in Decisions.
- No line is active (after a page change by the mouse): Down activates the first line, Up the last.
- Up/Down in a row are keys of the handler (T049). In a single-line field they do nothing else. Shift+Up/Down are left to the field.

## Acceptance criteria
1. Down/Up move the active line by one, and the cursor keeps its character index where possible. Ctrl+Home/Ctrl+End go to the first and last line of the page (model tests).
2. Down on the last line opens the next page with the cursor on its first line. Up on the first line opens the previous page with the cursor on its last line. On the ends of the book nothing happens (model tests and a UI test of the whole window).
3. The highlight on the scan follows each move, also across a page change: no highlight of the old page is drawn over the new picture (UI test).
4. With no active line Down goes to the first, Up to the last line.
5. A page without lines does not break the movement: the cursor stays where it is and nothing throws.

## Approach
- `BookEditor` (logic, unit tests in `BookEditorTest`), the wiring in `App.kt`, UI tests in a new `LineNavigationUiTest` with the fake page source of the existing tests.

## Dependencies
T049.

## Decisions
- **The editor decides, a small class acts:** `BookEditor.moveLine(delta)` returns what it came to (`MOVED`, `NEXT_PAGE`, `PREVIOUS_PAGE`, `NOTHING`) and puts the cursor itself when it moved. The page change is the viewer's, so `ProjectKeys` (new, implements `KeyActions`, takes the `AppModel`) asks the viewer for the page and tells the editor beforehand where the cursor goes on arrival (`arriveAt(page, FIRST|LAST)`). This replaces the callback `onPageChange` the ticket text suggested: the editor does not know the book's length, and a callback would have been the same thing with more wiring.
- **An arrival belongs to its page and is used once:** `arriveAt(3, FIRST)` only applies when page 3 is shown, so a page change by the mouse in between, or a read that is cancelled by the next key, cannot put the cursor somewhere unexpected. Works for pages read from the project and for pages kept in memory.
- **The column:** the rows report the cursor's character index to `BookEditor.caret` (a plain field, not state: nothing draws it). Up/Down, Ctrl+Home and Ctrl+End ask for that index in the target line; the row cuts it to the line's length. On arrival on a new page the cursor is at the start of the line.
- **A page without lines is a stop** (as the ticket proposed): Down/Up/Ctrl+Home/Ctrl+End do nothing there. It is left with the page shortcuts (T051), the toolbar or the mouse.
- **The text panel takes the focus when the page changes** (the finding of T049): the row that had it is gone. The panel's root is focusable and asks for the focus at every page change, before the page is read; a row takes it when the cursor arrives. So the shortcuts also work while a page is being read and on a page without lines (tested).
- **Down with no active line** goes to the first line, Up to the last. Shift+Up/Down are not matched, so they stay with the field.

## Progress
Done. `BookEditor` (`Arrival`, `LineMove`, `moveLine`, `firstLine`, `lastLine`, `arriveAt`, `caret`), `ProjectKeys`, the focus of the text panel, `App` uses `ProjectKeys` unless a test passes its own `KeyActions`. Tests: `LineNavigationTest` (9, the model) and `LineNavigationUiTest` (7, the whole window with real keys: Down/Up with the highlight, Ctrl+Home/End, the column, Down on the last line to the next page, Up on the first line to the previous page, the ends of the book, a page without lines; they fail when the action is removed, checked).
