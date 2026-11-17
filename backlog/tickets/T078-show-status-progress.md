# T078 Show the page status and the progress

**Type:** task · **Status:** done · **Story:** S09 · **Task:** S09.2

## What needs doing
- **Next to "Page n of N"** the status of the page: "Page 12 of 165 · corrected" or "· unchecked" (`ScanLinks.pageStatus`, read from the editor, so it follows a change at once).
- **A status bar** between the header and the panels: the progress "57 of 165 corrected (35 %)" and the button for the status (T079).
- **`progressText(corrected, total)`** decides the text: the percentage is rounded, but 100 % only when all are done and at least 1 % as soon as one is.

## Acceptance criteria
1. A new project says "0 of 165 corrected (0 %)" and "Page 1 of 165 · unchecked".
2. After a change the text follows; undo too.
3. 57 of 165 gives "(35 %)"; 1 of 1000 gives "(1 %)", 999 of 1000 gives "(99 %)", 1000 of 1000 "(100 %)".
4. Showing the progress reads no page file.

## Approach
`PageViewer.kt`/`ScanLinks.kt` (status text), new `StatusBar.kt` (progress), `App.kt`. Unit test for `progressText`; Compose UI test of the window.

## Dependencies
T077.

## Decisions
- The progress counts only "corrected"; an unknown status from a later version counts as not corrected.

## Progress
Done. `StatusBar.kt` (`progressText`, `correctedPages`, the bar), `ProjectPanels.kt` (the panels, moved out of `App`), `ScanLinks.pageStatus` and the status next to "Page n of N". Tests: `ProgressTextTest`, `StatusUiTest` (2, the whole window). Build green. The progress reads the statuses in memory; that no page file is read is tested with a 1000-page book in T081.
