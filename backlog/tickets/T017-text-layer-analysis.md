# T017 Analysis of the sample PDFs' text layers

**Type:** task · **Status:** ready for development · **Story:** S03 · **Task:** S03.1

Find out, with the real files, how their text layer is built and what PDFium makes of it, and write it down. The next tickets implement the rules this ticket settles.

## What needs doing
Write `docs/pdf-text-layer.md` and pin its findings with a test (`SampleTextLayerTest`, skipped when the samples are missing). The questions, each answered with evidence from `samples/Abhandlung_über_den_kleinen_Krieg.pdf` and, where it helps, `samples/Clausewitz_Strategie.pdf`:
1. **Characters or words?** What does the text layer contain, and in what units does PDFium hand it out?
2. **Spaces and line breaks.** Which characters does PDFium generate between text pieces (`generated`)? How do words appear on pages where the PDF has no space characters (kerning gaps only)?
3. **Order.** Is the content order the reading order for single-column pages? Check at least ten pages.
4. **Coordinates.** Origin and unit of the char boxes; offsets of the page boxes (the second sample has slightly shifted media boxes); rotation of the pages; page sizes (they differ from page to page in the first sample).
5. **Encoding.** Which characters occur: `ſ`, `ß`, umlauts, combining marks, characters outside the BMP, control characters, replacement characters? Does PDFium return them unchanged?
6. **Hyphens.** The OCR text contains hyphens and double hyphens at the end of lines (`ih-`, `Reute=`). Does PDFium keep them in the text, or does it drop or join them? How can they be read?
7. **Pages without text.** Which pages have none, and what is on them?
8. **The notice page** (page 1, added by Google Books): it has a real text layer, not OCR. What is different about it?
9. **OCR errors in the text** (words run together such as `einemScharmüzel`, odd foreign characters): they stay as they are (V1). Confirm that PDFium doesn't change them.
10. **Speed.** Average milliseconds per page for opening a page and reading all its characters, measured on the sample.

End the document with **"Rules for the import"**: the concrete rules T018–T022 implement (how words are split, what is discarded, how hyphens are read, what counts as a page without text).

## Acceptance criteria
1. `docs/pdf-text-layer.md` answers all ten questions, each with the evidence (page numbers, counts, short excerpts).
2. The facts that later code relies on are asserted by `SampleTextLayerTest`: the list of pages without text, the notice page having text, the number of pages, that line-end hyphens and `=` survive, that PDFium returns the characters unchanged, and the order on a few pages.
3. The "Rules for the import" section is specific enough to implement without further research, and says what was decided where PDFium's behaviour was awkward.
4. The measured speed is stated, with the machine it was measured on, and it is clear whether 1000 pages are feasible in the background.

## Approach
- A throwaway analysis against PDFBox already showed (see "Progress"): the OCR pages use an invisible glyph-less font (render mode 3) with one text piece per word, a horizontal-scaling value per word, a full-page JPEG2000 scan as background, and lines in reading order; page 1 is real Times text without space characters; pages 3, 139, 161, 163 and 165 have no text; line-end `-` and `=` occur.
- This ticket repeats the analysis through the binding of T016, because PDFium's view is what the import will see.
- Tests are limited to facts that must not change silently; the document carries the rest.

## Dependencies
T016.

## Decisions
None yet.

## Progress
Not started. Findings so far (from PDFBox, before any code): 165 pages in `samples/Abhandlung…pdf`, 5 without text (3, 139, 161, 163, 165), all pages unrotated, all page sizes slightly different (about 300 × 488 pt; the notice page is 612 × 792). Page 1 is Times text drawn with kerning gaps instead of spaces. About 1,200 lines in the first 36 pages; line-end `-` and `=` are in the text, and OCR errors such as missing spaces are too.
