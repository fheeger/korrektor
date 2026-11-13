# Architecture

An overview of the code structure. Every ticket that changes the structure updates this file.

## Packages
- `korrektor`: the app entry point (`Main.kt`) and app logic that has no UI.
  - `NewProject.kt`: the logic behind "New project": suggests a title from the PDF's file name, makes a safe folder name (`<title>.korrektur`), checks the PDF (a file with `%PDF-` near its start; pages are read at import, S03) and the target, and creates the project through `Project.create`.
- `korrektor.format`: the project format (S01), with no UI and no PDF code. The specification is [project-format.md](project-format.md).
  - `Model.kt`: the data model of a page layer: `PageLayer` → `Region` → `Line` → `Word`, with `Box` and `RegionType`. Original OCR and corrected text use the same types. Unknown fields are kept in `extra`.
  - `AtomicFile.kt`: crash-safe replacement of a file (temp file, flush, atomic rename) and cleanup of leftover temp files. Every file the project writes goes through it, except the append-only history.
  - `CanonicalJson.kt`: the shared writing rules (separators, key order, number format, string escapes) and parsing with `FormatException`.
  - `PageCodec.kt`: reads and writes a `PageLayer` as the text of a page file, one book line per file line. It only delegates to `PageEncoder.kt` (writing), `PageDecoder.kt` (reading) and `PageJson.kt` (typed access to the JSON, with errors that name the place in the file). The split keeps each part small enough for Detekt's size rules.
  - `Model.kt` also holds `Metadata` (title, author, year, source as typed).
  - `Project.kt`: a project folder. `Project.create` and `Project.open`, read-only `source.pdf` copy, page files through `PageCodec` and `AtomicFile`; original OCR layers are written once.
  - `ProjectFile.kt`: the text of `project.json` (format marker, version, page list, unknown fields kept): encoding and validated decoding.
  - `HistoryLog.kt`: the append-only undo history of one page (JSON Lines), read from the end, tolerant of a torn last line.
