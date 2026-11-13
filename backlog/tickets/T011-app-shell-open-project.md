# T011 App shell: start screen, open a project, window title

**Type:** task · **Status:** done · **Story:** S02 · **Task:** S02.5

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

- **Material 3 version 1.9.0, declared directly.** It is the newest stable release; Compose Multiplatform 1.12 pairs with alpha and beta Material releases only. The Gradle accessor `compose.material3` is deprecated and gives the same version. It compiles and runs with Compose 1.12.2. A later upgrade is a one-line change.
- **UI test library:** `org.jetbrains.compose.ui:ui-test` with the `v2` `runComposeUiTest` (the older one is deprecated). The tests run without a window.
- **An open project gets a placeholder `ProjectScreen`** (title, author, year, source, folder, "Open another project…"). The ticket only asks for the window title, but a project that shows nothing would be confusing, and the UI test needs something to check. T013 adds "Edit metadata" there.
- **"New project…" is shown but disabled** until T012 wires it (`StartScreen` enables it when given a callback).
- **ktlint is told that `@Composable` functions are PascalCase** (`ktlint_function_naming_ignore_when_annotated_with` in `.editorconfig`), like the Detekt override from T015.
- **The error banner sits above both screens** (in `App`), so a failed "Open another project…" also shows its error while the old project stays open.

## Progress
Done: PR #15 merged, CI green. Code done; `./gradlew build` green (14 new tests: 9 in `AppModelTest`, 5 Compose UI tests in `AppUiTest`; Detekt and ktlint clean). Checked for real: `./gradlew run` opened a window titled `korrektor` with no errors in the log. Not checked (for the story report, "how to try it"): the Swing folder dialog itself, and how the screens look. Docs updated: README, `docs/architecture.md`. Next: PR into `story/S02-create-project`; when CI is green, squash-merge, set this ticket to done, then start T012.
