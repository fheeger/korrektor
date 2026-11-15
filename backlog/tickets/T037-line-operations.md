# T037 Line operations on a page layer

**Type:** task · **Status:** ready for development · **Story:** S05 · **Task:** S05.3, S05.9

The logic of correcting a page, without any UI: change the text of a line, split it, join it with its neighbour, add a line, delete a line. Pure functions on an immutable `PageLayer`, so they are tested with exact data and reused by the editor (T038) and later by undo (S08).

## What needs doing
New package `korrektor.editor`, object `PageEdits`. Every function takes a `PageLayer` and returns a new one (the old one is never changed); a function that changes nothing returns the same object.
- **`setLineText(layer, lineId, text)`**: the text of a line is its words joined by one space (S01), so the new words are `text.split(' ')`, and an empty text has no words. Nothing is trimmed or normalized (V1): two spaces make an empty word, which joins back to two spaces. The line keeps its id, `origin` and `box`. **Word boxes:** if the number of words is the same as before, each word keeps its box (a corrected word is still where it was, as in the example of `docs/project-format.md`); if it changed, the words have no boxes (matching them again comes later).
- **`splitLine(layer, lineId, index)`**: cuts the text at character `index` (0..length). The first part keeps the id, `origin` and box rules below; the second part is a new line after it in the same region, with a new id and no `origin`. Words that are cut by the cut lose their boxes. **Boxes:** a part whose words all have boxes gets as line box the union of those boxes (one decimal); otherwise the first part keeps the old line box and the second has none.
- **`joinWithPrevious(layer, lineId)`**: appends the text of the line directly to the previous line (no separator is added; the last word of one and the first of the other become one word, as in a text editor) and removes the line. Id and `origin` of the previous line stay; the line box is the union of both boxes (or the one there is); the merged word has a box only if both parts had one (the union). The first line of a region joins the last line of the previous region; the first line of the page does nothing. `joinWithNext` is the same for the following line.
- **`addLineAfter(layer, lineId, text = "")`**: a new line without box and `origin` after the given line, in its region. On a page without regions (a page without text) it creates region `r1` (type `text`, order 1, no box) with the line.
- **`deleteLine(layer, lineId)`**: removes the line; a region left without lines stays, with `lines: []`.
- **Ids:** a new line gets `l<N>` where N is one more than the largest number of the ids `l<number>` on the page (1 if there are none), so ids stay unique on the page (S01). An id that was deleted may be used again after that (undo, S08, has to cope with it).
- Functions say which line they were given: an unknown `lineId` is an `IllegalArgumentException`.
- `docs/architecture.md`.

## Acceptance criteria
1. Changing the text changes only that line; the original layer object is untouched; the same text returns the same layer.
2. Same number of words: boxes stay with their words, texts replaced. Different number: the words have no boxes. Line box, id and origin never change.
3. Text is kept exactly: double spaces, leading and trailing spaces, `ſ`, `ꝛ`, `uͤ`, `-` and `=` at the end of a line survive `setLineText` and the file round trip.
4. Split at the start, in the middle of a word, between words and at the end gives the two texts that join back to the original text; the boxes follow the rule above (checked with exact numbers); the new line has no `origin`.
5. Join appends without a separator, drops the second id, takes the union of the boxes; the first line of the page does nothing; across a region boundary it joins the last line of the previous region.
6. Add and delete behave as described, including the page without regions and the empty region.
7. After every operation the layer is valid (`PageLayer` checks) and `PageCodec.decode(encode(layer)) == layer`; new ids are unique.

## Approach
- Unit tests in `src/test/kotlin/korrektor/editor/PageEditsTest.kt` with small hand-made layers, plus a property-style test: random sequences of operations on the imported layers of a generated PDF keep the layer valid and round-trip through the codec.

## Dependencies
None (S03 is done).

## Decisions
None yet.

## Progress
Not started.
