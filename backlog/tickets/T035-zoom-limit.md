# T035 Zoom stops at the scan's resolution, and gets stuck on a book with a coarse scan

**Type:** bug · **Status:** done · **Story:** S04 · **Task:** none (found by the user while trying S04)

## Report
On the Clausewitz sample (scan at about 200 dpi) the user cannot zoom in further than 208 %. "Fit width" gives 321 %, but then zooming in does nothing, and after zooming out it is not possible to zoom back in past 208 %. On the Abhandlung the maximum is 625 %, which is acceptable, but why is there a limit at all?

## Cause
`ViewerState.maxScale` was the resolution of the scan (`PageSource.nativeScale`, T027), because the story says "zooming in shows the scan's full detail up to its native resolution". That text asks for detail up to the native resolution; it does not ask for a limit beyond it. The limit was my decision, and a bad one:
- Beyond the scan's resolution a zoom is still useful: the OCR of a fraktur page is read letter by letter, and a pixel can matter.
- With a coarse scan the limit is below the fit-width scale (321 % against 208 %): zoom in is then a no-op, and after zooming out the steps above 208 % are not reachable at all.

## What needs doing
- **No limit at the scan's resolution.** The upper limit is a fixed 3200 % of actual size (plenty to look at a single scan pixel on any scan; the page at that scale stays far below PDFium's size limit).
- **The steps** go on to 3200 % (…, 800 %, 1200 %, 1600 %, 2400 %, 3200 %). The scan's own resolution stays as an extra stop between two steps, so that the user can still find "one scan pixel is one screen pixel" (the label shows its percentage, e.g. 208 %).
- Zoom in and out are always possible between the lowest step and the limit, from any scale (also from a fit scale).
- Docs (`docs/architecture.md`, README) say what the limits are.

## Acceptance criteria
1. With a coarse scan (the numbers of Clausewitz: native 208 %, fit width 321 %) zoom in from fit width goes up the steps (400 %, 600 %, …) to 3200 %.
2. After zooming out from fit width to 100 % and in again, the zoom passes the scan's resolution up to 3200 %.
3. The scan's resolution is a stop of zoom in and zoom out (between two steps if need be); a page without a scan has none.
4. Nothing zooms beyond 3200 %, and the smallest step is still 25 % (or the floor).
5. At 3200 % a generated page and the sample page render the visible part without error (a pixel of the scan is a block of about 43 screen pixels).

## Approach
- Test first: the Clausewitz numbers in `ViewerStateTest` (fails with the old limit). Then change `maxScale` and the steps; the tests of the old limit change to the new rule.
- The render at 3200 % is checked with `renderRegion` on a generated page and, if present, the sample.

## Dependencies
T027.

## Decisions
- **The limit is a fixed 3200 % of actual size** (32 × 4/3 pixels per point). At that scale a scan pixel of the sample (about 3 pixels per point) is a block of about 14 screen pixels, which is enough to look at one; the whole page at that scale is at most about 27,000 pixels on a side, far below the 1,000,000 of `renderRegion`. A region of 1400 × 900 pixels of the sample renders in about 30 ms.
- **The scan's resolution stays as a stop** between two steps (`nativeScale` is still read from the PDF), because it is the one scale at which the user sees the scan exactly as it is. It is not a limit any more. For Clausewitz it is the 208 % the user saw.
- **The page's `maxScale` no longer depends on the page**, so the code that kept a fit scale above the limit from zooming down is gone (zooming in from any scale goes to the next stop above it). The test for it became "from a fit scale between two steps, zoom in goes to the next step above".
- **Steps added:** 1200 %, 1600 %, 2400 % and 3200 %.
- **The story text** ("full detail up to its native resolution") is met as before: the picture is rendered at the current scale for what is visible, never enlarged; what changed is that it can be zoomed further.

## Progress
Done. `ViewerState` (limit, steps, the scan's resolution as a stop), README and architecture. Tests: the two regression tests with the Clausewitz numbers (fit width 321 %, scan 208 %) failed before the change; the tests of the old limit became tests of the new rule (the resolution is a stop and not the end, a page without a scan has no such stop, the highest zoom is 3200 % for any scan and also with the wheel, a fit scale between two steps); in `ImageDensityTest` the highest zoom renders a generated checkerboard (blocks of about 10 pixels) and the sample (about 30 ms). `./gradlew build` is green.
