# T088 Replace the selected matches as one undo step, and step by step

**Type:** task · **Status:** ready for development · **Story:** S10 · **Task:** S10.6

## What needs doing
- "Replace selected" changes all checked matches in one go: one step of the history (`recordStep`), so one Ctrl+Z takes back all of it, on all pages; it is saved by autosave like any correction. The page status does not change.
- Computing the new pages (reading pages that are not in memory) runs in the background, the step is recorded on the UI thread at the end; the button is disabled meanwhile, and the editor is checked before recording: a page that the user changed in the meantime is skipped.
- A match whose line has changed since the search (the line's text is not the one the search saw) is not replaced; the message says how many were skipped.
- Afterwards the replaced matches are gone from the results and the other matches of the changed lines have the right places (no new search).
- Step by step: "Replace" replaces the current match (its own undo step) and goes to the next, "Skip" goes to the next without changing anything. Both are in the panel while the replace field is shown, and use the current match of T086.
- The replacement of a line keeps the words' boxes if the number of words stays the same (the rule of `PageEdits.setLineText`); the line box stays.

## Acceptance criteria
1. Replacing 40 matches on 12 pages is one undo step: Ctrl+Z puts all 12 pages back; Ctrl+Y does it again.
2. The pages are unsaved afterwards and are written by the saver; after saving and reopening the project the text is replaced and the history has one step for it.
3. Unchecked matches stay as they were.
4. The status of a corrected page is still corrected.
5. A match whose line was edited after the search is skipped and counted, the others are replaced.
6. Replace and Skip step through the results; each Replace is its own undo step.
7. Several matches in one line (`aa` → `b` in `aa x aa`) all work; regex groups work.
8. While replacing, the UI keeps working (the pages are read and computed on the IO dispatcher).

## Approach
`korrektor/search/Replace.kt` (pure: from the matches and a way to get layers, the new layers and the changes), `SearchModel.replaceSelected/replaceCurrent/skip`, `BookEditor.recordStep` from S08. Unit tests; the cross-ticket tests are in T089.

## Dependencies
T086, T087.

## Decisions

## Progress
