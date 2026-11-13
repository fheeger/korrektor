# Architecture

An overview of the code structure. Every ticket that changes the structure updates this file.

## Packages
- `korrektor`: the app entry point (`Main.kt`).
- `korrektor.format`: the project format (S01), with no UI and no PDF code. The specification is [project-format.md](project-format.md).
  - `Model.kt`: the data model of a page layer: `PageLayer` → `Region` → `Line` → `Word`, with `Box` and `RegionType`. Original OCR and corrected text use the same types. Unknown fields are kept in `extra`.
  - `AtomicFile.kt`: crash-safe replacement of a file (temp file, flush, atomic rename) and cleanup of leftover temp files. Every file the project writes goes through it, except the append-only history.
  - `CanonicalJson.kt`: the shared writing rules (separators, key order, number format, string escapes) and parsing with `FormatException`.
  - `PageCodec.kt`: reads and writes a `PageLayer` as the text of a page file, one book line per file line.
  - `Model.kt` also holds `Metadata` (title, author, year, source as typed).
  - `Project.kt`: a project folder. `Project.create` and `Project.open`, `project.json` (format marker, version, page list, unknown fields kept), read-only `source.pdf` copy, page files through `PageCodec` and `AtomicFile`; original OCR layers are written once.
  - `HistoryLog.kt`: the append-only undo history of one page (JSON Lines), read from the end, tolerant of a torn last line.
