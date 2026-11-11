# S03 Import a PDF with OCR

**Features:** A2 · **Status:** ready for development

As a reader of historical sources, I want to import a PDF that already has OCR, with its page images and the text with positions
so that I can start correcting without running OCR myself.

A PDF with OCR contains the scanned image plus an invisible text layer with characters or words and their positions, but no lines, regions or confidence values. The import rebuilds the lines. Layout detection (B3) comes later, so each page gets one body region.

## Acceptance criteria
- The import runs right after the project is created (S02) and covers every page of the PDF, including notice pages such as the one Google Books adds at the front.
- For each page it stores the page size, one body region, the lines in reading order, and the words with text and box, following the format from S01.
- The imported text is stored as the original OCR layer and also copied as the starting point for the corrected layer.
- The text is imported as is: no normalization (V1), and no rejoining of hyphenated words (D4 comes later).
- Pages without a text layer are imported as empty pages with a note and don't stop the import.
- The import runs in the background with a progress display and can be cancelled. Cancelling leaves no half-created project.
- Sample PDF: all ~165 pages are imported. On a few spot-check pages, the word boxes sit on the printed words and the line order matches the reading order.
- A 1000-page PDF imports without freezing the UI (N1).

Known limitation: pages with several columns may have their lines mixed up until layout detection and layout fixing (B3, B4) exist.

## Tasks
- [ ] S03.1 Analyze the sample PDF: how the text layer is built (characters or words, order, coordinates, encoding)
- [ ] S03.2 Extract words with text and boxes from a page
- [ ] S03.3 Group words into lines and put lines into reading order; create one body region per page
- [ ] S03.4 Convert coordinates into the format's coordinate system
- [ ] S03.5 Write the original and corrected layers for each page
- [ ] S03.6 Run the import in the background with progress and cancel
- [ ] S03.7 Handle pages without text or with a broken text layer
- [ ] S03.8 Tests: the sample PDF plus small generated test PDFs (page without text, rotated text, two columns to check the import doesn't fail)
