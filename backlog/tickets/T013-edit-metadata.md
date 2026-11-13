# T013 Edit the metadata of an open project

**Type:** task · **Status:** done · **Story:** S02 · **Task:** S02.4

A dialog for changing title, author, year and source of the open project.

## What needs doing
- `EditMetadataModel` (`EditMetadataModel.kt`): state of the dialog, initialised from `Project.metadata` (empty fields for a project without metadata); `canSave` (title not blank, something changed); `save()` calls `Project.updateMetadata` and updates `AppModel`, so the window title changes; on failure `error` shows a message and the old metadata stays.
- `EditMetadataDialog` (Material 3): the same four fields as the new-project dialog (shared field composable), "Save" and "Cancel".
- A "Project" area on the screen shown while a project is open, with the title and an "Edit metadata…" button. (The editor screens of S04 and later will take this over; for now the screen shows the open project and this button.)
- README "Using korrektor": how to edit the metadata.

## Acceptance criteria
1. "Edit metadata…" is available while a project is open and shows the current values.
2. Saving changes the stored metadata and the window title; reopening the project shows the new values.
3. A blank title can't be saved.
4. "Cancel" changes nothing.
5. If saving fails (simulated), the old metadata remains in the file and in the app, and a message is shown.
6. Editing the title does not rename the project folder.

## Approach
- Unit tests for `EditMetadataModel` (values, `canSave`, save, failure) on real project folders.
- A Compose UI test: open a project, edit the title, save, see the new title.
- Update `docs/architecture.md`.

## Dependencies
T009, T011. Shares the field composable with T012, so it comes after it.

## Decisions
- The folder name stays as it is: renaming a folder the user may have moved or linked to is surprising, and the project does not depend on its folder name.

- **`AppModel` mirrors the metadata as Compose state.** `Project.metadata` is a plain property that the format package (no UI) changes, so the screens and the window title would not update after an edit. `AppModel.metadata` is the observable copy, set when a project opens and by `AppModel.updateMetadata`. `windowTitle` and `displayTitle` take it as an optional second argument.
- **The field composable is shared through an interface** (`MetadataForm` with `MetadataFields`) instead of a long parameter list, so both dialogs use the same four fields. To fit it, `NewProjectModel` got `updateAuthor`, `updateYear` and `updateSource` instead of public variables.
- **Unknown metadata fields are kept when saving** (`Metadata.copy`), in line with the format's rule that unknown fields survive.
- **Save needs a change:** unchanged values leave "Save" disabled. Values are saved as typed, without trimming (V1).
- **A write failure is an `IOException` from `AtomicFile`,** shown as "The metadata could not be saved: …". `Project.updateMetadata` (T009) already restores the old value; the dialog stays open, and a retry works. The tests make the write fail for real by putting a folder where the temporary file goes.
- **"Edit metadata…" sits in the button row of the project screen** next to "New project…" and "Open another project…". The three actions are passed as one `ProjectActions` object.
- **Saving runs on the UI thread:** it writes one small file. Creating a project, which copies a PDF, is the one that runs in the background.

## Progress
Done: PR #17 merged, CI green. Code done; `./gradlew build` green (9 new tests in `EditMetadataModelTest`, 4 new Compose UI tests in `AppUiTest`; Detekt and ktlint clean). Checked for real: `./gradlew run` opened a window titled `korrektor` with no errors in the log. Not checked (for the story report, "how to try it"): how the dialog looks, and editing a project made from the sample PDF. Docs updated: README, `docs/architecture.md`.
