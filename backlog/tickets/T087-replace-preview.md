# T087 Replace mode: the results as a preview, before → after, with checkboxes

**Type:** task · **Status:** done · **Story:** S10 · **Task:** S10.5

## What needs doing
- The replace field in the panel (shown by Ctrl+H or the toggle): the replacement text; with the regex option `$1` and `${name}` refer to groups, otherwise it is literal.
- While the field is shown, each result shows the context with the match replaced: the text before → the text after (the replaced part marked), with a checkbox, checked by default. A replacement that is not valid shows the message and no preview.
- "Select all" and "Select none" for the checkboxes; the status line says "12 of 40 selected".
- Nothing changes in the book while only previewing (the editor's pages and the history stay as they are).
- Hiding the field (or Ctrl+F) shows the plain results again; showing it again keeps the unchecked boxes as long as the results are the same.

## Acceptance criteria
1. With the replace field shown, every result has a checkbox, all checked; the text shows before and after.
2. Regex replacement with groups (`(\w+)ſ` → `$1s`) shows the right result in the preview.
3. Unchecking a result removes it from the selected count; "Select none" and "Select all" work.
4. Typing in the replace field changes the preview but does not start a new search.
5. The editor's layers and history are not touched by the preview (a test compares the editor state before and after).

## Approach
`SearchModel` (replacement, `deselected`, `selected`), `Match.replacement` from T083, `SearchPanel.kt`. Unit tests and a window test.

## Dependencies
T085, T083.

## Decisions
- The checkboxes are kept as the set of unchecked indices of `results`; a new search (any change of the query or the scope) checks everything again, since the hits are new. Hits found later while a search runs are checked.
- A replacement that is not valid (`$5` with no group 5) shows no "after" and the message "The replacement is not valid: …", checked on the first hit.
- The checkbox is a text glyph in a toggleable box, not the Material checkbox, whose 48 dp touch area would make every row three times as high.

## Progress
Done. `SearchSelection.kt` (`excluded`, `isSelected`, `setSelected`, `selectAll`, `selectNone`, `selectedCount`, `selectedMatches`, `preview`, `replacementProblem`), the panel split into `SearchPanel.kt`, `SearchResults.kt` (rows with checkbox and before → after) and `SearchWidgets.kt`; README. Tests: `SearchSelectionTest` (7), `SearchUiTest` (2 more). Build green.
