# T020 Lines in reading order and one body region

**Type:** task · **Status:** in progress · **Story:** S03 · **Task:** S03.3

Group a page's words into lines, put the lines and the words in reading order, and wrap them in one region. Layout detection (B3) comes later, so every page gets one body region.

## What needs doing
`LineBuilder` (package `korrektor.importer`): `build(words: List<Word with Box>): Region?` (top-left coordinates, so it works on the output of T019):
- **Lines:** words whose vertical extents overlap enough belong to one line. The test compares a word with the line built so far (its band), so lines that slope a little (skewed scans) stay together, and words of different size on one line (a page number next to a heading) too.
- **Reading order:** lines from top to bottom, words in a line from left to right. The order of the text layer is not trusted for sorting (the notice page and other layers may not be in reading order).
- **One region** per page: id `r1`, type `text`, order 1, box = the union of all line boxes. Lines get the ids `l1`, `l2`, … in reading order; a line's box is the union of its words' boxes.
- A page without words gets no region (`regions: []`).
- Words are not changed: the text of a line is its words joined by one space, as the format defines.
- Known limitation (in the story): on pages with several columns the lines of the columns are mixed up until B3/B4 exist.

## Acceptance criteria
1. Words given in scrambled order come out as lines top to bottom and words left to right.
2. A line whose baseline drifts by a few points across the page stays one line; two lines one line-height apart stay two lines.
3. Words of different font sizes on the same line (as `Vorrede.` and the page number `5` on page 6 of the sample) stay in one line.
4. The region is `r1`/`text`/order 1 with the union box; line ids are `l1…` in order; every line has a box and its words have theirs.
5. A page without words gives no region.
6. A two-column page does not fail; its lines are the mixed lines the story names as a known limitation (the test says so in a comment).
7. On page 6 of the sample (if present) the first line reads `Vorrede. 5`, the number of lines is the one the analysis of T017 expects for that page, and the lines are in strictly increasing vertical order.

## Approach
- Pure functions on data, so the tests need no PDF for most cases: hand-made word boxes (including the sloping line and the two-column case). One sample-PDF test.
- Tolerances are relative to the median word height of the page, not absolute points, because the pages differ in size.

## Dependencies
T018, T019.

## Decisions
- **The tolerance is an overlap ratio, not points.** A word joins a line if it shares at least half of the smaller of the two heights with the line's latest word (0.5). That is relative to the size of the text, so it works for small and large pages alike; the "median word height" planned in the approach was not needed.
- **Sweep from left to right and compare with the latest word of each line, not with the whole line.** That is what keeps a skewed line together: its words drift by a fraction of their height from one to the next, while its first and last word can be several points apart.
- **Lines are ordered by the median centre height of their words**, and the words of a line by their left edge. Nothing depends on the order the words arrive in (tested with shuffled and reversed input).
- **Stray words** (OCR noise from scan shadows at the page edge, between two lines) get a line of their own unless they overlap a text line enough. They are part of the text and stay (V1); a later feature can offer to clean them up. Seen on pages 24 and 36.
- **The independent oracle is PDFBox's position-based lines**, not PDFium's line breaks (PDFium breaks a line at only about one line end in three, see the analysis). PDFBox is stricter, so it finds as many lines or more on every page; the two are within two lines on 145 of 165 pages. The builder never finds more lines than PDFBox, and the sample tests say so.
- **I looked at it:** the lines drawn over the rendered pages 6, 24 and 36. All printed lines are one box each, in order; the extra lines PDFBox counts are the stray margin words.
- **The overlap rule was checked by changing it** (0.05 and 0.95): five tests fail each time.

## Progress
Done: `LineBuilder` and `PlacedWord`; 13 tests in `LineBuilderTest`: scrambled and reversed input, a skewed line (7 points of drift over 240 points) with its neighbours, close lines, words of different sizes on one line, the region and the line ids and boxes, words and their boxes unchanged, one-decimal boxes, a stray word, the two-column page (rows mixed, as the story says), and on the sample page 6 (`Vorrede. 5`, 30 lines in order) and all 165 pages (lines in order, words in order, no word lost, never more lines than PDFBox, within two lines on at least 140 pages). `./gradlew build` is green.
