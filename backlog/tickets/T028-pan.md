# T028 Pan: dragging and scrollbars

**Type:** task · **Status:** ready for development · **Story:** S04 · **Task:** S04.3

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
None yet.

## Progress
Not started.
