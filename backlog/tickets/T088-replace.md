# T088 Replace the selected matches as one undo step, and step by step

**Type:** task · **Status:** done · **Story:** S10 · **Task:** S10.6

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
`korrektor/search/ReplacePlan.kt` (pure: from the matches and a way to get layers, the new layers and the changes) and `SearchReplace.kt` (`replaceSelected`, `replaceCurrent`, `replaceAndGoOn`), `BookEditor.recordStep` from S08. Unit tests; the cross-ticket tests are in T089.

## Dependencies
T086, T087.

## Decisions
- The new text of a line is worked out from the old one with all its hits replaced at once (not hit by hit), so that several hits in one line do not move each other.
- A page that the user edited between the plan and the recording is left alone (the plan was made from its old state): `applyPlan` compares the editor's layer by identity under the lock, and the hits on that page count as "not replaced".
- After "Replace selected" the cursor goes back to the text (`leavePageField`): the click on the button took the keyboard away, and without a focused element Ctrl+Z reaches nothing.
- The hits that were left unchecked stay in the list and stay unchecked; the hits in a changed line are moved to where they are now, so that no new search is needed.
- "Replace" with no hit gone to yet only goes to the first hit; the replacement of a hit is its own step, and a replacement that does not change the text (the same text) counts as replaced but records no step.

## Progress
Done. `ReplacePlan.kt` (the plan: new layers and changes per page, skipped hits; `applyPlan` records one step with `recordStep`), `SearchReplace.kt` (`replaceSelected`, `replaceCurrent`, `replaceAndGoOn`, the results after a replacement), the buttons "Replace selected", "Replace" and "Skip" and the outcome line in the panel, README. Tests: `SearchReplaceTest` (12: one step over 12 pages with undo and redo, unchecked stay and move, several hits in a line, regex groups, status unchanged, stale lines, an edit during the plan, one by one, invalid replacement, nothing to replace, replacing flag, saved and reopened with one step), `SearchUiTest` (2 more). Build green.
