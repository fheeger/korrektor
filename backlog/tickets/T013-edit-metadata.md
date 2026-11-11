# T013 Edit the metadata of an open project

**Type:** task · **Status:** ready for development · **Story:** S02 · **Task:** S02.4

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

## Progress
Not started.
