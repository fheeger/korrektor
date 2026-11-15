# T041 The text panel shows the corrected lines

**Type:** task · **Status:** ready for development · **Story:** S05 · **Task:** S05.2

## What needs doing
- **`TextPanel(editor, modifier)`** in `korrektor.editor`: a scrolling list with one row per line of the shown page in reading order (`editor.lines`), the line number in a narrow column on the left, then the text of the line, in the font of T039. For now the rows are read only.
- **Text exactly as stored:** the text of a row is `line.text` as it is, with no normalization, no trimming, no wrapping; a long line scrolls sideways within its row rather than breaking into two (a row is a line of the book). Spaces stay as they are (double spaces too).
- **States:** "Loading…" while `editor.layer` is null; the error message if the page could not be read; for a page without lines the page's note (for example "No text on this page.") or "No lines on this page."
- **The active line** is marked (a stronger background); clicking a row makes it the active line (`editor.activate`). The list scrolls to keep the active line visible when it changes (for example by a click on the scan, T045).
- Stable keys: a row is identified by the line id, so rows are not mixed up when lines are added or removed later (T043).
- The panel replaces the placeholder of T040 and follows `viewer.page` through `editor.show`.

## Acceptance criteria
1. The panel shows the lines of the page in reading order, one row each, with their numbers; a page with several regions lists them by region order.
2. The text is shown exactly: `ſ ꝛ uͤ ß`, a trailing `-` or `=`, double spaces and a very long line are as stored (UI test with a layer made of such lines; the semantic text of a row equals `line.text`).
3. Both panels show the same page: going to another page in the viewer changes the rows (UI test); a page without text shows its note.
4. The loading state and the error state show; the panel never shows the lines of another page than the viewer.
5. Clicking a row makes it active and marks it; changing the active line from outside scrolls it into view (a page of 60 lines in a short window).
6. By eye on the sample: pages 2, 6 and 50 look as the text layer says (screenshot, story report).

## Approach
- UI tests with a fake `BookEditor` source (the editor takes its page reader as a function) and the real project for one end-to-end test.
- A `LazyColumn` with `key = line.id`; the number column has a fixed width computed from the largest number.

## Dependencies
T038, T039, T040.

## Decisions
None yet.

## Progress
Not started.
