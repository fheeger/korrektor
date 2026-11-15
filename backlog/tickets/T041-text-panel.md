# T041 The text panel shows the corrected lines

**Type:** task · **Status:** done · **Story:** S05 · **Task:** S05.2

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
- **The rows have test tags** (`line row <id>`, `line text <id>`); a clickable row merges the semantics of its children, so tests read the text through the unmerged tree.
- **A long line is one row that scrolls sideways** (`horizontalScroll`, `softWrap = false`, `maxLines = 1`), not wrapped: a row is a line of the book, and wrapping would break the one-to-one picture between rows and lines that clicking on the scan (T045) relies on.
- **The list follows the active line** only if it is not in view (`scrollToItem(index - 1)`, so that the line above is visible too); a line in view does not move the list.
- **Row numbers** are the position in reading order (1, 2, …), not the line ids, which are stable names and not numbers; the column is as wide as the largest number needs.
- **The selected state** of a row is exposed for accessibility and tests (`selected`), besides the stronger background colour.
- **What the screenshots show** (pages 2, 6 and 50 of the Abhandlung, in the whole window): the rows are the lines of the scan in order. On page 2 the last line is a CJK character the OCR read from a handwritten note under the imprint; it is drawn from a system font (Junicode has no glyph for it). On page 6 a few words are run together in the text layer itself (`seinenOf`, `seinemFürsten`, `unsereSchuldigkeit`, `derFriede`): the OCR text pieces had no space between them in the PDF, which S03 imports as they are (V1); they are corrections for the user to make. The stray `ı` at the end of line 7 is the speck of page 6.
- The loading and error messages are in the panel, not in a dialog.

## Progress
Done. `TextPanel`, the panel in the window instead of the placeholder. Tests (`TextPanelTest`, 9): one row per line in reading order with their numbers (regions stored in the wrong order), the text exactly as stored (`ſ ꝛ uͤ`, `=`, a trailing `-`, double and surrounding spaces, a 600 character line), loading and then the lines, an error instead of the lines, a page without lines shows its note (or "No lines on this page."), going to another page changes the rows, a click makes the row active and marks it, the list scrolls to an active line that is out of view (80 lines in a short panel), and in the whole app the panel shows the lines of the page the viewer is on and changes with it. `./gradlew build` is green.
