# T084 The search model: results that arrive while it searches, and cancel

**Type:** task · **Status:** done · **Story:** S10 · **Task:** S10.4

## What needs doing
`SearchModel` (Compose state, one per open project, `AppModel.search`) holds everything the panel shows and does, so that it can be tested without a window:
- the search text, the replacement, the three options, the scope (page shown or whole book), whether the panel is open and whether the replace field is shown;
- the results (`results`, a list that grows while the search runs), `searching`, how many pages are done, a message for an invalid query or unreadable pages;
- `search()`: cancels a running search and starts a new one after a short pause (150 ms, so typing does not start a search per letter). The search of the page shown reads the editor's layer; the search of the book uses what the editor has in memory (`changed`) and reads the other pages from the project, so unsaved corrections are found too.
- `cancel()`: stops a running search; the results found so far stay.
- Changing the text, an option or the scope starts a new search; the text, the options, the scope and the results survive switching between finding and replacing (`showReplace`).
- With the scope "page shown", the search follows the page (`pageChanged`) and the edits of that page.

## Acceptance criteria
1. With a 6-page project and a query, `results` fill up in page order while `searching` is true, and `searching` ends false with all matches.
2. Cancelling in the middle of a book search stops it: no more results arrive, `searching` is false, the results so far are kept.
3. A new query while one runs replaces the old one: no result of the old query is left.
4. Corrections that are not saved yet are found (the page was edited in the editor, no file written); the old text on disk is not.
5. The page scope searches the page shown only and follows `pageChanged`.
6. Switching `showReplace` on and off keeps text, options, scope and results.
7. An invalid regex gives a message and no results; clearing the text clears the results.
8. The UI thread never waits for page files: everything that reads files runs on the IO dispatcher (a test with a slow `readPage` checks that `search()` returns at once).

## Approach
`korrektor/search/SearchModel.kt` (state and job), builds on T083. The editor gets `currentLayer(page)` (the one in memory, else the file). Unit tests with `withHistoryProject` and a fake `readPage` with a delay, `runBlocking` with real dispatchers.

## Dependencies
T083.

## Decisions
- `Dispatchers.Main` is not on the classpath (tests have no UI thread), so the model takes the dispatcher for its results as a parameter (default `Dispatchers.Default`). Compose state can be changed from any thread; a new search and a late batch of the old one are kept apart by a lock and a generation number (a batch of a replaced search is dropped).
- The model is in `AppModel.search`, made when a project is opened and closed with it.

## Progress
Done. `SearchModel` (state, `search`, `cancel`, `show`, `change`, `changeScope`, `pageChanged`), `SearchRun.kt` (the running search and the arriving batches), `BookEditor.currentLayer`, `AppModel.search`. Tests: `SearchModelTest` (9: results while searching, cancel, a new query, unsaved corrections, page scope, switching between finding and replacing, invalid regex, unreadable page, never waits for files), `SearchWiringTest`. Build green.
