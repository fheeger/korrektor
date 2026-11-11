# Architecture

An overview of the code structure. Every ticket that changes the structure updates this file.

## Packages
- `korrektor`: the app entry point (`Main.kt`).
- `korrektor.format`: the project format (S01), with no UI and no PDF code. The specification will be in `project-format.md` (T007).
  - `Model.kt`: the data model of a page layer: `PageLayer` → `Region` → `Line` → `Word`, with `Box` and `RegionType`. Original OCR and corrected text use the same types. Unknown fields are kept in `extra`.
  - `AtomicFile.kt`: crash-safe replacement of a file (temp file, flush, atomic rename) and cleanup of leftover temp files. Every file the project writes goes through it, except the append-only history.
