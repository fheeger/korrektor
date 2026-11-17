# T067 The change model: line changes, applying them, and grouping typing

**Type:** task · **Status:** done · **Story:** S08 · **Task:** S08.1

What one undo step is, as plain data with pure functions. No UI, no files yet.

## What needs doing
In `korrektor.format` (it becomes part of the project format, T071): `Change.kt` (the entries of the file come in T071)
- `sealed interface Change { val page: Int; fun inverted() }` with one case today, **`LineChange(page, region, index, removed, inserted, regionBefore, regionAfter)`**: in the region `region` of the page, the lines `removed` (the lines as they were, complete with words and boxes) that start at position `index` of the region's lines are replaced by the lines `inserted`. Every edit of S05 and S06 (typing, split, join, add, delete) is one such change, or two if a join reaches into another region. Other kinds of change (the page status, S09.5) are further cases of the sealed interface.
- `inverted()` swaps `removed` and `inserted` (and `regionBefore` and `regionAfter`).

In `korrektor.editor`: `Steps.kt`
- **`diff(page, before: PageLayer, after: PageLayer): List<LineChange>`**: for each region whose lines differ, one change (common start and end of the two line lists cut off, the middle is `removed` and `inserted`). A region that exists only in `after` (an added line on a page without regions) gets a change on its own.
- **`apply(layer, change): PageLayer?`**: replaces the lines; null if the layer does not hold `removed` at `index` of that region (the step does not fit the page). A region that is missing is created (type `text`, the next `order`, no box) when `removed` is empty and `index` is 0. Pure; the old layer is not changed.
- **`TypingGroups`**: decides whether a text edit of a line continues the step that is open or starts a new one (rules below).

Typing rules (`TypingGroups.continues(...)`): an edit of the text of a line continues the open typing step if all of these hold:
1. It is on the same page and line as the step's last edit, and the step is still open (nothing sealed it: another kind of edit, an undo or redo, a page change, a save).
2. Not more than `TYPING_PAUSE_MS` (1000) have passed since the last edit (clock injected).
3. It touches the place of the last edit (the changed part of the text starts at or before its end, and ends at or after its start), so typing in one place and then in another are two steps.
4. It is a single character inserted or deleted (a paste or a replaced selection is a step of its own, and the next edit starts a new one).
5. It does not start a new word: inserting a space (after a character that is not a space) starts a new step, so "Die Welt ist" is undone as " ist", " Welt", "Die".

## Acceptance criteria
1. For each edit of `PageEdits` (setLineText, splitLine, joinWithPrevious, joinWithNext, addLineAfter, deleteLine; also on a page without regions, and a join across two regions) on generated layers: `apply(before, diff(before, after))` equals `after` and `apply(after, invert(...))` equals `before`.
2. `apply` returns null (and changes nothing) if the lines at `index` are not `removed`, or if `index` is out of range.
3. `diff` of equal layers is empty; a change in one region gives one change.
4. Typing rules: each of the five rules above, with a fake clock: a run of characters in a word is one step; a pause of 1 s ends it (999 ms does not); a space, a second place, a paste, another line and a sealed step each start a new one; Backspace in the same place continues.

## Approach
Unit tests (`StepsTest`, `TypingGroupsTest`) on generated layers; no files. Tests compare layers with `==` (data classes).

## Dependencies
None (T037's `PageEdits`).

## Decisions
- **A region that is made is a part of the change** (`regionBefore`/`regionAfter`, both true normally). The only edit that makes a region is adding the first line of a page without regions; without the flags undo would leave an empty region `r1` and the page would not be what it was. With them, applying the inverse removes the region again, so undoing everything gives the imported page byte for byte.
- **A change that does not fit is refused, not forced** (`apply` returns null): the lines `removed` must be at `index`, the region must be as expected, and the page must stay valid (a line id twice would throw, which is caught). Used by undo (T069) and by the recovery after a crash (T072).
- **Typing step rules** as in the criteria; a pause is less than 1000 ms (exactly 1000 ms ends the step). The position test compares the edit with the span of the run, in the text after each edit.
- The line positions are positions in the region's list of lines, not in the reading order of the page, so a change stays valid when the regions are sorted differently.
- Named `Change.kt` and not `HistoryEntry.kt` (the entries come in T071).

## Progress
Done. `Change.kt`, `Steps.kt` (`diff`, `apply`), `TypingGroups.kt`. Tests: `StepsTest` (12), `TypingGroupsTest` (10). Build, tests, ktlint and Detekt are green.
