# S01 Project format

**Features:** N5, N4 · **Status:** ready for development

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
- [ ] S01.2 Define the data model: project, page, region, line, word; original and corrected layers
- [x] S01.3 Choose the serialization (e.g. JSON, YAML, or a custom line format) → decided in D03 (docs/decisions.md)
- [ ] S01.4 Write the format spec
- [ ] S01.5 Implement reading and writing, with round-trip tests
- [ ] S01.6 Implement crash-safe writing (write to a temporary file, then rename)
