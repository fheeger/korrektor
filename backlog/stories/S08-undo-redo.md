# S08 Undo/redo

**Features:** F1 · **Status:** in progress

As a reader of historical sources, I want to undo and redo my edits, even after reopening the project
so that I can fix mistakes in my corrections without worry.

S08 covers every change to the text (S05, S06), to page status (S09) and from find and replace (S10). The edit history panel (F4), comparing against the original OCR (F2) and version control (F3) come later.

## Acceptance criteria
- Ctrl+Z undoes, and Ctrl+Y or Ctrl+Shift+Z redoes. Both appear in the menus and in the shortcut list (S06.6).
- There is one history for the whole book. If the last change was on another page, undo opens that page and puts the cursor at the change.
- One undo step is:
  - typing, grouped by word or by a short pause, not by single character
  - splitting, joining, adding or deleting a line
  - one change of page status
  - one "replace all" across the book (S10)
- Making a new change after undoing clears the redo steps.
- Undo still works after the project is closed and reopened, and after a crash. The history is saved together with the changes, so the two always match after a crash (S07).
- Undo and redo respond instantly. Opening a project doesn't require reading the whole history, even in a 1000-page book after months of work (N1).

## Scope note
Page status (S09) and "replace all" (S10) do not exist yet. S08 builds the step model so that they plug in: a step holds a list of changes, over several pages, and a change is a sealed type that S09 extends with the status (S09.5 "Hook into … undo (S08)"). The first two kinds of step are proved with the line changes of S05/S06, and a step over several pages is tested with `BookEditor.record`.

## Tasks
- [ ] S08.1 Define the change model: what one step is, and how typing is grouped → [T067](../tickets/T067-change-model.md)
- [ ] S08.2 Record steps for line edits, line structure changes and page status → [T068](../tickets/T068-record-steps.md)
- [ ] S08.3 Undo and redo, including switching to the page of the change → [T069](../tickets/T069-undo-redo-logic.md), [T070](../tickets/T070-undo-keys-menu.md)
- [ ] S08.4 Store the history in the project and load it on demand → [T071](../tickets/T071-history-file.md), [T072](../tickets/T072-history-with-saves.md), [T073](../tickets/T073-load-history.md)
- [ ] S08.5 Tests: → [T074](../tickets/T074-history-tests.md)
  - Undo and redo across pages.
  - Undo after reopening the project.
  - Undo after a simulated crash.
  - Measure speed with a long history.
