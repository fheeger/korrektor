# T022 Pages without text or with a broken text layer

**Type:** task · **Status:** in progress · **Story:** S03 · **Task:** S03.7

A page the import cannot get text from must not stop the import: it becomes an empty page with a note, and the import goes on.

## What needs doing
- **Format:** a page layer gets an optional field `note` (string): a short message about the page, for the user. It is written in both layers (original and corrected), after `regions`. `PageLayer` gets `note: String? = null` as its last parameter, so existing callers don't change; the codec reads and writes it; `docs/project-format.md` documents it (table, example). A new optional field does not change the format version.
- **Import:** in `PdfImport` (T021), each page is imported inside its own error handling:
  - no characters at all, or only whitespace/control characters (T017 settles exactly): an empty page (`regions: []`) with the note *"No text on this page."*;
  - PDFium cannot load the page or its text layer: an empty page with the note *"The text of this page could not be read: <reason>."* The page size is the page's own if it can be read, otherwise the size of the previous page (A4 if there is none), and the note says so.
  - Both layers are written as for any page, so every page of the PDF is a page of the project.
- **Result:** `ImportResult` lists the pages that got a note, with the note, so the UI can tell the user (T023).
- Cancelling is not an error and still stops the import (T021); only per-page failures are contained.
- Not in scope: judging the quality of OCR text (garbage text is imported like any other text).

## Acceptance criteria
1. A generated PDF with pages `text, empty, text` imports as 3 pages; page 2 has no regions and the note *"No text on this page."* in both layers; pages 1 and 3 are normal.
2. A page with only an image, and a page with only spaces, are handled as page 2 above.
3. A page whose content stream is broken (generated) either imports with the text that could be read or becomes an empty page with a note; in no case does the import stop or throw.
4. The `ImportResult` names exactly the pages with notes.
5. The `note` is written in the canonical form, reads back, survives open/save with identical bytes, and an unknown reader keeps it (it is a normal optional field); a page without a note is written as before (no change in existing files).
6. The spec documents `note`, and `SpecExamplesTest` covers its example.
7. On the sample PDF (if present): pages 3, 139, 161, 163 and 165 are empty pages with the note, and all other pages have text.

## Approach
- Model and codec change first (small, with `PageCodecTest` cases), then the import.
- The broken-page test generates a PDF whose content stream is cut off or has an invalid operator; PDFBox can write that with a raw stream. If PDFium happens to read it fine, the test accepts that (criterion 3 says "either").

## Dependencies
T021, T017.

## Decisions
- **`note` is a field of the page, written after `regions`** and kept in both layers: the corrected layer is a copy, and the user may want to see why a page is empty while correcting it. It is a normal optional field, so the format version stays 1; a reader that does not know it keeps it like any unknown field.
- **"No text" means no words.** A page with only a picture, or only spaces, has no words, so it gets the note, like a page with no characters at all. Whether the text is any good is not judged: garbage text is imported like any other.
- **Failures are caught per page, and only there.** Two levels: if the page cannot be loaded (or its size cannot be read), the page gets the size of the previous page (A4, 595.3 × 841.9, if it is the first) and the note says so; if the page is there but its text cannot be read, the page keeps its own size. The catch is on `Exception` and carries a comment; cancelling is checked before each page, outside the catch, so it is never swallowed.
- **PDFium is lenient with broken content:** a content stream cut off in the middle of an operator is read as far as it goes (the test PDF gives `Broken` and no note). So the "text could not be read" path is only reached through the `openPage` seam of `PdfImport.run`, which the tests use to fail a page to load and to hand over a page whose text cannot be read.
- **`ImportResult.notes`** lists the page numbers and notes, so the UI (T023) can tell the user without reading every page.
- **Not done:** a way to remove a note, or to re-import a page. The note is just text on the page for now.

## Progress
Done: `PageLayer.note` with codec and spec (table, text, and a `page-note` example checked by `SpecExamplesTest`); per-page error handling in `PdfImport` with `PageNote` and `ImportResult.notes`; `TestPdfs` can make pages with only a picture and pages with a cut-off content stream. 11 new tests: 3 for the codec (written after `regions`, before unknown fields, a page without a note is written as before; `null` accepted; a non-string refused naming `page.note`), 8 for the import (a page without text, an image-only page and a spaces-only page, notes surviving open and save, a broken content stream, a page that cannot be loaded and takes the previous size, the same at the start (A4), a page whose text cannot be read and keeps its own size, and the sample: notes on exactly pages 3, 139, 161, 163 and 165 in both layers). `./gradlew build` is green.
