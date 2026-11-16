# T066 Fixes from the S07 code review

**Type:** task · **Status:** done · **Story:** S07 · **Task:** S07.9

The end-of-story review (Opus 5.5, whole story) found one serious problem, three medium ones and four small ones. Seven are fixed here; one is a known gap.

## What needs doing
1. **The window could freeze (high).** The save timer (`AutoSaver.run`) ran on the UI thread. Opening another project waits there for a running save (`flushBeforeSwitch`, `runBlocking`), and the running save had to come back to the UI thread to release its lock. Both waited for each other. The timer now runs on `Dispatchers.Default` (`KeepSaving`).
2. **Edits typed while the closing save runs were lost (medium).** `requestClose` now saves again while something is unsaved (at most 5 rounds) before it exits.
3. **One failing page blocked the pages after it (medium).** `AutoSaver.writeAll` goes on with the other pages and reports the first failure, so a crash costs those pages no more than 10 s.
4. **Save to another location twice (medium).** A second click while the copy is made is ignored (`AppModel.copying`).
5. **Saving into the project itself (low).** A parent folder inside the project folder is refused with a clear message.
6. **A failed second lock attempt in the same process (low, Linux/macOS).** `ProjectLock` remembers the folders it holds, so a second channel is never opened (closing it could release the first lock on POSIX systems).
7. **A leaked lock (low).** `openProject` gives the lock back for any failure, not only `FormatException`.

## Acceptance criteria
1. A test that runs the timer through `KeepSaving` and opens another project from the UI thread while a save is held at a gate finishes (and hangs without the fix; checked).
2. An edit made during the closing save is on disk after `requestClose`.
3. With page 2 failing, pages 1 and 3 are written and only page 2 stays unsaved.
4. Saving into the project folder is refused, and a second save-elsewhere is ignored while one runs.
5. Repeated failed `acquire` calls in one process keep the first lock.

## Decisions
- **Known gap (review point 7):** `closeProject` gives the lock back at once, while the old saver's loop may still be running until the next recomposition. In "save elsewhere" this could let the old saver retry into the old folder for a moment. The window is small and the data is the same data; not changed. A `close()` on the saver would fix it.
- `freeFolderIn` still checks "does not exist" and `copyTo` creates the folder later. With the click guard (point 4) only another program could take the name in between; the clean-up after a failure is kept.

## Progress
Done. Tests: `CloseTest` (3 new + the freeze test), `SaverFailureTest`, `ProjectLockTest`.
