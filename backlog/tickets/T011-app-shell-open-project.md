# T011 App shell: start screen, open a project, window title

**Type:** task · **Status:** ready for development · **Story:** S02 · **Task:** S02.5

The first real screen of the app: it shows a start page, opens an existing project with a folder dialog, and shows the project title in the window title. It also sets the UI toolkit choice for the app.

## What needs doing
- Add Material 3 for Compose Multiplatform (`org.jetbrains.compose.material3:material3`, the version matching Compose 1.12.x) and the Compose UI test dependency for tests.
- `AppModel` (`AppModel.kt`): the state of the app, plain Kotlin with Compose state: `project: Project?`, `error: String?`, `openProject(folder: Path)`, `dismissError()`. A failed open sets `error` and leaves the current project as it is.
- `windowTitle(project: Project?)`: `<title> – korrektor` when a project with a title is open, the folder name (without `.korrektur`) if the project has no metadata, and `korrektor` otherwise.
- Start screen (`StartScreen.kt`): the app name, a short text, the buttons "New project…" (wired in T012) and "Open project…", and the error message in a dismissible banner.
- "Open project…" shows a folder chooser (Swing `JFileChooser` in directories-only mode, since the AWT file dialog cannot choose folders on Windows) behind an interface `FolderPicker`, so tests can replace it.
- `Main.kt` uses `AppModel` and the window title.
- README "Using korrektor": how to open a project.

## Acceptance criteria
1. "Open project…" lets me choose a project folder; the project opens and the window title shows its title.
2. A folder that is not a project (no `project.json`, wrong format, newer version) shows a clear error and changes nothing; a project that was already open stays open.
3. Cancelling the dialog does nothing.
4. A project without metadata shows its folder name as the title.
5. The error banner can be dismissed.
6. The app still starts (`./gradlew run`), and the start screen shows the app name.

## Approach
- Unit tests for `AppModel` and `windowTitle` with real project folders in a temp directory.
- One Compose UI test (`runComposeUiTest`) of the core flow: start screen → "Open project…" with a fake `FolderPicker` returning a project folder → the project's title is displayed; and a bad folder → the error is displayed.
- The dialog itself and the look are listed under "how to try it" in the story report.
- Update `docs/architecture.md` (package `korrektor`: `AppModel`, screens).

## Dependencies
T009 (title from metadata).

## Decisions
- **Material 3** for the UI components (text fields, buttons, dialogs): the text field of the foundation library alone is too bare for forms. The editor view of S05 builds on foundation components and doesn't depend on this choice.
- State lives in plain classes (`AppModel`, later one model per dialog) instead of a view-model framework, because there is only one window and no navigation stack yet.
- The project is opened without a "recent projects" list (out of scope for S02).

## Progress
Not started.
