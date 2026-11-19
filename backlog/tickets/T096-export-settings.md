# T096 Remember the export choices per format in the app settings

**Type:** task · **Status:** done · **Story:** S11 · **Task:** S11.1

## What needs doing
- The options of the last export are kept per format in `settings.json` under `export`: `{"text": {"pageMarkers": …, "header": …, "keepLineBreaks": …}, "markdown": {…}}`. Without an entry the defaults of the format apply (T093).
- `AppSettings.exportOptions(format)` and `rememberExportOptions(options)`; the file is written only when something changed. Reading is forgiving like the rest: a value that is not a boolean counts as missing, unknown fields stay.

## Acceptance criteria
1. First time: the defaults (all on for text, all off for Markdown).
2. After `rememberExportOptions`, a new `AppSettings` on the same file gives the same options; the other format is not affected.
3. A broken entry (wrong types, a string instead of an object) gives the defaults for what is broken and does not stop the app.
4. Fields this version does not know in the `export` object are kept when it is written again.
5. Remembering the same options again does not write the file.
6. The docs mention the new part of the settings file (the settings are not part of the project format): the class description of `AppSettings` and `docs/architecture.md`.

## Approach
`SettingsJson` (read and write of the `export` object) and `AppSettings`. Unit tests next to the existing settings tests.

## Dependencies
T093.

## Decisions
- Only the options are remembered, not the format that was chosen last: the story asks for the choices per format, and the format is the first thing the dialog asks.
- The functions are extensions in `ExportSettings.kt` (the class `AppSettings` has as many functions as Detekt allows), so `lock`, `current()` and `save()` of `AppSettings` are internal.

## Progress
Done. `ExportSettings.kt`, `ExportSettingsTest` (7 tests), kdoc of `AppSettings`, architecture overview. Build green.
