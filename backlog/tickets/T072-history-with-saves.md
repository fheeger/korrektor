# T072 History saved together with the pages, and recovery after a crash

**Type:** task · **Status:** done · **Story:** S08 · **Task:** S08.4

The history and the pages must agree after a crash (S07).

## What needs doing
- **The order of a save** (`AutoSaver.saveLocked`): (1) take the snapshot of the unsaved layers and `drainPending()` together, under the editor's lock, so the entries are exactly those the layers contain; (2) append the entries to `history/book.jsonl` (`appendAll`, flushed); (3) write the pages; (4) if all pages were written, append `{"type": "saved", "upTo": n}`. If appending the entries fails, they go back to the front of the pending list and the save fails like a failed page (warning, retry). If a page fails, the entries stay in the file and the next save writes the page again and then the `saved` entry. The saver gets the history file through a constructor parameter (a seam for tests, like `write`).
- **Recovery** (`HistoryRecovery.recover(project)` in `korrektor.format`, run when a project is opened, under the lock, before anything else is read; a failure is the usual "cannot open" message): read the history from the end back to the last `saved` entry. For every entry after it (oldest first) and each of its changes: if the page file's `history` is lower than the entry's `seq`, apply the change to the page (`apply`, T067; a change that does not fit is skipped), and set the page's `history`. Write every page that was changed once, then append `saved`. So a crash between the history and a page file leaves history and pages agreeing at the next opening, and a page file never has changes that the history lacks.
- **Save elsewhere** (S07.8): the pending entries are appended to the copy's history together with the unsaved pages and a `saved` entry, so the numbers in the copy's pages never run ahead of its history.
- The pending entries of "close without saving" are dropped with the rest of the unsaved changes.

## Acceptance criteria
1. After a save the history file ends with the entries of the session and a `saved` entry; every changed page file has the `history` of its last entry; a save without changes writes nothing (also no `saved` entry).
2. A save that fails to append the entries keeps them pending and fails like a failed page; the retry writes them once (no entry twice).
3. A page that fails to write stays unsaved; its entries are in the file without a `saved` after them; after the retry there is one.
4. Typing during a save is not delayed (the history append happens on the IO dispatcher like the page writes), and entries of edits made during a save are not in the file before their pages are in the snapshot of a later save.
5. Recovery, with a project whose process was stopped (simulated by cutting the sequence): after the append of the entries before any page (all pages old), after the first of two pages, after the last page but before `saved`, and with a torn last line: the pages hold all changes of the entries that are complete, nothing twice, and the history file ends with `saved`.
6. A project without a history file, and one whose last entry is `saved`, open without changes to any file (byte compare).
7. Save elsewhere: the copy opens with its history and pages agreeing; the changes unsaved in the old folder are in the copy and can be undone there.

## Approach
`AutoSaver`, `BookEditor.snapshot()` (layers and entries under one lock), `HistoryRecovery.kt`, `AppModel.open` (recovery first), `ProjectClosing.copyWithChanges`. Tests: `AutoSaverTest`, `SaverFailureTest`, new `HistoryRecoveryTest` (a fake step sequence cut at each point), `CloseTest`.

## Dependencies
T068, T071.

## Decisions
- **`HistoryRecovery` is in `korrektor.editor`, not `korrektor.format`** (the ticket said format): it needs `apply` of the change model, which lives with the editor, and the format package has no editor code. It reads and writes only through `Project`, `HistoryLog` and `HistoryCodec`.
- **A step is made on a page completely or not at all,** and the test "does the page have this entry" uses the page's number as it was before the step, so a step with several changes on one page is not skipped after its first change.
- **`saved` means: every entry up to `upTo` is in the page files.** The saver keeps the newest entry that has no `saved` after it (`HistoryWriter.uncommitted`), so a failed page does not lose the marker: it is written by the save that finally writes the page. A `saved` entry that cannot be written fails the save (the page is already written, so only the marker is retried).
- **`appendAll` is all or nothing:** if the write fails half way, the file is cut back to where it was, so a retry never writes an entry twice.
- **Save elsewhere holds the saver idle** (`AutoSaver.whileIdle`), so no save adds to the history while it is copied; the layers and the pending entries are read together under the editor's lock, as for a save but without draining, so that a failed copy loses nothing. The first version of this had `saver?.whileIdle { … } ?: copy(…)`, which copied twice (the block returns null on success); caught by `CloseTest`.
- **The recovery runs before the old project is closed** (first in `AppModel.open`); if it fails the project is not opened and nothing changes.
- **Not done here:** the history is not loaded into the stacks (T073); the new session only continues the numbers (`firstSeq`).

## Progress
Done. `HistoryWriter`, `HistoryRecovery`, `AutoSaver` (history before pages, `saved` after, `whileIdle`), `BookEditor.snapshot()`, `UndoHistory.restore`, `HistoryLog.appendAll` (all or nothing), `Project.historyLog`, `AppModel.open` (recovery first, numbering continues), save elsewhere. Tests: `HistorySaveTest` (7), `HistoryRecoveryTest` (9: every crash point), `CloseTest` (history in the copy); `EditorFlowTest` and `AutosaveFlowTest` allow the history file. Build, tests, ktlint and Detekt are green.
