# T017 Analysis of the sample PDFs' text layers

**Type:** task · **Status:** done · **Story:** S03 · **Task:** S03.1

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
- **The generated space is the word boundary where the PDF has no spaces** (Abhandlung), but not where it has real spaces and PDFium also generates some between letter-spaced pieces (Clausewitz). So T018 uses a per-page rule plus a gap of 0.6 of the font size. The number 0.6 is a judgement from the measured gaps (real spaces: median 0.45; generated spaces inside letter-spaced text: smaller), not a measurement.
- **PDFium's hyphen marker is undone in the import** (U+0002 with the `hyphen` flag becomes `-` and ends the word). This was the one place where PDFium's text differs from the PDF's: it joins hyphenated words, which the story forbids.
- **PDFBox is only a test oracle**, not a second reader: it combines `^` with the next letter, leaves out a letter drawn twice, and orders text pieces differently. The sample test therefore compares as a multiset of characters and names the one known difference (page 156).
- **Speed is not asserted in a test** (timing on shared CI machines is flaky); it is stated in the document with the machine.

## Progress
Done: PR #22 merged, CI green. `docs/pdf-text-layer.md` answers the ten questions and ends with the rules for the import; `SampleTextLayerTest` (6 tests, skipped without the samples) pins the facts: structure of both layers (pages without text, no real spaces in Abhandlung, only spaces and CR LF generated, no control characters, 65 `-`, 33 `=`, 94 `ſ`, ten hyphen markers each followed by a lower line), the first characters and box of page 6, the notice page, that PDFium separates `einem Scharmüzel`, and agreement with PDFBox on 164 of 165 pages.

Two things the earlier PDFBox-only look got wrong, found by this ticket: PDFBox had made `einemScharmüzel` look like an OCR error (the layer has the two words apart), and I had expected that PDFium would not change hyphens (it replaces ten of them by a marker).

Not looked at: the eight pages without text in Clausewitz, and rotated pages (none in the samples; generated PDFs cover them in T019).
