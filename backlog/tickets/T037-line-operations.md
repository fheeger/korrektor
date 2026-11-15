# T037 Line operations on a page layer

**Type:** task · **Status:** done · **Story:** S05 · **Task:** S05.3, S05.9

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
4. Split at the start, in the middle of a word, between words and at the end gives the two halves of the text (one space between two words is dropped at the cut); the boxes follow the rule above (checked with exact numbers); the new line has no `origin`.
5. Join appends without a separator, drops the second id, takes the union of the boxes; the first line of the page does nothing; across a region boundary it joins the last line of the previous region.
6. Add and delete behave as described, including the page without regions and the empty region.
7. After every operation the layer is valid (`PageLayer` checks) and `PageCodec.decode(encode(layer)) == layer`; new ids are unique.

## Approach
- Unit tests in `src/test/kotlin/korrektor/editor/PageEditsTest.kt` with small hand-made layers, plus a property-style test: random sequences of operations on the imported layers of a generated PDF keep the layer valid and round-trip through the codec.

## Dependencies
None (S03 is done).

## Decisions
- **A space at the cut is dropped:** if the cursor is next to the space between two words (before or after it), that one space is the line break and is not kept, so Enter between two words gives two clean lines and both lines keep the boxes of their words. Any other space stays (a cut inside a double space keeps the second one; nothing is trimmed, V1). The consequence: join after split does not give back the dropped space. A literal split would have left a trailing or a leading empty word on one of the lines at the most common cut.
- **Join is literal:** the text of the second line is appended directly, as in a text editor; no space is added. This is right for a hyphen at the end of a line (no rejoining of hyphenated words is made by the program, D4) and means that the user types a space where one is needed.
- **Each function returns an `EditResult`** with the cursor place (`Focus`: line id and character index) so that the editor (T038) and the panel (T043) do not have to work out where the cursor belongs: after a split the start of the new line, after a join the junction, after an add the new line, after a delete the end of the previous line (the start of the next one for the first).
- **The first part of a split keeps the old line box when it cannot compute a better one** (a cut word, or words without boxes) and has text; an empty first part (cut at the start) has no box, the text with its words moves to the new line.
- **Joining across regions** takes the previous line in reading order (regions by `order`), also from another region; the region the line came out of may stay with no lines.
- **Ids:** `l<N>`, N one more than the largest `l<number>` of the page; other ids (`x`) are not counted. An id of a deleted line can come back after a later add (when it was the largest): undo (S08) has to cope with this.
- **Words keep their `extra` fields** when the number of words stays the same (a corrected word is the same word); new words have none.
- **The helpers are in a second file** (`PageEditsSupport.kt`) because Detekt allows eleven functions per file.

## Progress
Done. `PageEdits`, `EditResult`, `Focus`, helpers. Tests (`PageEditsTest`, 27): no change returns the same layer, only the edited line changes and the old layer is untouched, boxes stay with the same number of words and go with another number, text is kept exactly (double spaces, leading and trailing spaces, `ſ ꝛ uͤ`, `=`, `-`, the empty text), split between words (before and after the space) with exact union boxes, inside a word, at the start and end, on a line without word boxes, at every place of a line (all text kept, one space lost at most), join with exact box and word union, the first line of the page, join with next, join with an empty line or a trailing space, join across regions, add (new id, no box, no origin, the page without regions, without a line id), ids never collide, delete (cursor places, the only line, the empty region), unknown lines are errors, and 400 random edits keep the layer valid, storable (codec round trip) with unique ids. `./gradlew build` is green.
