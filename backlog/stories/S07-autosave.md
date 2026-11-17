# S07 Autosave

**Features:** N4 · **Status:** in review

As a reader of historical sources, I want my corrections to be saved automatically
so that I never lose work, even if the app crashes.

S07 decides when and how changes from S05, S06 and later S09 (page status) are saved, and what happens when saving fails. It builds on the crash-safe writing from S01 (S01.6). The undo history belongs to S08.

## Acceptance criteria
- There is no save step you have to remember, and you're never asked to save when closing.
- Changes are saved after 1 s without typing. During continuous typing they're saved at least every 10 s. They're also saved when you switch pages, close the project or close the app.
- If the app or computer crashes, you lose at most the last 10 s of typing. The project always opens afterwards, in its last saved state.
- Only pages that have changed are saved. The original PDF and the original OCR layer are never written to (N4).
- Saving runs in the background and never interrupts typing, even in a 1000-page book (N1).
- A small indicator shows "Saved", "Saving…" or "Not saved".
- When saving fails (disk full, folder gone, no write permission):
  - You see a clear warning.
  - Your changes stay in memory and saving is retried.
  - If you close the app while saving keeps failing, you're warned first and offered the chance to save the project to a different location.
- Ctrl+S does nothing and shows no message, so pressing it out of habit doesn't disrupt your work.
- The same project can't be open twice at the same time, whether in two app windows or two instances. A second attempt gets a clear message. A lock left behind by a crash doesn't block opening the project.

## Tasks
- [x] S07.1 Keep track of which pages have unsaved changes → [T057](../tickets/T057-unsaved-pages.md)
- [x] S07.2 Save triggers: 1 s pause in typing, at least every 10 s, page switch, closing → [T059](../tickets/T059-save-triggers.md), [T062](../tickets/T062-closing-and-switching.md)
- [x] S07.3 Save in the background, using the crash-safe writing from S01.6 → [T058](../tickets/T058-page-saver.md)
- [x] S07.4 Save status indicator → [T061](../tickets/T061-save-indicator.md)
- [x] S07.5 Handle failed saves: warning, retry, warning when closing, saving to another location → [T060](../tickets/T060-failed-saves.md), [T062](../tickets/T062-closing-and-switching.md)
- [x] S07.6 Catch Ctrl+S and ignore it silently → [T063](../tickets/T063-ctrl-s.md)
- [x] S07.7 Lock the project so it can't be opened twice, and clean up a lock left behind by a crash → [T064](../tickets/T064-project-lock.md)
- [x] S07.8 Tests: → [T065](../tickets/T065-autosave-tests.md)
  - Kill the app while typing and while saving, then check that the project opens and at most the last 10 s are lost.
  - Simulate failed saves.
  - Measure saving speed in a 1000-page book.
- [x] S07.9 Fixes from the code review → [T066](../tickets/T066-review-fixes.md)

## Report

### What was built
- **Tracking** (T057): `BookEditor` knows which pages differ from what is on disk (`changedPages`, `unsaved()`, `markSaved` by layer identity, so an edit made during a save stays unsaved).
- **The saver** (T058, T059): `AutoSaver` writes only the changed pages, in the background on the IO dispatcher, one save at a time. It saves 1 s after the last change, at least every 10 s during continuous typing, and at once on a page switch, on closing and on switching projects.
- **Failed saves** (T060): the changes stay in memory, a red banner names the reason, and saving is retried every 5 s; the warning goes by itself when saving works.
- **Indicator** (T061): "Saved", "Saving…" or "Not saved" in the text panel.
- **Ctrl+S** (T063) is swallowed and does nothing.
- **Project lock** (T064): an operating system file lock on `<project>/.lock`. A second window or instance gets a clear message. A file left behind by a crash blocks nothing, because the lock is the system's, not the file's.
- **Closing and switching** (T062): closing saves first without asking. If saving fails, a dialog offers "Save to another location…" (copies the project, writes the unsaved pages into the copy, opens the copy), "Keep the app open" or "Close without saving". Switching projects saves first and does not switch if that fails.
- **Tests** (T065): kills of a child JVM while typing and while saving, a folder that goes away and comes back, a window session from typing to reopening with the originals compared byte for byte, and speed in a book of 1000 pages.
- **Review fixes** (T066): see below.

### Decisions
- No "is the lock stale?" check: an OS lock is released by the system when the process ends, however it ends. The `.lock` file is never deleted.
- The retry interval is fixed (5 s); `Ctrl+S` is not in the shortcut table, so it is not listed as a shortcut.
- The timer runs on `Dispatchers.Default`, not on the UI thread (review finding): the UI thread may wait for a save (project switch).
- `saveElsewhere` does not exit the app: it opens the copy and shows a notice with its path.

### How each criterion was checked
- **No save step, saved at the right moments:** `SaveTriggersTest` (virtual time: 1 s, 10 s cap, flush) and `SaveTriggersUiTest` (real window: pause, page switch); `CloseTest` for closing and switching.
- **At most the last 10 s lost, the project always opens:** `CrashTest` kills a real child JVM. Typing kills lost 0.56 to 2.6 s; a 12 s run of continuous typing lost about 2 s (the 10 s cap works). 8 of 10 kills while saving 40 pages left a `.tmp` file, i.e. landed mid-write; the project opened every time, every page decoded and held one whole state.
- **Only changed pages, originals untouched:** `AutosavePerformanceTest` (999 of 1000 pages not written) and `AutosaveFlowTest` (all files except the corrected layers byte for byte equal after a session).
- **Background, never interrupts typing, 1000 pages:** save of 1 page 7 to 9 ms, of 50 pages about 50 ms; typing latency 95th percentile 21 µs without a save, 33 µs during a save of 500 pages.
- **Indicator, warning, retry:** `SaveStatusUiTest`, `SaveWarningUiTest`, `SaverFailureTest`, `AutosaveFlowTest` (folder moved away and back).
- **Closing while saving fails:** `CloseTest`, `SaveFailedDialogTest`, `AutosaveFlowTest`.
- **Ctrl+S:** `CtrlSTest`.
- **Lock:** `ProjectLockTest` (same JVM, child JVM, killed child), `AppModelLockTest`.
- Kover: the new files are covered 90 to 100 % of their lines (`AutoSaver` 77/78, `SaveUi` 20/20, `SaveIndicator` 10/10, `ProjectLock` 25/26, `ProjectClosing` 56/60, `ProjectCopy` 12/16).

### Code review (Opus 5.5, whole story)
Eight findings; seven fixed in T066 (#77): the window could freeze when another project was opened during a save; edits typed during the closing save were lost; one failing page held back the pages after it; save-to-another-location could run twice or into itself; a failed second lock attempt could release the first lock on Linux/macOS; a failed open could leak the lock. The freeze test was checked to hang without the fix.

### Known gaps
- After `closeProject` the old saver's loop may run until the next recomposition and could retry into the old folder for a moment (only in "save elsewhere"). A `close()` on the saver would fix it.
- `flushBeforeSwitch` still uses `runBlocking` on the UI thread: a slow disk freezes the window for the length of the save when switching projects.
- "Saving…" has no minimum display time, so it may flash.
- The long crash run (10 kills each) and the speed tests only run with `KORREKTOR_PERF_TESTS=1`; CI runs 3 kills each.
- Not tried by hand: the real window close button, the real folder chooser in the failure dialog, Ctrl+S on a real keyboard, two real app instances with the native lock, and how the red banner and the indicator look.
- Open since S06: should Up/Down skip pages without lines (e.g. sample page 3)? They stop there now.

### How to try it
Open a project, type, and watch the indicator: "Not saved", then "Saved" after a second. Close the window right after typing: it closes without asking, and the text is there after reopening. Press Ctrl+S: nothing happens. Start a second instance on the same project: it says the project is open already. To see a failure, make the project folder read-only (or move its `pages` folder away), type, and look at the red banner; then close the app and try "Save to another location…".
