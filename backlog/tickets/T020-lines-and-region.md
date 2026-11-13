# T020 Lines in reading order and one body region

**Type:** task · **Status:** ready for development · **Story:** S03 · **Task:** S03.3

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
None yet.

## Progress
Not started.
