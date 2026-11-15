# T025 Fixes from the S03 code review

**Type:** task · **Status:** done · **Story:** S03 · **Task:** none (review findings)

The end-of-story code review of S03 (Opus 5.5, see the story report) found eight points. This ticket fixes those that hold up and records why the others stay.

## What needs doing / done
1. **Wrong note on page 1** (fixed): when page 1 cannot be loaded, the note said the size of the previous page was used, but A4 is used. The note now says "A4 size is used" in that case; `docs/project-format.md` says so too. Test: `PageNotesTest.aPageThatCannotBeLoadedAtTheStartUsesA4`.
2. **Orphan project on a cancel from outside during the create step** (fixed): `createProject` now deletes the project when the coroutine is cancelled after `create` has finished but before the import took over. Test first, which failed without the fix: `ImportFlowTest.aCancelWhileTheProjectFolderIsBeingMadeLeavesNoFolder`.
3. **Reading order only tested against itself** (fixed): `ImportSampleTest.theLinesFollowThePrintedOrderOnPagesLookedAt` asserts the first word of each of the first twelve lines on pages 6, 50 and 100, checked by eye against the overlays.
4. **Cleanup claimed success without checking** (fixed): `deleteTree` throws a `FormatException` naming the folder if something could not be deleted, so the user sees a message instead of "Import cancelled." with a folder left behind. Tests: `FolderTreeTest` (the failure case runs on Windows only, where an open file cannot be deleted).
5. **Word-gap rule on rotated pages with real spaces** (not fixed, known gap): the rule compares `box.left` with the end of the previous word, in the page's own coordinates. On a page that is rotated by 90° or 270° with upright text *and* real space characters *and* generated spaces between pieces, the gap is not measured along the text. No sample has a rotated page; Abhandlung has no real spaces at all, where the rule is not used. Left for when such a PDF turns up; recorded in the story report.
6. **Native page leak if the page cannot be sized** (fixed): `PdfDocument.page` closes the loaded page if `PdfPage` cannot be built.
7. **Progress text** (not changed): the dialog shows the number of pages *done* as "Importing page n of N" (T023 decision, covered by UI tests). Before the first page is done it shows "Creating the project…". Cosmetic; kept.
8. **Docs** (fixed): the merged `KorrektorTheme`/`MetadataForm` bullet in `docs/architecture.md`, and a line for `FolderTree.kt`, `Project.setPages` and `PageLayer.note`.

## Acceptance criteria
Every fixed point has a test (finding 6 and the Windows-only case of 4 are covered by the build as far as the environment allows); `./gradlew build` is green.

## Dependencies
T024.

## Progress
Done.
