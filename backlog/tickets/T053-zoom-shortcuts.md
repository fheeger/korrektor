# T053 Zoom shortcuts

**Type:** task · **Status:** ready for development · **Story:** S06 · **Task:** S06.5

## What needs doing
- **Ctrl+Plus / Ctrl+Minus** call `ViewerState.zoomIn()` / `zoomOut()` (the same steps as the buttons, including the scan's own resolution). **Ctrl+0** calls `fitPage()`, **Ctrl+1** calls `fitWidth()`.
- The keys must not reach the text field: a `+` or `-` must not be typed. Check that the handler consumes the event before the field sees the character.
- The viewport keeps its anchor as with the buttons (the centre).

## Acceptance criteria
1. Each shortcut does what its toolbar button does, with the same result in `ViewerState` (UI test comparing with the button).
2. Ctrl+Plus, Ctrl+= and Ctrl+NumPad+ zoom in. Ctrl+Minus and Ctrl+NumPad− zoom out.
3. No character is typed into the line when a zoom shortcut is pressed (UI test: the line text is unchanged).
4. At the limits (smallest step, 3200 %) nothing happens and nothing throws.

## Approach
- Actions in `App.kt` calling the existing `ViewerState` methods. UI tests in `ZoomShortcutsUiTest`.

## Dependencies
T049.

## Decisions
(filled in while building)

## Progress
Not started.
