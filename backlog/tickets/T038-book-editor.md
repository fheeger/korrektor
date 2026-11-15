# T038 The editor state: corrected pages in memory

**Type:** task · **Status:** done · **Story:** S05 · **Task:** S05.3

The corrected text of the pages the user has looked at or changed lives in memory in a `BookEditor` that both panels share. Nothing is written to the project yet: saving is autosave (S07). This ticket makes the state, the loading and the page switch; the panels come later.

## What needs doing
- **`BookEditor(project, io)`** in `korrektor.editor`, one per open project (made by `AppModel` next to the `ViewerState`):
  - `layer`: the corrected layer of the page shown, as Compose state (null while it is being loaded), and `lines`: its lines in reading order (regions by `order`).
  - `show(page)`: makes [page] the shown one. A page that was changed in this session comes from memory, any other is read from the project (`readPage`) on the IO dispatcher. Showing a page cancels the loading of the one before. A page that cannot be read gives an error message (`error`) instead of a layer; the panels say so.
  - **Edits** `setLineText`, `splitLine`, `joinWithPrevious`, `joinWithNext`, `addLineAfter`, `deleteLine` apply `PageEdits` (T037) to the shown layer and store the result. A page with changes stays in memory when the user goes to another page and comes back (`changedPages`, the set S07 will write).
  - **The active line** (`activeLineId`, state): the line the cursor is in; `activate(lineId)`. It is reset to none when the page changes, and when its line is deleted it moves to a neighbour. A `focusRequest` (line id and character index) lets the editor ask the text panel to put the cursor somewhere (after a split, a join, a click on the scan).
  - The original OCR layer is never touched; the editor never writes a file.
- **Page switch:** one place decides the page: `ViewerState.page`. The screen calls `editor.show(viewer.page)` when it changes, so both panels show the same page.
- **Memory:** only pages that were changed are kept (a layer is a few tens of KB); a shown page that was not changed is dropped when the user leaves it.
- `docs/architecture.md`, README (a note that corrections are not saved yet, until S07).

## Acceptance criteria
1. After `show(n)` the layer is the project's corrected layer of page n; `lines` are in reading order.
2. An edit changes the shown layer and marks the page changed; going to another page and back shows the edited layer, not the file's; a page that was only looked at is not in `changedPages`.
3. No file of the project is written or changed by showing pages and editing (byte comparison of the whole project folder).
4. Showing another page while one is loading ends the first load; the result of a cancelled load never replaces the page shown (a test with a slow read).
5. An unreadable page file gives an error and no layer; the editor is not stuck and the next page loads.
6. `activeLineId` is none after a page change; deleting the active line moves it to the previous line (the next if it was the first); a `focusRequest` is made by split, join, add and delete.
7. A page without text (empty regions, a note) gives a layer with no lines, and adding a line creates region `r1`.

## Approach
- Unit tests with a real project made from a generated PDF (import with `PdfImport`), a slow read through an injected reader for the cancel test.

## Dependencies
T037.

## Decisions
- **The editor takes a page reader** (`(Int) -> PageLayer`, `Project::readPage` in the app) instead of the project, so tests can make a read slow or fail without a project; the ticket text said `BookEditor(project, io)`.
- **A page that was only looked at is not kept:** `changed` holds only pages with a real change (the layer after an edit is a different object); an edit that changes nothing (the same text, a join on the first line) is not a change and does not mark the page.
- **`show` clears the layer first** (so the panel never shows the old page under the new page number), and ignores the result of a read for another page than the one shown (the cancel of the caller also stops it: `withContext` rethrows at its end).
- **Focus requests have a serial number,** so asking twice for the same place moves the cursor twice (a click on the same line again, for example).
- **`requestFocus` also activates the line,** so after a split, a join, an add and a delete the active line is where the cursor goes; deleting the only line leaves no active line.
- **`AppModel.editor`** is made in `open` next to the viewer state; `App` follows `viewer.page` with an effect. For now nothing reads the editor except tests: the panels come with T041.
- **The README note** that corrections are not saved yet comes with T042, where editing becomes possible.

## Progress
Done. `BookEditor`, `FocusRequest`, `AppModel.editor`, the page effect in `App`. Tests (`BookEditorTest`, 16): a shown page is the project's corrected layer in reading order (also with regions stored in the wrong order), a page without text has its note and no lines, an edit marks the page and survives going away and back, looking is not changing, an edit that changes nothing is no change, nothing is written (the project folder byte for byte), a line added on a page without text creates `r1`, a read that was left for another page is never used (slow read), an unfinished load shows no layer, an unreadable page gives an error and the next page loads, the active line is none after a page change, an unknown line cannot become active, split, join, add and delete ask for the cursor where it belongs, every focus request is new, deleting the only line leaves no active line, and the app makes a fresh editor for each project. `./gradlew build` is green.
