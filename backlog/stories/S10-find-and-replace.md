# S10 Find and replace with a preview

**Features:** D2 · **Status:** in review

As a reader of historical sources, I want to find and replace text across the whole book, with a preview before anything changes
so that I can fix recurring OCR errors in one go without breaking anything.

S10 searches and replaces in the corrected text, on the current page or across the whole book. Not part of it: treating variant spellings as equal, such as ſ/s or uͤ/ü (D5); matches across line breaks and hyphenation (D4); suggestions to repeat a correction everywhere (D3).

## Acceptance criteria
- Finding and replacing share one panel with an expandable replace field:
  - Ctrl+F opens the panel with the replace field hidden.
  - Ctrl+H opens it with the replace field shown.
  - A toggle button in the panel shows or hides the field, so you can switch at any time.
- Switching between finding and replacing keeps the search text, options, scope and results.
- You can search the current page or the whole book.
- Search options: match case, whole words only, and regular expression (off by default).
- Only the corrected layer is searched, exactly as stored. For example, "s" does not find "ſ"; use a regex like `[sſ]` for that.
- A match must lie within a single line.
- Results are listed with the page, the line and some surrounding text. Clicking a result opens that place, puts the cursor on the match and highlights the line on the scan (S05).
- Enter or F3 goes to the next match, and Shift+F3 to the previous one.
- Searching the whole book runs in the background: results appear while it's still searching, and it can be cancelled. A 1000-page book is fully searched within a few seconds and the UI never freezes (N1).
- While the replace field is shown, the results list is the preview: each result appears as before → after, with a checkbox. All checkboxes are checked by default. Nothing changes before you confirm.
- "Replace selected" changes all checked matches in one go. It counts as one undo step (S08) and is saved by autosave (S07).
- You can also step through matches one by one, choosing replace or skip for each.
- Page status doesn't change when text is replaced (S09).
- The shortcuts are in the menus and in the shortcut list (S06.6).

## Tasks
- [x] S10.1 Search engine: current page and whole book, with match case, whole words and regex, within a line → [T083](../tickets/T083-search-engine.md)
- [x] S10.2 Search and replace panel with an expandable replace field (Ctrl+F, Ctrl+H, toggle button) and a results list; clicking a result opens the place → [T085](../tickets/T085-search-panel.md)
- [x] S10.3 Next/previous match (Enter, F3, Shift+F3) → [T086](../tickets/T086-next-previous-match.md)
- [x] S10.4 Search in the background, with results appearing as they're found and a cancel option → [T084](../tickets/T084-search-model.md)
- [x] S10.5 Results list in replace mode: before → after, with checkboxes → [T087](../tickets/T087-replace-preview.md)
- [x] S10.6 Replace the selected matches as one undo step; step-by-step replace or skip → [T088](../tickets/T088-replace.md)
- [x] S10.7 Add the shortcuts to the menus and the shortcut list → the table entries and menu entries are made in [T085](../tickets/T085-search-panel.md) and [T086](../tickets/T086-next-previous-match.md)
- [x] S10.8 Tests: search options, replacing across many pages with undo, speed in a 1000-page book → [T089](../tickets/T089-search-tests.md)
- [x] S10.9 Fixes from the code review → [T090](../tickets/T090-s10-review-fixes.md)

## Report

### What was built
- **Engine** (T083): `SearchQuery` (plain, match case, whole words, regex), `searchLayer` (hits within one line, reading order, no empty hits) and `searchBook` (chunks of pages read in parallel, reported in batches in page order, cancellable even inside a backtracking regex).
- **Model** (T084): `SearchModel` per open project: query, scope, results that fill up in the background, cancel; it searches the corrections of the session, saved or not.
- **Panel** (T085): Ctrl+F / Ctrl+H, the ▸ toggle for the replace field, options, scope, results with page, line and context; a click opens the place (cursor on the hit, line marked on the scan); Escape closes.
- **Next / previous** (T086): Enter, F3, Shift+Enter, Shift+F3, wrapping; the search field keeps the keyboard while stepping; Edit menu entries and the F1 list.
- **Preview** (T087): with the replace field shown each result has a checkbox and shows before → after; `$1` works for a regex; nothing changes until confirmed.
- **Replace** (T088): *Replace selected* is one undo step over all pages (`recordStep`), saved by autosave, statuses untouched; *Replace* and *Skip* go through hits one by one.
- **Tests** (T089) and **review fixes** (T090).

### Decisions
- `s` does not find `ſ`: a plain search folds case per letter by hand; a regex folds ASCII only (Java's Unicode folding would equate them).
- Whole words: no letter, mark, digit or `_` next to the hit.
- The cursor goes to the start of the hit; the hit is not selected.
- Results are not refreshed when the user edits the text in a whole-book search; they are checked before every replace and stale hits are skipped and counted. The page scope searches again after each edit.
- After *Replace selected* the cursor goes back to the text, so that Ctrl+Z works (without a focused element no key reaches the shortcuts).
- S09's "Mark done" change is the first commit of this branch.

### How each criterion was checked
- **Panel, Ctrl+F / Ctrl+H / toggle, state kept:** `SearchUiTest`, `SearchModelTest`.
- **Scope, options, exactly as stored, within a line:** `SearchTest`, `SearchModelTest`.
- **Results and click, Enter / F3 / Shift+F3:** `SearchUiTest` (real key events), `SearchNavigationTest`.
- **Background, cancel, 1000 pages, UI free:** `SearchModelTest`, `SearchPerfTest`. Measured on a generated book of 1000 pages × 30 lines: rare word (1000 hits) 461 ms, common word (35172 hits) 207 ms, a stand-in UI thread waited at most 44 ms, cancel 2 ms.
- **Preview and checkboxes:** `SearchSelectionTest`, `SearchUiTest`.
- **One undo step, autosave, status:** `SearchReplaceTest`, `SearchFlowTest` (save, reopen, simulated crash), `HistoryCrashTest` (12 kills with a replace on all pages every sixth round), `SearchPerfTest` (1000 hits on 1000 pages in 0.4 s, undo 75 ms). The sample (165 pages): "Krieg" 14 hits in 389 ms, replaced by "Kampf" on 13 pages, one undo puts all back.
- **Step by step:** `SearchReplaceTest`, `SearchUiTest`.
- **Shortcuts in menus and list:** `MenusTest`, `ShortcutsTest`, `ShortcutListTest`.

### Code review (Opus 5.5, whole story)
One high finding, fixed in T090: in "This page" scope a step-by-step Replace jumped over a hit, because the replacement made the panel search the page again. Two medium, fixed: a new query typed during a replacement could leave wrongly moved hits in the new results; "Whole words" treated a combining mark (`zuͤ`) as a word boundary. Low ones fixed: wrap-around after replacing the last hit, Enter and Escape in the replace field, wording of the outcome, docs. Not fixed (listed below).

### Known gaps
- Not tried by hand: the real window (how the panel looks and fits, the width of the before → after rows, the colours, the keyboard flow with Enter and F3, clicking results).
- The preview runs the regex on already-found hits on the UI thread (not interruptible); closing the panel during a long replace cancels it without a message; plain-text case folding is not symmetric for `ẞ`/`ß`.
- A regex with match case off ignores the case of ASCII letters only (`[äÄ]` for umlauts).
- Enter right after typing, before the first results arrive, does nothing.
- The time limits are asserted only with `KORREKTOR_PERF_TESTS=1`.
- The scan marks the line only if it has a box (an added line has none).

### How to try it
Open the sample project and press **Ctrl+F**; type `ſ`: the hits are listed with page and line; click one, press **F3** to go on. Switch to *Whole book*, try *Regex* with `[sſ]t`. Press **Ctrl+H**, type a replacement, uncheck a hit, press *Replace selected*, then **Ctrl+Z**: everything comes back in one step. Try *Replace* and *Skip* one by one.
