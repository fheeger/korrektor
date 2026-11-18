# T091 Replace mode: keyboard shortcuts and nothing marked by default

**Type:** task · **Status:** done · **Story:** S10 (review feedback) · **Task:** S10.10

Feedback from the user's review of S10: in replace mode the keyboard is not enough, and all hits are marked when the mode opens.

## What needs doing
1. **Mark a hit from the keyboard.** F3 already steps to the next hit in replace mode, but the checkbox of the current hit can only be changed with the mouse. Add a shortcut that marks or unmarks the current hit.
2. **A shortcut for the "Replace" button** (replace the hit that is shown and go on). The panel has a second button, "Replace selected" (all checked hits); it gets a shortcut too.
3. **Nothing is marked by default.** Today all hits are marked when replace mode opens or when new results arrive. They should start unmarked, so that the user chooses what is replaced.
4. **Ctrl+F in replace mode goes back to search mode:** the replace field is hidden, the query, options and results stay, and the search field has the keyboard.

## Acceptance criteria
- In replace mode, the new "mark / unmark" shortcut flips the checkbox of the current hit; with no current hit it does nothing. Steps with F3 and the shortcut can be repeated without the mouse.
- The "Replace" shortcut does what the button does (replaces the hit that is shown, one undo step, goes on); the "Replace selected" shortcut replaces all marked hits in one undo step, and does nothing with none marked, like the disabled button. All three keys work only while the replace field is shown.
- After a search in replace mode, and after switching from search to replace mode, no hit is marked. The "Replace" button is disabled until one is marked. New results (a changed query or option) also start unmarked.
- Ctrl+F in replace mode hides the replace field and keeps query, options, scope and results. Ctrl+F in search mode behaves as before. Ctrl+H still opens or switches to replace mode.
- The shortcuts are in the Edit menu, the F1 list and the table of shortcuts.

## Approach
- `SearchModel`: `excluded` becomes `marked` (empty by default); `toggleCurrent()`; `show(replace = false)` hides the replace field. The Replace shortcuts ask through request counters, and `SearchPanel` runs the same code as the buttons on the UI thread. New actions in `Shortcuts`, `ProjectKeys`, `Menus` (enabled only with the replace field shown).
- Tests: extend `SearchModelTest` (default unmarked, toggle, replace with none marked) and `SearchUiTest` (real key events: F3, mark, replace, Ctrl+F back to search). Update the tests that expect all hits to be marked by default.
- Docs: `docs/architecture.md` (T085/T088 entries), the S10 story summary, the F1 list.

## Dependencies
T085, T087, T088 (all done).

## Decisions
- **Keys:** Ctrl+M marks or unmarks the current hit; Ctrl+R is Replace; Ctrl+Shift+R is Replace selected. First proposed with Alt, but the table of shortcuts never accepts Alt (AltGr on Windows, see `Shortcuts`). Plain Space and Enter cannot be used because the search field has the keyboard; Ctrl+Enter is the status change (S09).
- **Ctrl+F in replace mode only hides the replace field.** Nothing is cleared, so going back and forth between Ctrl+F and Ctrl+H loses no work.
- **Marks are not kept across searches.** A new result list always starts unmarked, so a mark never refers to a hit the user has not seen.

## Progress
Done. `./gradlew build koverXmlReport` green. Tests: `SearchSelectionTest` (nothing checked at first, `toggleCurrent`, new search starts unchecked), `SearchUiTest` (real keys: F3, Ctrl+M, Ctrl+R, Ctrl+Shift+R, Ctrl+F back to searching, the keys do nothing in search mode), `MenusTest`. Tests that replace everything now call `selectAll()` first.
