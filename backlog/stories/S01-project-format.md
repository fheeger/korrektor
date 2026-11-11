# S01 Project format

**Features:** N5, N4 · **Status:** in review

As a reader of historical sources, I want a stable, text-based project format that stores pages, layout regions, lines and words, and keeps the original OCR separate from my corrections
so that my work is safe, easy to diff and share, and the original can always be compared.

## Acceptance criteria
- The format is documented in a spec, and every project records its format version.
- The format is text-based: correcting one word changes only a few lines in one file, and loading a page does not require reading the whole book.
- For each page it stores the page in the source PDF, the image size, the regions (box, type, reading order), the lines (box, order) and the words (box, text).
- Corrected lines can exist without a box, and their words can exist without boxes (needed for S05).
- The original OCR is stored separately and never overwritten. Corrections are stored as a full corrected text layer next to it.
- The project contains its own copy of the source PDF. The copy is never modified.
- Fields can be added later without breaking existing projects (A1 metadata and C5 page status come with their own stories).
- Saving is crash-safe: an interrupted save never leaves a broken file.
- The project stores the undo history in an append-only file, so it can be loaded without reading the whole history (needed for S08).
- Opening a project and saving it without changes leaves every file byte-for-byte identical.
- The data of the sample PDF fits the format.

## Tasks
- [x] S01.1 Decide the layout: one project folder or a single file, one file per page or not → decided in D03 (docs/decisions.md)
- [x] S01.2 Define the data model: project, page, region, line, word; original and corrected layers → [T002](../tickets/T002-data-model.md)
- [x] S01.3 Choose the serialization (e.g. JSON, YAML, or a custom line format) → decided in D03 (docs/decisions.md)
- [x] S01.4 Write the format spec → [T007](../tickets/T007-format-spec.md)
- [x] S01.5 Implement reading and writing, with round-trip tests → [T003](../tickets/T003-page-file-codec.md), [T005](../tickets/T005-project-folder.md), [T006](../tickets/T006-history-log.md), [T008](../tickets/T008-sample-fits-format.md)
- [x] S01.6 Implement crash-safe writing (write to a temporary file, then rename) → [T004](../tickets/T004-crash-safe-write.md)

## Report

### What was built
The project format as the package `korrektor.format`, specified in [docs/project-format.md](../../docs/project-format.md):
- **Data model** (T002): `PageLayer` → `Region` → `Line` → `Word` with `Box`. Original OCR and corrected text use the same types. Unknown fields are kept in `extra`.
- **Page files** (T003): canonical JSON writer and reader. One book line is one file line, coordinates have one decimal, and writing is byte-stable.
- **Crash-safe writing** (T004): `AtomicFile` (temp file, flush, atomic rename).
- **Project folder** (T005): `Project.create` and `Project.open`, `project.json` with format marker and version, a read-only copy of the PDF, page files, original OCR written only once.
- **History** (T006): `HistoryLog`, the append-only JSON Lines file per page, read from the end, tolerant of a torn last line.
- **Spec** (T007) and **sample test** (T008).

### Decisions
Recorded in the tickets T002–T008. The main ones:
- A line's text is its words joined by one space; there is no separate text field (T002).
- Unknown fields are preserved at every level; a word that has some is written as an object instead of an array (T002, T003).
- Hand-written canonical JSON writer instead of a pretty printer, to get one file line per book line (T003).
- Corrected layers keep the IDs of the original layer and point to it with `origin` (T007).
- The model does not round coordinates; the codec rounds to one decimal on read and write. Code that builds boxes (the S03 import) must use one-decimal values, or they change on the first save.
- ktlint is both formatter and linter (T001).

### How each criterion was checked
| Criterion | Check |
|---|---|
| Documented in a spec, every project records its format version | `docs/project-format.md`; `ProjectTest` (version 1 in `project.json`); `SpecExamplesTest` runs every example in the spec |
| Text-based; one correction changes few lines in one file; loading a page doesn't read the book | `PageCodecTest` and `SampleProjectTest` (one word → one line in one file); `ProjectTest` (reading one page with all other page files removed) |
| Per page: PDF page, size, regions, lines, words with boxes | `ModelTest`, `PageCodecTest`, `SpecExamplesTest` |
| Corrected lines and words without boxes | `PageCodecTest`, `SampleProjectTest` |
| Original OCR separate, never overwritten | `ProjectTest.originalPageIsWrittenOnlyOnce` |
| Own copy of the PDF, never modified | `ProjectTest.sourcePdfIsNeverModified` (hash after operations, read-only flag) |
| Fields can be added later | `PageCodecTest` (unknown fields at every level), `ProjectTest` (unknown fields in `project.json`), the version rules in the spec |
| Crash-safe saving | `AtomicFileTest` (simulated failure keeps old content, no temp file), `ReviewFixesTest` (atomic copy) |
| Append-only history, loadable without reading everything | `HistoryLogTest` (100,000 entries, bytes read bounded, torn line, prefix check) |
| Open and save without changes: identical bytes | `PageCodecTest`, `ProjectTest`, `SampleProjectTest` (whole 165-page project) |
| The sample PDF's data fits | `SampleProjectTest` with the real sample: 165 pages with its real page sizes, generated OCR text |

### Code review (Opus 5.5) and what was done
No serious bugs were found. Found and fixed:
- `source.pdf` was copied without flush and rename, so a power cut could leave a truncated PDF in a project that opens normally. Now `AtomicFile.copy`.
- Page file names used the system locale for digits. Now `Locale.ROOT`.
- The timing test ran on every CI build. Now it only runs with `KORREKTOR_PERF_TESTS` set.
- `create` could delete a folder that appeared between the check and the creation, and created missing parent folders. Now `Files.createDirectory`; the parent must exist.
- Unknown fields could reuse a known name and cause duplicate keys. Now rejected.
- Invalid UTF-8 was silently replaced when reading, which would change bytes on save. Now an error.
- `NaN` and `Infinity` in unknown fields or history entries made invalid JSON. Now refused when writing.
- Page sizes below 0.05 or above 1e9 points, and huge coordinates, are now rejected.
- Two places where the spec and the code disagreed (position of unknown fields, deletion of `*.tmp` files).
- Smaller: a failing cleanup of the temp file no longer hides the original error; the read-only test no longer fails as root.

### Known gaps and uncertainties
- **Crash safety** is tested with a simulated failure, not by killing the process (S07 adds that). The folder itself is not fsynced after the rename, so a power cut can lose the very last rename, but never corrupt a file.
- **The sample's real OCR data** is not in the format yet; that needs the import (S03). Page sizes use the PDF MediaBox only; CropBox and rotation are not considered, and the spec does not yet say which box "page size" means. To be decided in S03.
- **History:** `Project` has no access to the history files yet (no `history/` folder created, no accessor). S08 adds it and must use the same page-number-to-file-name rule.
- **Two app instances** on one project: opening deletes leftover `*.tmp` files, which could be another instance's write in progress. This goes away when the lock file arrives (S07).
- `AtomicFile` throws `IOException` while readers throw `FormatException`; callers (S02, S07) should handle both.
- The format is only checked with generated data. Real-world quirks (very long lines, odd characters) will show up with S03.

### How to try it
There is nothing to see in the app yet. To check:
1. `./gradlew build` (about a minute): compiles, runs the tests and ktlint.
2. With `samples/Abhandlung_über_den_kleinen_Krieg.pdf` in place, `SampleProjectTest` also uses the real page sizes (it is skipped otherwise). Set `KORREKTOR_PERF_TESTS=1` to include the timing check.
3. Read [docs/project-format.md](../../docs/project-format.md) and judge whether this is the format you want to commit to.
