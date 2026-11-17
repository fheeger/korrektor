# T072 History saved together with the pages, and recovery after a crash

**Type:** task · **Status:** ready for development · **Story:** S08 · **Task:** S08.4

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
Written down while working.

## Progress
Not started.
