# T062 Closing the app or the project: save first, and a way out when saving fails

**Type:** task · **Status:** ready for development · **Story:** S07 · **Task:** S07.2 (closing), S07.5 (second part)

## What needs doing
- **Closing the window** (`onCloseRequest` in `Main`) saves first: `AppModel.requestClose(onDone)`:
  - nothing unsaved and nothing being written: close at once, no question;
  - otherwise flush (`AutoSaver.flush()`, suspend, writes everything now and waits for a running save) and close when it worked. The user is never asked whether to save.
  - if saving fails: do not close. A dialog says what happened ("The changes of N pages could not be saved: <reason>") with three buttons:
    - **Save to another location…**: choose a folder; the whole project is copied there (`Project.copyTo`, below), the unsaved pages are written into the copy, and the copy becomes the open project. Then the window closes if it was asked to.
    - **Close without saving**: closes, the unsaved changes are lost.
    - **Keep the app open** (also Escape): the dialog goes, saving keeps being retried.
- **`Project.copyTo(newFolder)`**: copies `project.json`, `source.pdf`, `pages/` and `history/` (everything but `.lock` and `*.tmp`) with the crash-safe writing for each file; nothing is left behind if it fails; the source project is untouched. If the source is no longer readable (the folder is gone) the copy fails with a clear message and the dialog stays open.
- **Opening or creating another project** while changes are unsaved flushes first (as closing). If that fails, the other project is not opened and the error says why; the changes stay in memory.
- The window cannot be closed while the flush runs? It can be asked again; a second close request during a flush is ignored.
- `AppModel.canClose` (today: not while creating) stays.

## Acceptance criteria
1. Closing with unsaved changes writes them before the close callback runs (the callback sees the page files up to date), without a question.
2. Closing with nothing unsaved does not write and does not ask.
3. A close request with a failing writer does not call the callback and shows the dialog; each of the three buttons does what it says (UI test per button; "Close without saving" calls the callback, "Keep the app open" does not).
4. "Save to another location…" with a folder chooser fake: the copy opens with all pages, the corrected pages including the unsaved ones, and has no `.lock`/`.tmp`; the old folder has the old state; the open project is now the copy; later edits are saved there.
5. `Project.copyTo` of the sample-like test project is complete and the copy opens; it fails cleanly (nothing left) if the target exists or the source is missing.
6. Opening another project with unsaved changes saves them first; with a failing save it does not open it.

## Approach
`AppModel` (`requestClose`, flush before open), `Main.kt`, a dialog `SaveFailedDialog.kt`, `Project.copyTo` in the format package, `docs/project-format.md` (nothing new in the format; the copy is a convenience). Tests: `CloseTest` (model, fake writer, fake folder picker), `CopyToTest`, a UI test of the dialog.

## Dependencies
T058, T060, T064 (the copy has to skip the lock file; if T064 is not done yet the file is simply not there).

## Decisions
(filled in while building)

## Progress
Not started.
