# T034 Fixes from the S04 code review

**Type:** task · **Status:** done · **Story:** S04 · **Task:** none (review findings)

The end-of-story code review of S04 (Opus 5.5) found eight points and one low, speculative one. This ticket fixes those that hold up and records why the others stay.

## What was done
1. **Freeze on a very large viewer area** (fixed): the loop that halves the margin never ended once both margins were 0 and the visible part alone had more than 30 million pixels (an 8K screen with the window maximised), and it ran on the UI thread. It now stops at margin 0; `renderRegion` accepts up to 36 million pixels and refuses beyond with a message. Test first (failed with a timeout before): `aViewportOfMoreThan30MillionPixelsIsRequestedWithoutMarginAndDoesNotHang`.
2. **Shift+wheel and sideways scrolling did nothing in the real window** (fixed): Compose Desktop reports Shift+wheel, a tilt wheel and a touchpad swipe as a horizontal scroll delta; the viewer read only the vertical one. `wheel` now takes `deltaX` and `deltaY` and scrolls by both; Ctrl zooms by `deltaY`. The earlier tests had called the model with a `shift` flag the real events never set. Test first through the pointer event: `aSidewaysWheelDeltaScrollsTheZoomedPageSideways`.
3. **A tiny page could crash the composition** (fixed): `coerceIn(minScale, max(maxScale, …))` throws on an empty range, which happens when the floor (a tenth of the fit-page scale) is above the limit, e.g. a fixed zoom carried to a page of 60 × 60 points. Both places use `max(min)` now. Test first: `aTinyPageDoesNotMakeTheScaleOrAZoomThrow`.
4. **The debounced save could write a view the user had left** (fixed): the filter that dropped the starting view also dropped a return to it, so page 13 was written after 12 → 13 → 12. The first value is dropped instead (`drop(1)`). Test first: `goingAwayAndBackToTheStartingViewWithinTheDelayWritesTheStartingView`.
5. **Unreadable settings were overwritten by the next save** (fixed): if the file exists but cannot be read (a sharing violation on Windows), the settings now stay in memory for the session and nothing is written, so the views of the other projects are not lost. Test: `settingsThatCannotBeReadAreNotOverwrittenByTheNextSave`. Not fixed, recorded: two running instances of the app each keep the whole tree and the last writer wins.
6. **fsync on the UI thread** (fixed for the debounced save): the write happens on `Dispatchers.IO`. The save when another project is opened and at window close stays synchronous on purpose (it must be done before the project or the app goes away).
7. **Docs** (fixed): `docs/architecture.md` now has `render(page, scale, region)`, `nativeScale`, `caches`, `renderRegion(scale, region)`, `ProjectViewer` and the "Word boxes" chip.
8. **A restored fixed zoom is not cut down to the scan's resolution** (not changed, KDoc corrected): the upper limit applies to what the user zooms to next, not to a restored or carried-over scale. Cutting it would also make "zoom in from a fit scale above the limit" go down (T027). Low severity: the zoom stays usable.
- **Not done:** the settings key is case-sensitive on Windows (the same folder under another spelling would be a second entry); `toRealPath()` would also resolve links. Speculative until there is a list of recent projects.

## Acceptance criteria
Every fixed point has a test that failed on the old code (1–4, 5 by the unreadable file); `./gradlew build` is green.

## Dependencies
T026–T033.

## Progress
Done.
