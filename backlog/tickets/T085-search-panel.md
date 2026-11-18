> Changed by [T091](T091-replace-mode-keyboard.md): Ctrl+F in replace mode hides the replace field again.

# T085 The search panel: Ctrl+F, Ctrl+H, options, results list, click opens the place

**Type:** task · **Status:** done · **Story:** S10 · **Task:** S10.2, S10.7 (the keys of this ticket)

## What needs doing
- `SearchPanel` between the status bar and the scan/text panels, shown while `search.open`: the search field, three toggles (match case, whole words, regular expression), the scope (page / book), a button that shows or hides the replace field (the field itself comes in T087), a close button, a status line ("12 matches on 5 pages", "Searching… 340 of 1000 pages · Cancel", the message of an invalid query), and the results list.
- A result shows the page, the line number and the line's text around the match (the match marked). Clicking it opens that place: the page is shown, the cursor goes to the start of the match in the text, and the scan highlights the line (S05).
- `AppAction.FIND` (Ctrl+F) opens the panel with the replace field hidden and puts the cursor in the search field (the text in it is selected); `REPLACE` (Ctrl+H) opens it with the replace field shown. When the panel is open, the keys go to its field. Escape in the search field closes the panel and gives the cursor back to the text.
- Both in a new menu entry group of the **Edit** menu and on the F1 list.

## Acceptance criteria
1. Ctrl+F opens the panel without the replace field and focuses the search field; Ctrl+H opens it with the field (checked in T087); the toggle shows and hides it at any time.
2. Typing a text lists the matches with the page, the line number and the surrounding text; a regex option and the other options change the list as in T083.
3. Clicking a result on another page opens the page, the cursor is at the match in the text, and the scan highlights the line.
4. Escape closes the panel and the cursor is back in the text.
5. The keys are in the table of shortcuts, the menus and the F1 list (the existing tests that check that every action has a handler, a menu entry and a description pass).
6. The panel works in a window test with real key events.

## Approach
`korrektor/search/SearchPanel.kt`, `Shortcuts.kt`, `Menus.kt`, `ProjectKeys.kt`, `ProjectPanels.kt`, `AppModel.kt`. UI test (`SearchUiTest`) with the test tags `search field`, `search results`, `result <n>`.

## Dependencies
T084.

## Decisions
- Ctrl+F on a panel that is open keeps a replace field that is shown (the toggle hides it): switching between finding and replacing must not lose anything. Ctrl+F on a closed panel opens it without the field.
- The replace field is already there (the toggle needs something to show); what it does comes in T087 and T088.
- The options and the scope are labelled buttons ("Match case", "Whole words", "Regex", "This page", "Whole book") instead of icons: no tooltips to learn.
- The list of shortcuts on F1 got long enough to push "Close" out of view: the list now scrolls inside a dialog of at most 640 dp.

## Progress
Done. `SearchPanel.kt` (field, options, scope, replace field, summary, results), `SearchSnippet.kt` (what a result shows, the summary), `SearchKeys.kt` (`ProjectKeys.openResult`), `BookEditor.showPlace`, `FIND` (Ctrl+F) and `REPLACE` (Ctrl+H) in the table, the Edit menu and the F1 list, README. Tests: `SearchUiTest` (5, real key events), `SearchSnippetTest`, `MenusTest`/`ShortcutListTest`. Build green.
