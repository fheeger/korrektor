# T036 Scrolling down at fit width: the lower part appears late

**Type:** bug · **Status:** ready for development · **Story:** S04 · **Task:** none (found by the user while trying S04)

## Report
After "Fit width", scrolling down shows the lower part of the page only after a noticeable pause. The user wants it smoother.

## Cause
The viewer renders the visible part plus a margin of half a viewport (T028). At fit width the page is taller than the window, so after scrolling by about half a window the visible part leaves the rendered region: a new picture is requested, and until it arrives (tens to some hundred milliseconds with a real scan) the uncovered part shows the empty background. The margin is the compromise for pages that are huge at a high zoom; for the usual case, a page at fit width or fit page, it is far too small: such a page is only a few million pixels.

## What needs doing
- **Render the whole page when it is small enough:** if the page at the current scale has at most 16 million pixels (64 MB, which is fit width on a screen up to about 2000 pixels wide, and every page at fit page), the request is the whole page. Scrolling, dragging and the scrollbars then never wait for a picture. A new picture is only needed when the scale changes.
- **A larger margin for the rest:** for a page that is larger (a high zoom), the margin is one viewport on every side instead of half a viewport (still limited to 30 million pixels per picture), so that a normal movement stays inside the picture.
- The neighbours that are rendered in advance (T030) use the same rule, so after a page change the whole page is there at once.
- The cache budget (256 MB) is unchanged; it holds four whole pages of 64 MB, and many more at a typical size.

## Acceptance criteria
1. A page of up to 16 million pixels at the current scale is requested whole, from the first request on; scrolling anywhere in it asks for no new picture (model test; UI test with a fake source: scrolling to the bottom of a fit-width page causes no further request).
2. A larger page is requested with a margin of one viewport on every side (as far as the page goes), reduced as before so that one picture never has more than 30 million pixels.
3. The neighbours rendered in advance have the same region as the page will ask for, so the switch is still a cache hit (the existing test stays green).
4. Changing the scale (zoom) still asks for a new picture of the new scale; a zoom that makes the page larger than 16 million pixels falls back to the margin rule.
5. The time-to-picture numbers of T031 do not get worse for a switch to a page that is not cached (recorded again in this ticket).
6. By eye on the sample: after "Fit width", scrolling to the bottom shows no gap.

## Approach
- `nextRequest` gets the limit as a parameter with a default (the tests of the margin rule pass 0). The margin factor is a constant.
- Tests: `ViewerStateTest` for the two rules, `PageViewerTest` for the scrolling at fit width, the existing cache tests for criterion 3; `ViewerPerformanceTest` for criterion 5.

## Dependencies
T028, T030, T035 (touches the same file; do T035 first).

## Decisions
None yet.

## Progress
Not started.
