# T022 Pages without text or with a broken text layer

**Type:** task · **Status:** ready for development · **Story:** S03 · **Task:** S03.7

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
None yet.

## Progress
Not started.
