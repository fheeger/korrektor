# T062 Closing the app or the project: save first, and a way out when saving fails

**Type:** task · **Status:** done · **Story:** S07 · **Task:** S07.2 (closing), S07.5 (second part)

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
- **Closing never asks to save.** `requestClose()` (a suspend function in `ProjectClosing.kt`, called from `Main`'s `onCloseRequest` in a coroutine) saves with `saveNow()`, which also waits for a save that is running, and then calls `AppModel.exit` (`exitApplication` in `Main`; tests record the call). With nothing unsaved nothing is written. A project that is being created is not interrupted (`canClose`), and a second close request while the first is saving is ignored.
- **If saving fails the app stays open,** and `AppModel.closeProblem` holds the message ("The changes of N pages could not be saved: <reason>"); the dialog shows while it is set. The three buttons: **Close without saving** (exit, the changes are lost), **Keep the app open** (the dialog goes, the changes stay in memory and saving is retried, T060), **Save to another location…**.
- **Save to another location copies the project and opens the copy, but does not close the app.** The ticket text said "then the window closes"; a window that disappears right after a recovery gives no chance to see where the work went. Instead the copy becomes the open project with a notice ("Your changes were saved in a copy of the project: <folder>. The copy is open now."), and the user closes the app again, which now works. The copy is `<chosen folder>/<name>.korrektur`, or `<name> (copy).korrektur`, `(copy 2)`, … if the name is taken.
- **`Project.copyTo`** (an extension in `format/ProjectCopy.kt`; `Project` has as many functions as the code checks allow) copies every file but the lock and `*.tmp`, each with the crash-safe copy of `AtomicFile`; it refuses an existing target or a project without `source.pdf`, and removes what it made if it fails. Then the unsaved pages are written into the copy (from the snapshot `unsaved()`), and if that fails the copy is removed again and the dialog says so. If the project folder itself is gone or unreadable, the copy fails with that message and the changes stay in memory: there is nothing to build a project from (the PDF is in the project folder). That case is told to the user instead of guessed around.
- **Opening or creating another project first saves the open one** (`flushBeforeSwitch`, blocking the UI thread for the few milliseconds a save of a few pages takes). If that fails, the other project is not opened and `error` says why; the changes are kept.
- **The dialog content is a separate composable** (`SaveFailedContent`) so that it can be tested without a window.
- **Not tested here:** the real window-close event (Main) and a real folder chooser; the flow from `requestClose` to the dialog buttons is tested through the model and the dialog content.

## Progress
Done. `ProjectClosing.kt` (`closeProject`, `requestClose`, `flushBeforeSwitch`, `saveElsewhere`), `format/ProjectCopy.kt` (`Project.copyTo`), `SaveFailedDialog.kt`, `AppModel` (`closeProblem`, `exit`, flush in `openProject`/`openCreated`), `Main` (the close request), README and `docs/architecture.md`. Tests: `CloseTest` (13: closing saves first without asking and exits, nothing unsaved writes nothing, no project, failure stays open with the reason, a second request is ignored, creating blocks, opening another project saves first and does not open it when saving fails, saving elsewhere with the change, a taken name, a project without its PDF), `CopyToTest` (5), `SaveFailedDialogTest` (4: the three ways out and a cancelled chooser).
