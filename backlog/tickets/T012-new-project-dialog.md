# T012 "New project" dialog

**Type:** task · **Status:** in progress · **Story:** S02 · **Task:** S02.2

The dialog that creates a project: choose a PDF, fill in the metadata, choose where to store it, create.

## What needs doing
- `NewProjectModel` (`NewProjectModel.kt`): the state of the dialog: `pdf`, `title`, `author`, `year`, `source`, `location`, `busy`, `error`, and:
  - `selectPdf(path)`: sets `pdf`; sets the title to `NewProject.suggestTitle(path)` unless the user already typed a title; sets `location` to the PDF's folder unless the user already chose one.
  - `selectLocation(path)`.
  - `canCreate`: a PDF is chosen and the title is not blank.
  - `projectFolder`: the folder that will be created (`location / folderName(title)`), shown to the user.
  - `create()`: runs `NewProject.create` on a background thread (the PDF can be large), with `busy` set; on success the new project becomes the open one in `AppModel`; on failure `error` shows the message and nothing is left behind.
- `NewProjectDialog` (`NewProjectDialog.kt`, Material 3): "Choose PDF…" first (the other fields are shown but disabled until a PDF is chosen), then title (required), author, year, source (all free text), location with a "Change…" button, the resulting folder, "Create" and "Cancel", a progress indicator while busy, and the error text.
- The PDF chooser is an AWT `FileDialog` with a `*.pdf` filter, the location chooser is the `FolderPicker` of T011; both behind interfaces so tests can replace them.
- The "New project…" button of the start screen opens the dialog.
- README "Using korrektor": how to create a project.

## Acceptance criteria
1. "New project…" starts with choosing a PDF; without one, "Create" is disabled.
2. After choosing a PDF the title is suggested from the file name and can be edited; the location defaults to the PDF's folder and can be changed.
3. "Create" is disabled while the title is blank.
4. Year and source accept any text (`ca. 1790`, `1785?`, a URL).
5. Creating makes the project folder with the copy of the PDF and the metadata; the project is then open and the window title shows its title.
6. If creating fails (project folder exists, location not writable, PDF vanished), the dialog stays open with a clear message, and nothing half-created is left behind.
7. The dialog cannot be closed or started twice while creating (buttons disabled while busy).
8. A title typed before choosing the PDF is not overwritten by the suggestion.

## Approach
- Unit tests for `NewProjectModel` (suggestion rules, `canCreate`, success, failure, busy state) with real files in a temp directory and fake pickers.
- One Compose UI test of the core flow: open the dialog, choose a PDF through a fake picker, edit the title, create, and see the project title; and the failure message when the folder exists.
- Not checked automatically (listed for the user): real file dialogs, look and layout, creating a project from the 165-page sample.
- Update `docs/architecture.md`.

## Dependencies
T010, T011.

## Decisions
- The location defaults to the folder of the PDF: no extra setting is needed and the project lands next to its source. It can be changed in the dialog.
- Title, author, year and source are on one page: there are only four fields.
- Creating runs on a background thread; the UI stays responsive for large PDFs.

- **The in-window `Dialog` of Compose** (not a separate window), so it works in the UI tests and stays on top of the app. Closing it by clicking outside or pressing Escape is ignored while creating.
- **A suggestion never overwrites what the user decided:** a typed title (or a chosen location) stays when a PDF is chosen. A suggested title follows a second PDF, and a cleared title gets a new suggestion. An error disappears as soon as the user changes something.
- **Creating needs a location too:** `canCreate` also requires a location, which is always set once a PDF is chosen.
- **"New project…" is also on the project screen** (next to "Open another project…"), so a second project can be created without restarting. The ticket only named the start screen.
- **Time-limited waits in the UI tests:** creation runs on a real background thread, so the tests wait for the result (up to 10 s) instead of assuming it is instant.
- **The dialog model uses coroutines directly** (`kotlinx-coroutines`, already on the classpath through Compose): a scope from the dialog and a dispatcher for the copy. Tests use a dispatcher they control to look at the busy state.

## Progress
Code done; `./gradlew build` green (14 new tests in `NewProjectModelTest`, 5 new Compose UI tests for the dialog in `AppUiTest`; Detekt and ktlint clean). Checked for real: `./gradlew run` opened a window titled `korrektor` with no errors in the log. Not checked (for the story report, "how to try it"): the real PDF file dialog and the Swing folder dialog, how the dialog looks and fits, and creating a project from the 165-page sample PDF. Docs updated: README, `docs/architecture.md`. Next: PR into `story/S02-create-project`; when CI is green, squash-merge, set this ticket to done, then start T013.
