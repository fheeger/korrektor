# T048 Fixes from the S05 code review

**Type:** task · **Status:** done · **Story:** S05 · **Task:** none (review findings)

The end-of-story code review of S05 (Opus 5.5) found seven points and one remark. This ticket fixes them.

## What was done
1. **A wish for the cursor followed the user to the next page** (fixed): `BookEditor.show` now clears the focus request, and the list of rows is a new list for every page (`key(editor.page)`), so the rows of the old page (the same ids `l1`, `l2`, …) cannot keep the focus or the scroll position. Tests: `BookEditorTest.aWishForTheCursorDoesNotFollowToAnotherPage`, `EditLinesTest.aCursorWishFromOnePageIsNotCarriedOutOnTheNextPage` (the read of page 2 takes longer than a frame).
2. **Corrections are lost without a sign when the project is closed or another one is opened** (made visible): the panel shows "1 page changed, not saved yet" (or "N pages changed, …") in red above the list as soon as there is a correction. A confirmation dialog before switching projects was not added: saving comes with S07, which removes the problem; the README says it too. Test: `EditLinesTest.thePanelSaysHowManyPagesAreChangedAndNotSaved`.
3. **Boxes at a split** (fixed): (a) Enter at the start of a line whose words have no boxes now gives the line's box to the part with the text (the new second line), so it can still be highlighted and clicked; (b) a part now gets the union of the boxes that its words *have* (a word cut in two has none), not "all or nothing", so after Backspace by mistake and Enter at the same place line 1 is one line tall again and line 2 keeps its own place. The rule is in `PageEdits.splitLine`; `PageEditsTest.enterAtTheStartOfALineWithoutWordBoxes…` and `…joiningByMistakeAndSplittingAgain…`.
4. **Clicking the visible part of a wide line jumped the scan sideways** (fixed): `reveal` no longer moves sideways if part of the box is in view (a line is often wider than the panel when zoomed in). Vertically nothing changed. Tests: `AutoPanTest.aBoxThatIsPartlyInViewSideways…` and `…clickingTheVisiblePartOfAWideLine…` (the earlier test only clicked at the first zoom).
5. **A keystroke could be lost** (fixed, in a different way than the review suggested): the field always reports its text now (the editor ignores a text it already has), so a second edit that equals a stale text is no longer dropped. Just doing that exposed a worse problem, which the tests of T042 caught: a row left over from the page before reported its text a moment late and, because line ids repeat on every page, changed the line of the page shown. The row actions are now bound to the page of their list (`listPage`) and do nothing for any other page (`EditLinesTest.aRowThatIsLeftOverFromAnotherPageCannotChangeTheLinesOfThePageShown`).
6. **Every keystroke recomposed the scan and restarted its tap detector** (fixed): `App` reads only the highlight (a `derivedStateOf`) and a remembered click handler, so typing does not recompose the scan panel; `PageViewer` reads the click handler through `rememberUpdatedState` and its tap detector is no longer restarted. The typing numbers did not change (median 5 ms per character).
7. **The Enter key of the numpad did not split** (fixed): `Key.NumPadEnter` is handled like Enter (`LineOperationsUiTest.theEnterKeyOfTheNumpadSplitsToo`).
8. **Docs:** `docs/project-format.md` says how ids of added lines are made and that a new line can get the id of a deleted one (so `origin`, not the id, tells which lines come from the OCR).

## Acceptance criteria
Every fixed point has a test that failed before or, for 5, that failed with the first version of the fix; `./gradlew build` is green; the performance tests pass with `KORREKTOR_PERF_TESTS=1`.

## Dependencies
T037–T047.

## Progress
Done.
