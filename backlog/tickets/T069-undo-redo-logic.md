# T069 Undo and redo in the editor, also on another page

**Type:** task · **Status:** done · **Story:** S08 · **Task:** S08.3

## What needs doing
- **`BookEditor.undo()` and `redo()`** (extensions in `Undo.kt`, like `LineNavigation.kt`): take the top step of the undo stack (redo stack), apply its changes in reverse (forwards) to the pages, and move the step to the other stack. Each returns an **`UndoResult`**: nothing to do, or done with the page the change is on and the `Focus` for the cursor, or the step did not fit (see Decisions). One step may change several pages (a "replace all").
- A page that is not the one shown is taken from `changed` if it was changed in this session, otherwise read with `readPage`; the result goes into `changed` as for any edit (`changedPages`, `onUnsaved`), and the layers are stamped with the entry's number.
- The history gets the entry `UndoEntry(seq, step, changes)`, with the inverted changes of the step (applying an entry's changes forward is always what the entry does, T072), or `RedoEntry`. Both seal the open typing step first.
- **Where the cursor goes:** for a line that was changed in its text: the end of the changed part in the text that is there after the undo (after the restored text; after the inserted text on redo). For lines that were added, restored or removed by the step: the start of the first restored or inserted line, or, if lines were removed, the end of the line before the first removed one (the start of the one after it if there is none). For several changes: the first one.
- **Another page:** `undo` and `redo` say which page; the caller shows it. The editor keeps the cursor wish for that page (`pendingFocus`, like `arriving`: it belongs to its page and is carried out by `show` when the page's lines are there). On the page shown, `requestFocus` is used directly.
- `show(number)` prefers the layer in `changed` to the one it read, so an undo that arrives while a page is still being read is not overwritten.

## Acceptance criteria
1. Undo and redo of every kind of edit restore the page exactly (compare layers with `==`, also the boxes): typing, split, join, add line, delete line, on a page with lines and on one without regions.
2. Undo, undo, redo goes back to the state after the first undo; after a new edit the redo steps are gone.
3. With the last change on another page: `undo()` changes that page (also if it was never shown in this session), reports the page, and `show(page)` puts the cursor where the change was; the page shown before is not changed.
4. A step of two pages (made with `record`) is undone and redone on both pages.
5. The cursor ends where the rules above say, for typing, split (undo: the end of the first part), join, add and delete.
6. Undo and redo make the pages unsaved (`changedPages`, `onUnsaved`).
7. Nothing to undo or redo: `undo()` does nothing and says so. A step that does not fit the page is dropped and reported, and the next call works with the step below.

## Approach
`Undo.kt`, `BookEditor` (`pendingFocus`, `show`, `commit`). Tests: `UndoTest` with the generated layers of T067 and `BookEditor`; one test over several pages with a fake `readPage`.

## Dependencies
T067, T068.

## Decisions
- **Undo makes the inverted changes in the reverse order,** redo the changes as they are; a step's changes are applied in order, each relative to the state the one before left (S10 must make its steps that way). The entry of an undo holds exactly the changes that were applied, so applying an entry forward is what it did (T072).
- **Two kinds of failure.** A step that does not fit the pages (they were changed behind the editor's back) is dropped: it can be neither undone nor redone, and the next call works with the step below (`DoesNotFit`). A page that cannot be read leaves the step where it is (`Failed(reason)`), because the problem may pass. Nothing is changed in either case (all pages are worked out first, then put in place under the lock).
- **A page that is not in memory is read on the UI thread** (`readPage`, one small file, a few ms), so that undo is one step with no state in between where the user could type; it is not read twice (`changed`, then the page shown, then the project).
- **Where the cursor goes** is worked out from the change as it was made (`focusAfter`): a text change in one line: the end of what was typed or restored; lines joined: the junction; lines split or added: the start of the first new line after the first part; lines removed: the end of the line above (the start of the one below, then the first line of the page). Redo of a split lands where the split itself did (the start of the second part), redo of a join at the junction, as the edits themselves do.
- **`pendingFocus` is used once and belongs to its page,** like `arriving`: `show` drops one for another page, `applyArrival` carries it out when the lines are there and then forgets any wish of `arriveAt` for the page.
- **`show` prefers `changed[page]` to what it read,** so an undo that arrives while the page is still being read is not overwritten by the older text from the file.

## Progress
Done. `Undo.kt` (`undo`, `redo`, `focusAfter`), `UndoResult.kt`, `UndoHistory.peek/commit/discard`, `UndoEntry` and `RedoEntry`, `pendingFocus` in `BookEditor` and `applyArrival`. Tests: `UndoTest` (15; it also covers T068's criterion that a new edit clears the redo steps). Build, tests, ktlint and Detekt are green.
