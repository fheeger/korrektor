# T090 Fixes from the S10 code review

**Type:** task · **Status:** done · **Story:** S10 · **Task:** S10.9

The end-of-story review (Opus 5.5, whole story) found one high finding, two medium and several low ones.

## What was done
1. **Step-by-step Replace skipped hits in "This page" scope (high).** A replacement changed the layer of the page shown, and the panel searched that page again, which threw away the results and the current hit; the next Replace then jumped over a hit. `pageChanged` now ignores the layer that the replacement itself made (`layerAfterReplace`).
2. **A new query during a replacement mangled the new results (medium).** `replace` remembers the generation of the search it started from and leaves the results alone if a new search has begun meanwhile.
3. **Whole words and combining marks (medium).** `\p{M}` counts as part of a word, so `zu` does not match inside `zuͤ`.
4. **Replace on the last hit went back to a skipped hit (low).** The current hit now wraps around to the first, like F3.
5. **Enter and Escape in the replace field (low):** they work as in the search field.
6. **Docs (low):** the outcome text says the page may not have been readable; the architecture and the model comment say that results are handed over on a default dispatcher; the story's tasks are ticked.

## Acceptance criteria
Tests in `SearchReviewFixesTest`: a replace in page scope keeps the results and the current hit; a new query during a replacement keeps the new results right; replacing the last hit makes the first one current; a combining mark belongs to its letter.

## Decisions
- **Not changed (low):** the preview runs a regex on the UI thread on already-found hits (it found them once on the IO thread, so it is not expected to backtrack badly, but it is not interruptible); closing the panel during a long replace cancels it without a message (no data is lost: the step is recorded only at the end); plain-text case folding is not symmetric for `ẞ`/`ß` and `İ`/`i`; the timing limits are asserted only with `KORREKTOR_PERF_TESTS=1`, as in S07 and S08; the results of a whole-book search are not refreshed when the user edits the text (they are checked before every replace and skipped if stale).

## Progress
Done. Build green.
