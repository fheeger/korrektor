# T092 Fixes from the second S10 code review

**Type:** task · **Status:** done · **Story:** S10 · **Task:** S10.11

The re-review of S10 after T091 (Opus 5.5) found no high finding, one medium and several low ones.

## What was done
1. **Replace on the last hit lost the keyboard (medium).** The button goes away with the last hit and nothing took the focus, so Ctrl+Z, F3 and Ctrl+F did nothing. The cursor now goes back to the text then.
2. **Ctrl+Shift+R with nothing checked, or during a replace,** no longer moves the cursor out of the search field: it does nothing, like the disabled button.
3. **Menu entries:** "Mark match" needs a current hit and "Replace marked matches" needs checked hits and no replace running (`MenuState.hasCurrentMatch`, `hasCheckedMatches`).
4. **Docs:** the story's "how to try it", report and gaps, and notes in T085 and T087 pointing to T091.

## Acceptance criteria
`MenusTest` covers the menu states; the build is green. The focus fix (1) and the no-op key (2) are UI behaviour that the tests do not drive; they are on the list for the user to try by hand.

## Decisions
- **Not changed (low):** marks are lost in *This page* scope when the text changes or the panel is reopened (a new search starts); cancelling a search while a replace runs leaves the old results; a second Ctrl+R during a replace is dropped. All are in the story's known gaps.

## Progress
Done.
