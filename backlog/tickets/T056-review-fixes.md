# T056 Fixes from the S06 code review

**Type:** task · **Status:** done · **Story:** S06 · **Task:** S06.8

The end-of-story review (Opus 5.5, whole story) found five problems and some cleanups. All are fixed here.

## What needs doing
1. **AltGr taken for a shortcut.** On Windows the JDK can report AltGr as Ctrl plus the AltGraph modifier, without Alt. AltGr+0 (`}`) and AltGr+Plus (`~`) on a German keyboard would then have been Ctrl+0 and Ctrl+Plus: the shortcut used up the key, and the character could not be typed. `Shortcuts.resolve` now also rejects an event whose native key event says `isAltGraphDown`.
2. **Down/Up in the moment after a page change.** The viewer changes the page at once, the editor a frame later. A second Down in between was worked out on the old page (its last line still active) and opened the viewer's next page, so a page was skipped; an Up was lost. `ProjectKeys.moveLine` now ignores a key while `editor.page != viewer.page`.
3. **A stale arrival.** A wish for the cursor on page 5 (Ctrl+Down) that was never carried out (the user went on to page 6 before 5 was read) stayed, and later put the cursor into page 5 when it was opened with the mouse. `BookEditor.show` drops an arrival that is for another page. T050's decision ("cannot put the cursor somewhere unexpected") holds now.
4. **Escape or Enter in the page field on a page without lines** left the focus in the field. They now give the focus to the text panel (`BookEditor.focusPanel`, a request counter the panel reacts to).
5. **The column was not sticky.** Moving down over a short line took the column away (8, then 2, then 2). `caret` is now the column the user put the cursor at (a click, typing, the arrow keys, a split); moves with Up/Down/Ctrl+Home/Ctrl+End keep it (`requestFocus(..., keepColumn = true)`), and the row no longer overwrites it with the clamped index.
6. **Cleanups:** `Arrival.NONE` was never used and is gone; the page field and `goToText` share one parser (`ViewerState.pageOf`); an outdated comment in a test; the architecture document, which was not yet committed, is part of this ticket.

## Acceptance criteria
1. A key press with AltGr held is never a shortcut (see Decisions on how this was checked).
2. A Down in the moment after a page change opens no further page (UI test; it fails without the fix, checked).
3. An arrival for a page that was not shown is dropped (model test); the arrival tests of T050 still hold.
4. Escape and Enter in the page field on page 3 of the test book give the focus to the text panel, and Ctrl+Up works from there (UI test).
5. Up/Down over a short line keep the column (model test).
6. The architecture document describes the new files and changes.

## Approach
`Shortcuts.kt`, `ProjectKeys.kt`, `BookEditor.kt`, `LineNavigation.kt`, `TextPanel.kt`, `ViewerActions.kt`, `PageViewer.kt`; tests in `LineNavigationTest`, `PageShortcutsUiTest`.

## Dependencies
T049 to T055.

## Decisions
- **AltGr is not tested by a test.** The test framework cannot make a key event with the AltGraph modifier (the public constructor has no such parameter, and a native event cannot be wrapped), so criterion 1 is the code plus a manual check on a German keyboard (listed in the story report).
- **Not done:** one shared `ProjectKeys` for `Main` and `App` (two stateless instances are harmless); a stronger check of the burst test (a key during a page read is lost by design, so the end page of a burst is not exact; the paced test is exact).
- **A key that falls into the moment between viewer and editor is dropped** rather than queued: it is under a frame long, and a repeating key sends the next one soon.

## Progress
Done. Tests added: `LineNavigationTest` (3: the arrival of a page that was never shown, the sticky column, the existing arrival test adapted), `PageShortcutsUiTest` (2: the page field on a page without lines, a key right after a page change). `./gradlew build koverXmlReport` is green.
