# T032 Remember last page and zoom per project in the app settings

**Type:** task · **Status:** done · **Story:** S04 · **Task:** S04.7

## What needs doing
- **App settings file** (not part of the project format): a JSON file `settings.json` in the user's config folder (`%APPDATA%\korrektor` on Windows, `~/Library/Application Support/korrektor` on macOS, `$XDG_CONFIG_HOME/korrektor` or `~/.config/korrektor` on Linux). Content: `{"version": 1, "projects": {"<absolute folder path>": {"page": 12, "zoom": {"mode": "fixed", "scale": 1.5}}}}`. The zoom is `fitWidth`, `fitPage` or `fixed` with the scale in screen pixels per point (a scale, not a percentage, so it does not depend on the display).
- **`AppSettings`** class (read, update one project's view, write with `AtomicFile`). A missing, empty, damaged or newer-version file counts as empty: the app starts and nothing else is lost; a damaged file is kept as `settings.json.bad`. Unknown fields are kept when writing. At most 200 projects are kept (the oldest are dropped).
- **Saving:** the view is saved when page or zoom change, after 1 s without further changes (debounced), when the project closes and when the window closes. A failed write is not an error for the user (written to stderr; the settings are a convenience).
- **Restoring:** opening a project (also right after creating it) restores page and zoom; a page beyond the page count falls back to the last page; no entry means page 1 with fit width. A project folder that moved is a new project for the settings (the key is the path; recorded as a limitation).
- The settings location can be replaced for tests (`AppModel` takes an `AppSettings`).

## Acceptance criteria
1. Closing and reopening a project restores page and zoom (fit width, fit page and a fixed scale, each tested).
2. Two projects keep their own values.
3. A missing, empty, damaged (not JSON, wrong types) or newer-version settings file lets the app start with defaults and does not crash; the damaged file is kept as `.bad`.
4. A page number beyond the page count is clamped; zero or negative becomes 1.
5. The settings are written atomically and the file is valid JSON after every write.
6. Unknown fields in the file survive a write.
7. The project folder contains no viewer setting: `project.json` and the page files are byte-identical before and after viewing.
8. Changes within one second cause one write, not many.

## Approach
- Unit tests with a temp settings folder and a test clock for the debounce; a lifecycle test through `AppModel` (open, go to page 5, change zoom, close, reopen).
- Docs: `docs/architecture.md` (settings), README ("the viewer remembers where you were").

## Dependencies
T027, T029.

## Decisions
- **`AppModel` owns the `ViewerState`** of the open project (made in `open`, restored from the settings), instead of `ProjectScreen`: the model must save the view when another project is opened and when the window closes, and a screen that is gone cannot. `ProjectScreen` now takes a slot for the viewer (Detekt allows five parameters) and `ProjectViewer` is the composable with the `PageSource` and its lifecycle (the test file `ProjectViewerTest` was renamed with it).
- **Settings in memory by default:** `AppModel(settings = AppSettings.inMemory())`; only `Main` gives it the real file. Otherwise every test that opens a project would write to the user's real settings.
- **Not valid is not there:** an entry with a page that is text, an unknown zoom mode, a fixed zoom without a positive scale, or no zoom is ignored for that project; the other projects stay. A file that is not JSON, not an object, has no integer `version`, or has `projects` that is not an object is **moved aside** as `settings.json.bad` (an older bad file is replaced) and the app starts with none. An empty or missing file is just empty.
- **A newer version is moved aside too** (not only ignored): writing over it would destroy what the newer app saved. The cost is that using two versions of the app in turn loses the settings of one of them each time; the settings are a convenience.
- **Only changed views are written:** `rememberView` compares the new entry with the old one and writes nothing if they are equal (a project that was only opened, or the same page again). `writeCount` makes this testable.
- **Order is the age:** an updated entry moves to the end of the object, so the first ones are the least recently used and are dropped beyond 200.
- **A fixed zoom is saved as its scale** at that moment (not the one the user last asked for), so what is restored is what was seen; it is clamped to what the page allows once the page is known. Fit modes are saved without a scale.
- **Saving:** `keepViewSaved` waits one second after the last change (`collectLatest` with a `delay`), `AppModel.saveView()` writes at once when another project is opened and from `Main`'s close request. A failed write only prints to stderr.
- **Moved projects** are new projects for the settings (the key is the absolute, normalized path of the folder). Recorded as a limitation.
- **The file location** (`AppSettings.defaultFile`) takes the system, home and environment as parameters so that all three systems are tested on any machine; an `XDG_CONFIG_HOME` that is not an absolute path is ignored, as the specification says.

## Progress
Done. `AppSettings`, `SettingsJson`, `SavedView` (`saved`, `restore`), `ViewPersistence`, `AppModel.viewer`, `ProjectViewer`, `Main` saves on close, README and architecture. Tests: 16 in `AppSettingsTest` (every zoom kind across a restart, two projects, the file's shape, valid JSON after every write, no rewrite for no change, memory-only settings, missing and empty files, damaged files moved aside and rewritten, newer versions kept as `.bad`, invalid entries ignored, a failed write is no error, unknown fields survive, a fit mode drops the scale, the limit of projects with the oldest dropped, the default limit, the file location per system) and 12 in `ViewRestoreTest` (all modes saved and restored, a fixed scale before the window is known, a page beyond the book, fit modes without a scale, the whole flow through two `AppModel`s, a never-viewed project, switching projects saves the first, two projects, a shorter project, the project folder unchanged byte for byte, one write for a burst of changes, no write for a project only opened). `./gradlew build` is green.

Not checked by me (story report): that closing the real window saves the view and that the real settings file appears in `%APPDATA%\korrektor`.
