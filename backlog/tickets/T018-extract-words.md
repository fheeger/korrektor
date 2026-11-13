# T018 Extract words with text and boxes from a page

**Type:** task · **Status:** ready for development · **Story:** S03 · **Task:** S03.2

Turn the characters of a page's text layer into words: the text of each word, exactly as in the PDF, and its box.

## What needs doing
`WordExtractor` (package `korrektor.importer`): `words(textPage): List<RawWord>`, where `RawWord(text, left, bottom, right, top)` is in PDF coordinates (origin bottom-left; T019 converts them). It implements rules 1–5 of "Rules for the import" in [docs/pdf-text-layer.md](../../docs/pdf-text-layer.md):
- **Characters** are PDFium's, unchanged: no normalization (V1), no repair of OCR errors, no joining of hyphenated words (D4 comes later). The two halves of a UTF-16 surrogate pair become one character.
- **Word boundaries:** real whitespace and PDFium's generated line breaks always separate words. A generated space separates words if the page has no real space characters (the words are separate text pieces, as in Abhandlung); on a page with real spaces, it separates words only if the gap to the next character is at least 0.6 of the font size.
- **Hyphen marker:** a character with the `hyphen` flag (U+0002) becomes `-` and ends the word; the next character starts a new word. (PDFium replaced a line-end hyphen by that marker and dropped the line break, see the analysis.)
- **Control characters** (below U+0020 other than whitespace) and U+0000 are dropped; a word left empty is dropped.
- **The box of a word** is the union of the boxes of its real characters; generated characters have empty boxes and are ignored.
- Words come in PDFium's order; sorting them is T020's job.

## Acceptance criteria
1. On a generated page with known words, the extractor returns exactly those words, with boxes within 1 point of the drawn positions.
2. On a page without space characters, where the words are separate text pieces (as in Abhandlung), each gap between pieces separates words.
3. On a page with real space characters, a real space separates words, a generated space with a small gap (letter-spaced text) does not, and a generated space with a gap of 0.6 of the font size or more does.
4. `ſ`, a combining `ͤ`, `ꝛ` and a character outside the BMP come out unchanged (same code points; the pair is one character).
5. A hyphen or `=` at the end of a line stays at the end of its word, and the next line's first word is not joined to it. In particular a hyphen marker becomes `-` and ends its word. (On the sample, the ten places listed in the analysis give words like `Erin-` and `nerung`.)
6. Invisible text (render mode 3, as in the OCR PDFs) is extracted like visible text.
7. A page without text gives an empty list, without an error.
8. On page 6 of the sample (if present) the first words are `Vorrede.` and `5`; over the whole sample no word contains U+0002 or a space, and no word is empty.

## Approach
- Unit tests on generated PDFs (`TestPdfs`): words in two lines, words as separate text pieces, a page with real spaces and one letter-spaced piece, unusual Unicode, line-end hyphens, invisible text, an empty page. Sample-PDF tests for the whole book.
- Reading characters in blocks keeps the number of native calls low; the box of a word needs only the extent of its characters.
- The 0.6 threshold and the other parameters are constants with a reference to the analysis in a comment.

## Dependencies
T016, T017.

## Decisions
None yet.

## Progress
Not started.
