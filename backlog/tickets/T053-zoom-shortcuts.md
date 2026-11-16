# T053 Zoom shortcuts

**Type:** task · **Status:** done · **Story:** S06 · **Task:** S06.5

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
- **The actions are the viewer's own methods** (`zoomIn`, `zoomOut`, `fitPage`, `fitWidth`), called from `ProjectKeys`, so keys and buttons cannot differ (tested by comparing the scale after each).
- **The anchor is the centre of the viewport,** as with the buttons (the default of `zoomTo`).
- **"No `+` or `-` typed":** the root handler uses the key press up (`onPreviewKeyEvent`), so the field never sees it, and the test checks that the text and `changedPages` are unchanged. The test driver sends key events only, not the typed characters of a real keyboard: whether a real keyboard types a character anyway (Ctrl+Plus on a German layout) is on the list for the report, "how to try it".
- **The limits** need no code: `zoomIn`/`zoomOut` do nothing beyond the steps.

## Progress
Done. Four lines in `ProjectKeys`. Tests: `ZoomShortcutsUiTest` (6, whole window, real keys: Plus/Minus against the buttons, the alternative keys, fit page and fit width on the number row and the numpad, no text typed, the limits, from the page field).
