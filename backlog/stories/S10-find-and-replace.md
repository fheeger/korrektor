# S10 Find and replace with a preview

**Features:** D2 · **Status:** ready for development

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
- [ ] S10.1 Search engine: current page and whole book, with match case, whole words and regex, within a line
- [ ] S10.2 Search and replace panel with an expandable replace field (Ctrl+F, Ctrl+H, toggle button) and a results list; clicking a result opens the place
- [ ] S10.3 Next/previous match (Enter, F3, Shift+F3)
- [ ] S10.4 Search in the background, with results appearing as they're found and a cancel option
- [ ] S10.5 Results list in replace mode: before → after, with checkboxes
- [ ] S10.6 Replace the selected matches as one undo step; step-by-step replace or skip
- [ ] S10.7 Add the shortcuts to the menus and the shortcut list
- [ ] S10.8 Tests: search options, replacing across many pages with undo, speed in a 1000-page book
