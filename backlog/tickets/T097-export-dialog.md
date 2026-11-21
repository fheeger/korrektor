# T097 The export dialog: File menu, Ctrl+E, format, range, options, file, note about unchecked pages

**Type:** task · **Status:** done · **Story:** S11 · **Task:** S11.1, S11.5

## What needs doing
- A new menu "File" with "Export…" (Ctrl+E, in the shortcut table so that menu, keys and the F1 list agree), and an "Export…" button in the project header.
- The dialog: the format (plain text or Markdown), the page range (from, to; both start as the whole book, checked for numbers in 1..pages and from ≤ to), the three options as checkboxes (starting with the remembered choices of the format; switching the format shows the choices of that format), the note "N of M pages are still unchecked" when there are some in the range, and "Export…", which asks for the file (a save dialog, suggested name: the title with `.txt` or `.md`) and then runs the export of T095 with a progress bar and "Cancel".
- When the export is done the dialog closes and a notice says what was written and where; on an error the dialog stays open and shows it. The choices are remembered when an export starts.
- The save dialog is a seam (`ExportPicker`) like the other pickers.

## Acceptance criteria
1. Ctrl+E, the menu and the button open the dialog; without a project the menu entry is disabled.
2. The defaults are right for the first export of each format; later the last choices of that format.
3. A wrong range disables the export button and says why.
4. The note shows the number of unchecked pages in the chosen range, using the statuses in the editor (also those not saved yet); with all pages corrected there is no note.
5. While exporting the UI works, the controls that would change the choices are disabled, Cancel stops it and leaves no file.
6. After a successful export the dialog is closed and the notice names the file; after a failure the dialog shows the reason.
7. README: how to export, the options and the shortcut.

## Approach
`export/ExportModel.kt` (the state, no UI), `ExportDialog.kt` (Compose), `ExportPicker.kt`, an `EXPORT` action in `Shortcuts`, `Menus` and `ProjectKeys`, `AppModel.exportOpen`, `Dialogs`. Unit tests of the model, UI tests of the dialog with a fake picker.

## Dependencies
T093, T094, T095, T096.

## Decisions
- The save dialog is the AWT `FileDialog` in save mode: the system asks before it replaces a file. A name without the extension of the format gets it. `AppModel.exportPicker` holds it (a property, like `exit`), so that `App` and `Dialogs` did not need another parameter and tests put a fake in its place.
- The options of each format are kept in the dialog while it is open (switching to Markdown and back does not lose a choice), and remembered in the settings when an export starts, not when the dialog closes.
- The note is "N of M pages are still unchecked. They are exported as they are." For a part of the book it says "pages in the range". A page with a status this version does not know counts as checked, like in the jumps of S09.
- While an export runs, the choices are locked and the dialog cannot be closed except by "Cancel", which stops the export; the dialog stays open after a cancel.
- The suggested file name is the title with the same cleaning as the project folder name (`NewProject.folderName`), and the extension of the format.
- The page range is typed as two numbers; there is no "whole book" switch, because the fields start as the whole book.

## Progress
Done. `ExportModel.kt` (with `ExportBook`), `ExportDialog.kt` (also `AppModel.openExport/closeExport`), `ExportPicker.kt`, `AppAction.EXPORT` (Ctrl+E) in `Shortcuts`, `Menus` (a new File menu), `ProjectKeys`, the button in `ProjectScreen`, README, architecture overview. Tests: `ExportModelTest` (10), `ExportDialogUiTest` (7), `MenusTest` (+1, two adjusted for the File menu). Build green. Not checked by hand: the look of the dialog and the system save dialog (listed for the story report).
