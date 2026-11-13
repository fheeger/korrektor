# The text layer of the sample PDFs

What the text layer of a PDF with OCR looks like, and what PDFium makes of it. This is the evidence behind the import of S03 (T018–T022). The facts that later code depends on are asserted by `SampleTextLayerTest`; the numbers below come from an analysis through the binding of T016 on 2026-11-13 (PDFium `chromium/8152`).

Two PDFs were looked at. Both are Google Books scans with an OCR layer:

- **Abhandlung** (`Abhandlung_über_den_kleinen_Krieg.pdf`): 165 pages, German, 1785. The first test object.
- **Clausewitz** (`Clausewitz_Strategie.pdf`): 99 pages, a German book in Fraktur with much poorer OCR. Used to check that the rules are not tuned to one file.

## 1. Characters or words?

**In the PDF** the two files are built differently:

- **Abhandlung, pages 2–165:** the text is invisible (text render mode 3), in a glyph-less font (`GlyphLessFont`, two-byte codes), drawn over one full-page JPEG 2000 scan. There is **one text piece per word**, each with its own position and its own horizontal scaling (`Tz`) so that the word spans the printed word. The lines come one after the other, each in its own text block. There are no space characters in the layer at all.
- **Abhandlung, page 1** (the notice added by Google): real, visible text in Times, one text piece per line, with the words separated only by kerning gaps (`-250` in the `TJ` arrays). No space characters either.
- **Clausewitz:** the layer contains real space characters between words (29,140 of them), and in some headings one text piece per letter or pair of letters.

**From PDFium** the text arrives as a list of **characters**. Each has a Unicode value, a box, a font size, and two flags: `generated` (PDFium inserted it, it is not in the PDF) and `hyphen`.

## 2. Spaces and line breaks

PDFium inserts three kinds of characters of its own. All three are flagged `generated`:

| Character | Abhandlung | Where |
|---|---|---|
| U+0020 space | 30,455 | between two text pieces on the same line |
| U+000D, U+000A | 2,110 each, always as a pair | where the next text piece is on a new line |

Their boxes are empty (zero width, at the end of the previous character) and their font size is 1.

- **In Abhandlung every generated space is a word boundary.** The gap between the characters around it is, relative to the font size, 0.30 at the median (25 % of them 0.20 or less, 75 % 0.41 or less), while characters inside a word have a gap of 0.00 (95 % are 0.00 or less, the largest is 0.56). The notice page is the same: PDFium generates spaces from the kerning gaps, so `Über dieses Buch` comes out with spaces although the PDF has none.
- **In Clausewitz the generated spaces are not reliable.** There are only 859 of them next to 29,140 real spaces. The gap after a real space has a median of 0.45; after a generated space the median is 0.25 but the largest 5 % are above 1.4 and some lie inside letter-spaced headings (`S tr a te g ie`).
- **The CR LF pairs are not reliable line boundaries.** At a hyphen at the end of a line PDFium may put a plain space instead (`Truppen-` space `dienst`, see 6), and it may join lines without anything (see 6).

## 3. Order

PDFium gives the characters in the order of the text objects of the page, which is mostly the reading order but not always:

- On pages 6–25 of Abhandlung, 20 of 278 line starts lie **above** the line before them (7 %). Typical cause: a page number or a running head that the OCR wrote late.
- PDFium and PDFBox (reading the same content) agree on the sequence of characters on 144 of the 165 pages; on 20 more pages they have the same characters in a different order.

So the import cannot trust the order: it sorts words by their position (T020).

## 4. Coordinates

- Boxes are in the **page's own coordinates**: points, origin at the **bottom-left**, y upwards. A box is `left`, `bottom`, `right`, `top`.
- For the glyph-less font the box bottom is the baseline and the box is about the font size high: on page 6 the first word, drawn at (97.7, 451.7) with size 11, has the box 97.7 / 451.7 / 109.2 / 462.9 for its first letter.
- **Rotation:** all 264 pages of both files have rotation 0. The binding reports the rotation of a page, and T019 handles 90°, 180° and 270° with generated PDFs.
- **Page size:** in Abhandlung the sizes differ slightly from page to page (mostly 290–312 × 484–497 points, a few larger ones such as 330 × 516; the notice page is 612 × 792). The size has to be read for every page. Media and crop box are the same there.
- **Page boxes that do not start at 0, 0:** in Clausewitz the visible area starts at x = 0.00 and y = -0.01 (other pages 0.01, 0.02). Conversion has to subtract the box's lower-left corner (`PdfPage.bounds`).

## 5. Encoding

Abhandlung has 193,598 characters in PDFium's list: 156,177 printable ASCII, 30,455 generated spaces and 4,220 generated line break characters, and the rest as follows. PDFium returns all of them as the Unicode the PDF's font map gives, unchanged:

- German letters: `ß` 931, `ä` 604, `ö` 225, `ü` 696, `Ö` and `Ü` 3 each; `å` 106; the long s `ſ` 94; a few accented letters (`á é í ó ú à ę`).
- About 60 characters from other scripts (Greek, Cyrillic, Hebrew, Arabic, Devanagari, Bengali, Tamil, Telugu, Khmer, Hiragana, Katakana, a CJK character). These are OCR noise from specks and stains; they are part of the text layer and stay.
- `U+2013`, `U+2022` and `U+2192` once to three times.
- **No** U+0000, no U+FFFD, no control characters, except the hyphen marker U+0002 (see 6), which PDFium makes up.
- No characters outside the BMP and no combining marks in Abhandlung. The generated test PDFs of T016 show how they arrive: `ſ` and a combining `ͤ` as themselves, a character outside the BMP as two UTF-16 halves.
- Clausewitz has `^` as a separate character where PDFBox, reading the same page, merges it with the next letter into `â`. PDFium leaves it as it is, which is what the text layer contains (V1).

## 6. Hyphens

- `-` is in the text layer (65 times in the Abhandlung as PDFium reports it, plus 10 hyphen markers, 75 in all, the same as PDFBox's 75). `=`, which the printers of that time used as a hyphen, occurs 33 times, and PDFBox finds the same 33.
- Almost every hyphen is kept: `ih-` + generated space + `nen`, `Truppen-` + generated space + `dienst`, `Reute=` + CR LF + `rey`. There is nothing to undo there.
- **PDFium joins hyphenated words in 10 places.** It replaces the hyphen by the control character **U+0002 with the `hyphen` flag**, and it puts **nothing** between that and the next line's text: `Erin` U+0002 `nerung`, `einKriegs` U+0002 `freund,`. Without a countermeasure the import would produce one glued word, and `Erin-` and `nerung` would be rejoined, which the story forbids. The marker stands for exactly the hyphen that is in the PDF: with it counted as `-`, PDFium's characters are identical to PDFBox's on the pages where the order agrees. In all ten cases the next character lies on a lower line.

## 7. Pages without text

Abhandlung: pages **3, 139, 161, 163 and 165** have no characters. Looking at them (rendered by the binding), they are near-empty pages: page 3 is the faint show-through of the back of the title page, page 139 a nearly blank page with a few specks. The scan is there; the OCR found nothing.

Clausewitz: 8 of 99 pages (1, 2, 3, 4, 8, 38, 94, 99). They were not looked at.

## 8. The notice page

Page 1 of Abhandlung is Google's notice, not part of the book. It has a real text layer (3,742 characters, Times) with correct, visible text, no space characters, and the same generated spaces and CR LF pairs as the OCR pages. It is imported like any other page (the story says so).

## 9. Errors in the OCR text

They are in the layer and stay: `seinenOf` + `ficier` on page 6 (a word glued to part of the next, then the rest), foreign-script noise (section 5), and in Clausewitz passages of misread Fraktur such as `3ufammen3icben`. PDFium does not change any of it. Two things that look like OCR errors when read through PDFBox are not in the layer: PDFBox joined `einem` and `Scharmüzel` on page 7 because the layer has no space between the pieces; PDFium separates them correctly.

## 10. Speed

Reading **every character with its box and flags** of every page, including opening each page and its text page:

| File | Pages | Characters | Time |
|---|---|---|---|
| Abhandlung | 165 | 193,598 | 247 ms |
| Clausewitz | 99 | 221,269 | 331 ms |

That is 1.5–3.3 ms per page, including JVM warm-up, on an AMD Ryzen (AMD64 Family 25 Model 97, 16 threads), Windows 11, JDK 21. A 1000-page book needs a few seconds of PDFium time in the background; writing two files per page (with a flush to disk each) will take more and is measured in T021 and T024. The JNA direct mapping is fast enough.

## Rules for the import

These are what T018–T022 implement.

1. **Characters.** Use the characters PDFium gives, with their Unicode values unchanged: no normalization, no repair of OCR errors. Join the two halves of a UTF-16 surrogate pair into one character.
2. **Hyphen marker.** A character with the `hyphen` flag (U+0002) is a hyphen: write it as `-` (U+002D), and **end the word there**. The next character starts a new word. (The line break PDFium dropped is recovered by the geometry in T020.)
3. **Word boundaries.** Real whitespace (a space character the PDF has, a tab) always separates words. PDFium's generated CR and LF always separate words. A **generated space** separates words if the page has **no real space characters** (then the words are separate text pieces, as in Abhandlung); on a page that does have real spaces it separates words only if the gap to the next character is at least **0.6 of the font size** (larger than the typical word space, so letter-spaced headings stay in one piece). The value 0.6 is a judgement from the numbers in section 2.
4. **Other characters.** Control characters (below U+0020, except those handled above) and U+0000 are dropped from words. They do not occur in the samples. A word that is left empty is dropped.
5. **Boxes.** The box of a word is the union of the boxes of its real (not generated) characters. Generated characters have empty boxes and are ignored for this.
6. **Order and lines.** The order in which PDFium gives the words is not the reading order (section 3), and its CR LF pairs are not the line boundaries (section 2). Lines and their order come from the geometry (T020).
7. **Pages without text.** A page with no characters, or no words left after rule 4, is an empty page with a note (T022); so is a page whose text cannot be read.
8. **Coordinates.** Subtract the lower-left corner of the page's visible area, flip y, and handle the rotation (T019); page sizes are read per page.
