# T083 Search engine: one layer or the whole book, within a line

**Type:** task · **Status:** done · **Story:** S10 · **Task:** S10.1

## What needs doing
- `SearchQuery(text, matchCase, wholeWords, regex)` and its compiled form (a `Regex`, or a message why the query is not valid: an empty text finds nothing, a regex that does not compile says why).
  - Plain text is searched literally; match case is off by default, whole words only is off by default, regex is off by default.
  - Whole words: the match must not be preceded or followed by a letter, digit or underscore (`\p{L}`, `\p{N}`, `_`). It works with regex too.
  - Case-insensitive search uses Unicode case folding of single characters (`UNICODE_CASE`), so `ä` finds `Ä`. It does not fold `ſ` to `s` (that is D5, not part of S10).
- `Match(page, lineId, lineNumber, lineText, start, end)`: a hit lies within one line, in the line's text exactly as stored (the words joined by one space). Empty matches (a regex such as `x*`) are dropped.
- `searchLayer(page, layer, regex)`: the matches of one corrected layer, in reading order, each line of each region.
- `searchBook(pages, regex, readLayer, onBatch)`: a suspend function, searches the pages in order and reports the matches after every chunk of pages, so that the caller can show them while it is still working. Pages are read in parallel inside a chunk, the batches come in page order. It stops at once when cancelled. A page that cannot be read is skipped and counted (reported in the result).
- A regex that takes forever on one line (catastrophic backtracking) must still be cancellable: the line is given to the regex as an interruptible `CharSequence`.
- The replacement of a match: `Match.replacement(regex, with, literal)` gives the text a match turns into: literal text for a plain search; for a regex search `$1`, `${name}` and `\$` work as in Kotlin's `Regex.replace`. A replacement that is not valid (`$9` with no group 9) is reported as a message.

## Acceptance criteria
1. Plain text, match case, whole words and regex each give the right matches (also with several matches in a line and with Unicode letters).
2. `s` does not find `ſ` in the layer (exactly as stored); `[sſ]` as a regex does.
3. A match is never across lines: a text spanning the end of one line and the start of the next finds nothing, and `.*` does not leave the line.
4. A page with no lines, or a layer with several regions, is searched in reading order; lines are numbered as in the text panel.
5. `searchBook` reports matches in page order, in several batches; after cancelling it does not report any more and returns promptly, also in the middle of a line with a regex that backtracks badly.
6. An unreadable page is counted, the others are still searched.
7. Invalid queries and invalid replacements are a message, not an exception.

## Approach
New package `korrektor.search` (`SearchQuery.kt`, `Match.kt`, `Search.kt`). Pure logic, no UI. Unit tests with hand-made layers, plus one with the real page files of a small project (`withHistoryProject`).

## Dependencies
None.

## Decisions
- Ignoring case is done by hand for a plain text (each letter becomes a set of its cases) and with the ASCII-only flag for a regex: Java's Unicode case folding makes `ſ` equal to `s`, so a search for `ſt` would find every `st`, which is the opposite of "exactly as stored". The price: a regex with match case off does not find `Ä` for `ä`; it says `[äÄ]`.
- Empty hits are no hits; a regex such as `x*` lists only the real ones.

## Progress
Done. `SearchQuery.kt`, `Match.kt`, `Search.kt` in `korrektor.search`; `SearchTest` (16 tests, also a regex that backtracks forever is cancelled). Build green.
