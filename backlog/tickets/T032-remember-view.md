# T032 Remember last page and zoom per project in the app settings

**Type:** task · **Status:** ready for development · **Story:** S04 · **Task:** S04.7

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
None yet.

## Progress
Not started.
