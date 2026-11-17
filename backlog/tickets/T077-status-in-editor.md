# T077 Page status in the editor: recording, undo, saving, recovery

**Type:** task · **Status:** ready for development · **Story:** S09 · **Task:** S09.5

## What needs doing
- **`BookEditor` holds the statuses** (Compose state, from `Project.statuses` when the project is opened): `statusOf(page)`, `setStatus(page, status)`. A change is one step of the history (`StatusChange`, entry `edit`); no change if the status is the same.
- **Undo and redo** make `StatusChange`s too (`Undo.kt`): a change fits if the page has the status `from`; undo opens the page of the change.
- **Saving (S07):** the statuses are unsaved after a change (`statusesChanged`); the snapshot of a save has them with the entries; `AutoSaver` writes them with `writeStatuses` (`Project.setStatuses`) after the pages and before the `saved` entry. The save indicator, the close dialog and the close loop count them as unsaved.
- **Recovery:** after a crash, `HistoryRecovery` applies the newest `to` of every page's `StatusChange` among the entries after the last `saved`, then writes `project.json` if it differs. Save elsewhere gets the same through the recovery of the copy.
- `Project.pages` is `@Volatile`: the saver replaces it on the I/O thread.

## Acceptance criteria
1. `setStatus` changes the status and is one undo step; undo restores it and opens its page; redo brings it back; a new step clears the redo.
2. A status change and line edits on different pages undo in order.
3. A status change is saved by autosave, also when nothing else changed; the indicator says "Not saved" until `project.json` is written.
4. If writing `project.json` fails, the save fails (error, retry), the status stays in memory, and `saved` is not written.
5. After a crash between the history entry and `project.json`, opening the project has the status the history says (every crash point is tested).
6. A project saved elsewhere with an unsaved status change has the status in the copy.

## Approach
New `PageStatuses.kt` (extensions: `setStatus`, `statusOf`, unsaved handling), changes in `BookEditor`, `Undo.kt`, `UnsavedPages.kt`, `AutoSaver.kt`, `HistoryRecovery.kt`, `AppModel.kt`, `ProjectClosing.kt`. Tests like those of S08: `PageStatusTest` (recording, undo, redo), `StatusSaveTest` (saver, failure), `StatusRecoveryTest` (crash points), a case in `CloseTest`.

## Dependencies
T076.

## Decisions
- **Replaying a status change after a crash sets the newest `to`:** a status is a value, not a position in a list, so replay is idempotent and needs no stamp in `project.json`.
- **The whole status map is written, not the difference:** one small file; the saver compares the map by identity like the layers.
- **A status change does not touch page layers or their `history` stamp.**

## Progress
