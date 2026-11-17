# T076 Page status in the format

**Type:** task · **Status:** done · **Story:** S09 · **Task:** S09.1

## What needs doing
- **`PageStatus`** (format): an open set like `RegionType`: `UNCHECKED` and `CORRECTED`; an unknown name from a later version is kept as it is.
- **`PageInfo.status`** (default unchecked), stored in the `pages` entries of `project.json` as `"status": "corrected"`. An unchecked page has no field, so a new or imported project is unchanged. All statuses are in one small file, so progress needs no page file (N1).
- **`Project.setStatuses(all)`** replaces the statuses of the pages (a map page → status, missing = unchecked) and writes `project.json`; if writing fails the old ones stay (like `setPages`). `Project.statuses` gives the map.
- **`StatusChange(page, from, to)`**, a case of the sealed `Change` (S08), and its entry in the history codec (`{"type": "status", "page": 12, "from": "unchecked", "to": "corrected"}`).
- **Docs:** `docs/project-format.md` (field, an example in the tested project block; the new change type in the history section).

## Acceptance criteria
1. A project with statuses is written and read back with the same statuses; an unchecked page writes no field; unknown fields in a page entry are still kept.
2. A status that is not a non-blank string is a `FormatException` naming the entry.
3. An unknown status name survives read and write.
4. `setStatuses` writes `project.json` only (page files untouched); on a failed write the old statuses stay.
5. A `StatusChange` round-trips through the history codec; `inverted()` swaps `from` and `to`.
6. The examples in the docs are tested (`SpecExamplesTest`).

## Approach
`Project.kt` (`PageStatus`, `PageInfo`), `ProjectFile.kt`, `Change.kt`, `HistoryCodec.kt`, docs. Unit tests on real temp folders: `ProjectStatusTest`, additions to `HistoryCodecTest`, `SpecExamplesTest`.

## Dependencies
None.

## Decisions
- **Status lives in `project.json`, not in the page files:** the progress of a 1000-page book is then one file read. Cost: a status change rewrites `project.json` (about 30 bytes a page); it is rare, and the write is atomic like all of them.
- **No default written:** unchecked is the absence of the field.
- **Open set:** later statuses ("needs attention", C5) need no format change.

## Progress
Done. `PageStatus`, `PageInfo.status`, `Project.setStatuses`/`statuses`, `StatusChange` and its codec, docs. Tests: `ProjectStatusTest` (8), additions to `HistoryCodecTest`, `SpecExamplesTest` (the examples now have a status and a status entry). Build green.
