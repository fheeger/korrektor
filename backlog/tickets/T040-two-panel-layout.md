# T040 Two panels with a divider you can drag

**Type:** task · **Status:** done · **Story:** S05 · **Task:** S05.1

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
- **`LayoutState`** (in `AppModel.layout`) holds the position; `SplitPane` takes it directly (Detekt allows five parameters, and a generic fraction parameter pair would have been six). The divider is at most 20 % from either side; 55 % for the scan at the start.
- **The fraction is saved to four decimals:** a float has a tail (0.3f is 0.30000001) that would make every save look like a change and fill the file with digits. Saving happens on the IO threads when the drag ends.
- **The position is read through `AppSettings.splitFraction`**, which returns any number; `LayoutState` decides what is valid (inside 0.2 to 0.8, otherwise the default). A `"ui"` that is not an object, a string or null as the value, and out-of-range numbers all mean the default (tested).
- **`AppSettings` was split** (Detekt's function limit): the file itself is `SettingsFile` (reading, setting aside, atomic writing, the unreadable-file rule); `AppSettings` keeps the meaning (views, the divider). No behaviour changed; the old settings tests are unchanged and pass.
- **The header** has the title and the three buttons in one row and the metadata, the number of pages and the folder in one line; the texts are the same as before, so the tests of the app shell pass unchanged.
- **The text panel is a placeholder** ("Text") until T041. `ProjectScreen` takes a content slot and no longer knows about the viewer; `App` composes the two panels.
- **Found by the screenshot, fixed here:** in the narrower scan panel the viewer's toolbar overflowed (the "+" button and the zoom percentage were cut off) and the Material text field for the page number made the row 56 dp high. The toolbar is now a `FlowRow` (the navigation and the zoom wrap onto two lines when needed) and the page field is a plain bordered `BasicTextField` of 36 dp. The tests of the viewer, which find it by its description "Go to page", pass unchanged.
- **Keyboard access to the divider** (moving it with the arrow keys) is not part of this ticket; keyboard navigation is S06.

## Progress
Done. `LayoutState`, `SplitPane`, the new `ProjectScreen`, `AppSettings.splitFraction` and `rememberSplitFraction`, `SettingsFile`, the wrapping toolbar and the compact page field. Tests (`LayoutTest`, 7): the divider starts at 55 % and cannot leave its range, the saved position comes back and invalid values (too small, too large, text, null, negative, 7, a `ui` that is not an object) mean the default, saving keeps the views and other fields and writes nothing for no change, every project uses the same position, dragging the divider resizes both panels (exact widths) and stops at the limits and reports the end of the drag once, and in the whole app the scan is on the left with 55 %, both panels fill the height, a drag moves the divider and the position is saved to the settings file. By eye: a screenshot of the window on the sample (page 6): the scan has about twice the room of before, the header is two lines. `./gradlew build` is green.
