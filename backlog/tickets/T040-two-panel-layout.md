# T040 Two panels with a divider you can drag

**Type:** task · **Status:** ready for development · **Story:** S05 · **Task:** S05.1

## What needs doing
- **`ProjectScreen`** gets a new layout: a compact **header** on top (the title, the metadata in one line, the three buttons "Edit metadata…", "New project…" and "Open another project…" in one row; the folder path as a small line) and below it the two panels filling the window: the scan (`ProjectViewer`) on the left and the text panel on the right. The text panel is a placeholder until T041 (a box saying "Text").
- **The divider** between them is a thin bar (about 8 dp wide, a different colour on hover, the resize cursor) that can be dragged with the mouse. The position is a fraction of the width of the area (0.2 to 0.8; a drag cannot go beyond), 0.55 at the start. The window can be resized and the fraction stays.
- **Remembered:** the fraction is saved in the app settings (`AppSettings`, a new top-level field `"ui": {"split": 0.55}`; unknown fields stay, as before) when a drag ends, and restored at start. A missing, invalid or out-of-range value means the default.
- The viewer keeps all it has (zoom, state, cache); its area gets narrower or wider, which the viewer already handles (`onSizeChanged`).
- Existing texts of the header stay (`Author: …`, `Year: …`, `Pages: …`) so that the tests of the app shell keep working.

## Acceptance criteria
1. The window shows the scan on the left and a text panel on the right with a divider between them; both fill the height below the header.
2. Dragging the divider by dx changes the fraction by dx / width, clamped to 0.2–0.8; the panels resize with it (UI test: drag, and the widths of the two panels).
3. The fraction is saved when the drag ends and restored on the next start (`AppSettings` round trip, including a damaged value, a value out of range, and a file with other fields in `"ui"`).
4. A second project uses the same fraction (it is an app setting, not a project setting); the project folder is not touched.
5. The header's buttons still work (existing UI tests pass).
6. By eye: the scan has much more room than before (screenshot, story report).

## Approach
- A small `SplitPane` composable of our own (a `Row` with weights from the fraction and a `pointerInput` drag on the divider); no new dependency.
- `AppSettings.splitFraction` / `rememberSplitFraction` next to the view functions, using `SettingsJson` for the field; saved on the IO dispatcher like the view.
- Tests: `AppSettingsTest`, a UI test for the split pane, `AppUiTest` stays green.

## Dependencies
T038 (the `AppModel` owns what the screen needs; the editor state is placed here, though unused until T041).

## Decisions
None yet.

## Progress
Not started.
