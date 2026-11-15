# T028 Pan: dragging and scrollbars

**Type:** task · **Status:** done · **Story:** S04 · **Task:** S04.3

## What needs doing
- When the page is larger than the viewport (at some scale), it can be moved by dragging with the mouse (primary button, hand cursor while pressed) and with scrollbars (horizontal and vertical, shown only in the direction where the page is larger). The mouse wheel scrolls vertically, Shift+wheel horizontally. Ctrl+wheel zooms (T027).
- When the page is smaller than the viewport in a direction, it is centred in that direction and cannot be moved there.
- **Scroll state** (`ViewerState`): the offset of the viewport's top-left corner in page pixels, always clamped so that the viewport stays within the page (the page edge stops the movement). Zooming and fitting keep the anchor where it is (T027) and then clamp.
- **Rendering while panning:** the visible part plus a margin of half a viewport on every side is rendered (`renderRegion`), so small movements need no new rendering. When the viewport leaves the rendered region, the old picture stays at the right place until the new one arrives, and areas not covered yet show the page background colour. Requests are coalesced like for zoom.

## Acceptance criteria
1. Dragging by (dx, dy) moves the offset by (−dx, −dy), clamped; a drag beyond the edge stops at the edge.
2. A page smaller than the viewport in one direction is centred in that direction and ignores a drag in it.
3. Scrollbars show position and size of the viewport relative to the page, and dragging a scrollbar changes the offset (model test: scrollbar fraction and offset convert both ways).
4. The wheel scrolls vertically and Shift+wheel horizontally by a fixed distance per notch (about 80 px); Ctrl+wheel still zooms.
5. After a pan the next picture request covers the new viewport with the margin, and a pan inside the rendered region causes no new rendering (fake page source counts requests).
6. A UI test drags the page in the test window and sees the offset change.

## Approach
- Model tests for the arithmetic (clamping, centring, scrollbar fractions); a UI test for the gestures with the fake source. Compose Desktop's `VerticalScrollbar` and `HorizontalScrollbar` take a `ScrollbarAdapter` that reads the `ViewerState`.

## Dependencies
T027.

## Decisions
- **Offset, clamping, centring and the plain wheel** were already in `ViewerState` from T027 (zooming around a point needs them); this ticket added dragging, the scrollbars, Shift+wheel, `canPan` and the render margin.
- **Dragging** uses `detectDragGestures` (the primary button; the touch slop of a few pixels is not part of the first movement). The hand cursor shows whenever the page can be moved (`canPan`), not only while the button is down: a cursor that changes only on press is not seen before it is needed.
- **Scrollbars** use Compose's `v2.ScrollbarAdapter` (`ViewerScrollbarAdapter`): offset, content size and viewport size of the zoomed page; one for each direction in which the page is larger than the viewport.
- **Render margin:** `nextRequest(have)` returns the request `have` unchanged while it is for the same scale and covers everything visible, so `snapshotFlow` emits nothing; otherwise a region of half a viewport more on every side, as far as the page goes. The margin halves until the region is at most 30 million pixels (a 5K screen would exceed one image). A 4K viewport therefore costs up to four times the pixels of the screen in memory for the current picture; the cache of T030 counts it.
- **Background:** the area behind and around the page is the theme's surface variant colour, so the uncovered parts of a stretched old picture and the edges of a page smaller than the viewport are visible as such.
- **Name:** `PictureRequest` (the test fake has a `RenderRequest` of its own).

## Progress
Done. Shift+wheel, `canPan`, `PictureRequest` and `nextRequest`, `ViewerScrollbarAdapter`, dragging, the hand cursor, the scrollbars and the margin. Tests: 11 more in `ViewerStateTest` (Shift+wheel, edges, a page smaller in one direction, `canPan`, scrollbar adapter both ways and to the end, margin and its limit at the page edges, no new request inside the region, a new one when leaving it or changing the scale, a huge viewport), 3 more in `PageViewerTest` (dragging moves the page, the scrollbars exist only where the page is larger, no rendering inside the region and a new one beyond it). `./gradlew build` is green.

Not checked by me (story report): how dragging, the scrollbars and the cursor feel in the real window.
