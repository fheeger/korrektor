# S08 Undo/redo

**Features:** F1 · **Status:** done

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
- [x] S08.1 Define the change model: what one step is, and how typing is grouped → [T067](../tickets/T067-change-model.md)
- [x] S08.2 Record steps for line edits, line structure changes and page status → [T068](../tickets/T068-record-steps.md)
- [x] S08.3 Undo and redo, including switching to the page of the change → [T069](../tickets/T069-undo-redo-logic.md), [T070](../tickets/T070-undo-keys-menu.md)
- [x] S08.4 Store the history in the project and load it on demand → [T071](../tickets/T071-history-file.md), [T072](../tickets/T072-history-with-saves.md), [T073](../tickets/T073-load-history.md)
- [x] S08.5 Tests: → [T074](../tickets/T074-history-tests.md)
  - Undo and redo across pages.
  - Undo after reopening the project.
  - Undo after a simulated crash.
  - Measure speed with a long history.
- [x] S08.6 Fixes from the code review → [T075](../tickets/T075-review-fixes.md)

## Report

### What was built
- **Change model** (T067): a step is a list of `LineChange`s (in a region, the lines `removed` at `index` are replaced by `inserted`); `diff` and `apply` on page layers, `inverted()`. Typing is grouped (`TypingGroups`): one place of one line, no pause of a second, a space after a word starts the next step, a paste or a replaced selection is a step of its own.
- **Recording** (T068): `UndoHistory` (undo and redo stacks, pending journal entries), every edit of the editor becomes a step; `oneStep { }` (Enter with a selection), `recordStep` (steps over several pages, for S10). Each layer carries `history`, the number of its last entry.
- **Undo and redo** (T069, T070): `BookEditor.undo()/redo()` work on any page, also one never shown; the cursor goes to the place of the change (on another page when it is shown). Ctrl+Z, Ctrl+Y, Ctrl+Shift+Z, new Edit menu, F1 list, README.
- **The history file** (T071): `history/book.jsonl`, one history for the whole book (edit, undo, redo, saved entries), lines written like in a page file; page files get the optional `history` number; specified in `docs/project-format.md` with a tested example.
- **Saved together with the pages** (T072): a save appends its entries, writes the pages, appends `saved`. `HistoryRecovery` runs on opening and makes the entries after the last `saved` on the pages that lack them. Save elsewhere copies the history with the pending entries.
- **Loaded on demand** (T073): opening reads only the end of the file (newest 100 steps and the redo steps); older steps are read when undo needs them.
- **Tests** (T074) and **review fixes** (T075).

### Decisions
- One history file for the whole book (`history/book.jsonl`) instead of one per page as first sketched in the format, because undo across pages needs one order. No format version change (a new file and an optional page field).
- The history is a journal: undo and redo are entries too, with the changes they made, so every entry is applied forward as it is. Which steps can be undone or redone is worked out from the latest entry that mentions a step.
- Order of a save: history, then pages, then `saved`; recovery decides per page by its `history` number. A step is made on a page completely or not at all; a change that does not fit is skipped, never forced.
- A saved step cannot be changed (append-only), so continuous typing across a save starts a new step.
- A damaged history entry stops the opening; a history that cannot be *repaired* does not (notice). Undo that reaches a damaged older part says so.
- **Page status (S09.5) and "replace all" (S10)** are not here: the model takes them (sealed `Change`, steps over several pages, tested with `recordStep`), their hooks are those tasks' work. The acceptance line "one change of page status is one step" is therefore met by S09.5.
- "Close without saving" after a failed save does not throw the changes away completely: their entries are already in the history file, so they come back at the next opening (and can be undone).

### How each criterion was checked
- **Ctrl+Z / Ctrl+Y / Ctrl+Shift+Z, menus, list:** `ShortcutsTest`, `MenusTest`, `ShortcutListTest`, `UndoUiTest` (real key events in the whole window).
- **One history, undo opens the page of the change:** `UndoTest`, `UndoFlowTest` (every page compared at every step), `UndoUiTest` (three pages in the window).
- **What one step is:** `TypingGroupsTest`, `RecordingTest`, `StepsTest` (every edit of `PageEdits` round trips exactly).
- **New change clears redo:** `UndoTest`.
- **After reopening and after a crash:** `HistoryReopenTest`, `UndoFlowTest`, `HistoryRecoveryTest` (every crash point), `HistoryCrashTest` (a real child JVM killed again and again; history and pages are compared exactly against a replay of the history; 12 kills locally, each had 40 to 200 entries after the last `saved`; 3 kills in CI), `HistorySampleTest` (the real sample: 50 corrections on 10 pages, saved, reopened, undone in 50 steps; all 165 pages equal to the import).
- **Fast, opening does not read the whole history:** `UndoHistoryLoadTest` (100,000 steps read in under 1 % of the bytes) and `HistoryPerformanceTest` (1000 pages, 100,000 steps, 33 MB): opening 58 ms, first undo 5.3 ms, 100 undos p95 0.37 ms (max 2.1 ms), older history read on demand max 8.2 ms, typing p95 0.045 ms.
- Kover: the new files are covered 98 to 100 % of their lines; not covered: the notice texts in `ProjectKeys` (does not fit / cannot be read), the failure branches of `HistoryLog`, and two lines in `HistoryWriter`.

### Code review (Opus 5.5, whole story)
No high finding. Two medium and one low fixed in T075: Ctrl+Z could throw when reading a damaged older part of the history; a project could not be opened when a failed page write had left entries in the history; the half-made layer inside `oneStep` could be saved without its entry.

### Known gaps
- Redo state can differ between session and reopening in two corner cases (a typed-and-deleted step after an undo; a step dropped because it does not fit). Nothing is damaged.
- If only the `saved` entry cannot be written, the close dialog says "0 pages".
- `canUndo` stays true while older entries exist, even if all were undone for good; a history whose newest steps were nearly all undone is read back further when opening.
- Reading older history, and the recovery, happen on the UI thread (a few ms measured).
- The performance test and the long crash run only run with `KORREKTOR_PERF_TESTS=1`.
- Not tried by hand: the real window and menu bar, Ctrl+Z on a real keyboard (also against the text field's own undo), and how the cursor looks after an undo on another page.

### How to try it
Open a project, type a few words in a line (each new word is its own undo step), press Ctrl+Z and Ctrl+Y (or Ctrl+Shift+Z). Split a line with Enter, delete one, add one, and undo them. Correct something on page 3, go to page 5, press Ctrl+Z: page 3 opens with the cursor at the change. Close the app, open the project again, and press Ctrl+Z: the last session's changes are taken back. Look at the Edit menu: entries are greyed out when there is nothing to do. To see the crash recovery, kill the app (task manager) while typing, reopen, and undo.
