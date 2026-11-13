# T018 Extract words with text and boxes from a page

**Type:** task · **Status:** ready for development · **Story:** S03 · **Task:** S03.2

Turn the characters of a page's text layer into words: the text of each word, exactly as in the PDF, and its box.

## What needs doing
`WordExtractor` (package `korrektor.importer`): `words(textPage): List<RawWord>`, where `RawWord(text, left, bottom, right, top)` is in PDF coordinates (origin bottom-left; T019 converts them).
- Split into words at whitespace, at characters PDFium generated as separators, and — for text layers without space characters, like the notice page — at gaps between neighbouring characters that are wider than a threshold relative to the font size (the value comes from T017).
- The text of a word is the code points of its characters, unchanged: no normalization (V1), no joining of hyphenated words (D4 comes later), hyphens and `=` at the end of a line stay in the word. Surrogate pairs stay together.
- The box of a word is the union of its characters' boxes.
- What is discarded and how unusual characters are treated follows the "Rules for the import" of T017 (for example control characters).
- Words come in the order of the text layer; sorting is T020's job.

## Acceptance criteria
1. On a generated page with known words, the extractor returns exactly those words, with boxes within 1 point of the drawn positions.
2. Text without space characters (words separated only by gaps) is split into the right words; a normal space inside a word's neighbourhood does not split a word that has no gap.
3. `ſ`, a combining `ͤ`, `ꝛ` and a character outside the BMP come out unchanged (same code points).
4. A hyphen or `=` at the end of a line stays at the end of its word, and the next line's first word is not joined to it.
5. Invisible text (render mode 3, as in the OCR PDFs) is extracted like visible text.
6. A page without text gives an empty list, without an error.
7. On page 6 of the sample (if present) the first words are `Vorrede.` and `5`, and the number of words matches an independent count from PDFBox within a few words, with line-end hyphens kept.

## Approach
- Unit tests on generated PDFs (`TestPdfs`): words in two lines, kerning-only spacing, unusual Unicode, line-end hyphens, invisible text, an empty page. One sample-PDF test.
- Reading characters in blocks keeps the number of native calls low; the box of a word needs only the extent of its characters.
- The parameters (gap threshold, discarded characters) are constants with the T017 evidence in a comment.

## Dependencies
T016, T017.

## Decisions
None yet.

## Progress
Not started.
