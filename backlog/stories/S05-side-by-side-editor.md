# S05 Scan and text side by side

**Features:** C1 · **Status:** in review

As a reader of historical sources, I want to see the scan and the text next to each other, kept in sync line by line
so that I can compare each line with the original and correct it right there.

S05 puts the page viewer (S04) next to a text panel. Keyboard navigation (S06), autosave (S07), undo/redo (S08) and page status (S09) have their own stories. Here, corrections are only changed in memory; saving them comes with S07.

Sync works at the line level. A line keeps its box when its text is edited. Matching individual words to their boxes again after an edit comes later, so the optional word overlay (S04) shows the original OCR boxes. This needs the data model (S01.2) to allow corrected lines whose words have no boxes.

**Note from the UI spike (D01):** the spike used one text field for the whole page and matched text lines to OCR lines by their position in the list. That breaks as soon as a line is added or deleted. The real implementation needs a stable identity per line (from the data model, S01.2) that survives editing, splitting and joining.

## Acceptance criteria
- The scan is on the left and the text on the right, with a divider you can drag. Its position is remembered in the app settings.
- The text panel shows the page's corrected lines in reading order, one row per line.
- You can edit the text of any line. Changes go into the corrected layer and never into the original OCR (S01).
- Lines can be split, joined, added and deleted. A line you add has no box and is therefore not highlighted on the scan.
- The line with the cursor is highlighted on the scan by its box.
- Clicking a line on the scan puts the cursor in that line of the text.
- When you're zoomed in, the scan pans by itself to keep the active line visible.
- Both panels always show the same page. Switching pages still meets S04's target: under 0.5 s, aiming for 0.3 s.
- Text is shown exactly as stored, with no normalization (V1). The font displays historical characters such as ſ, ꝛ and superscript e correctly.
- Typing has no noticeable delay, even in a 1000-page book (N1).
- Sample PDF: on a few spot-check pages, highlighting and clicking land on the right line.

## Tasks
- [x] S05.1 Two-panel layout with a divider you can drag, remembered in the app settings → [T040](../tickets/T040-two-panel-layout.md)
- [x] S05.2 Text panel: show the corrected lines of a page in reading order → [T041](../tickets/T041-text-panel.md)
- [x] S05.3 Edit a line and write the change to the corrected layer (in memory) → [T037](../tickets/T037-line-operations.md), [T038](../tickets/T038-book-editor.md), [T042](../tickets/T042-edit-lines.md)
- [x] S05.4 Highlight the active line's box on the scan → [T044](../tickets/T044-highlight-active-line.md)
- [x] S05.5 Click a line on the scan to put the cursor in that line of the text → [T045](../tickets/T045-click-line-on-scan.md)
- [x] S05.6 Pan the scan automatically to keep the active line visible → [T046](../tickets/T046-auto-pan.md)
- [x] S05.7 Choose a font that covers historical characters and test it → [T039](../tickets/T039-font.md)
- [x] S05.8 Tests with the sample PDF → [T047](../tickets/T047-editor-tests.md)
- [x] S05.9 Split, join, add and delete lines; lines without a box → [T037](../tickets/T037-line-operations.md), [T043](../tickets/T043-line-operations-ui.md)

## Report

### What was built
- **Line operations** (T037): `PageEdits`, pure functions on an immutable `PageLayer`: change the text of a line, split it, join it with its neighbour, add and delete a line, each telling where the cursor goes afterwards. The text of a line is its words joined by one space and is never normalized; a word keeps its box if the number of words stays the same.
- **The editor state** (T038): `BookEditor` holds the page shown and every page changed in this session in memory (`changedPages`, which autosave S07 will write), the active line and requests to move the cursor. It writes nothing.
- **Font** (T039): Junicode (SIL OFL, 1 MB) is bundled for the text panel; `ſ ꝛ uͤ ß` and the other characters of old German print are drawn correctly; OCR noise from other scripts comes from a system font.
- **Layout** (T040): the scan on the left and the text on the right with a draggable divider (20 % to 80 %), remembered in the app settings; a compact header; the viewer's toolbar wraps in the narrower panel.
- **Text panel** (T041–T043): one row per line in reading order with the number, each row a text field (what is typed goes to the editor at once, as typed); Enter splits, Backspace at the start and Delete at the end join, "Add line" and "Delete line", a dot for lines without a box on the scan.
- **Scan and text linked** (T044–T046): the line with the cursor is highlighted on the scan; clicking a line on the scan puts the cursor into its row at the clicked place (dragging still pans); when the cursor moves to a line that is out of view, the scan pans to it. The word-box overlay of S04 now shows the original OCR boxes.
- **Tests** (T047) and **review fixes** (T048).

### Decisions
All in the tickets under "Decisions". The ones that shape the product:
- **Corrections stay in memory** until S07; the README and a red note in the panel say so ("N pages changed, not saved yet"). Switching or closing the project drops them.
- **A split drops the one space between two words** at the cut, so Enter between words gives two clean lines with their boxes; joining is literal (nothing is added), which is right for a hyphen at the end of a line (T037).
- **Boxes:** a corrected word keeps its box if the line keeps its number of words; after a different number the words have none but the line keeps its place on the scan; a split gives each part the union of the boxes its words have (T037, T048).
- **The cursor and the text:** the row owns cursor and selection, the editor owns the text; a row follows the editor when its text changes from outside (T042). Row actions belong to the page of their list, because line ids repeat on every page (T048).
- **Clicking the scan** finds the smallest box that holds the point; the cursor character is guessed from the horizontal position (T045).
- **The scan follows the cursor** only when the active line changes, never while typing, and sideways only if no part of the line is in view (T046, T048).
- **The page sizes and the viewer** are as in S04; the editor follows the viewer's page (`App`).

### How each criterion was checked
| Criterion | Tests |
|---|---|
| Scan left, text right, a draggable divider, remembered in the settings | `LayoutTest` (panel widths and limits, save and restore, invalid values, in the whole app) |
| The panel shows the corrected lines in reading order, one row per line | `TextPanelTest`, `BookEditorTest`, `EditorSampleTest` (all 165 pages, 4,636 lines, exactly as the layer says, in order) |
| Edit the text of any line; changes go into the corrected layer, never the original | `EditLinesTest`, `PageEditsTest`; the project folder is compared byte for byte after editing (`EditLinesTest`, `BookEditorTest`, `EditorFlowTest`) |
| Split, join, add and delete lines; an added line has no box and is not highlighted | `PageEditsTest` (incl. 400 random edits that stay valid and storable), `LineOperationsUiTest`, `HighlightTest` |
| The line with the cursor is highlighted by its box | `HighlightTest` (the tint is where the transform puts it and follows zoom and movement), `EditorSampleTest` (the boxes lie on ink) |
| Clicking a line on the scan puts the cursor in that line | `ClickOnScanTest` (also zoomed and moved), `EditorSampleTest` (100 of 100 line boxes on pages 2, 6, 50, 100 are hit correctly) |
| The scan pans by itself to keep the active line visible | `AutoPanTest` (exact numbers, and in the whole app) |
| Both panels show the same page; switching meets 0.5 s (aim 0.3 s) | `EditorSampleTest` (50 random page changes), `EditorFlowTest`, `EditorPerformanceTest`: **35 ms median, 46 ms maximum** in a 1000-page book and **52 ms / 86 ms** in the sample (picture and text) |
| Text exactly as stored; the font shows ſ, ꝛ, uͤ | `TextPanelTest`, `EditLinesTest`, `HistoricFontsTest` (every Latin character of both sample books has a glyph; rendering checks) |
| Typing has no noticeable delay, even in a 1000-page book | `EditorPerformanceTest`: **5 ms median, 10 ms maximum per character** on page 500 of 1000 with both panels showing; 0.011 ms for the model |
| Sample: highlighting and clicking land on the right line | `EditorSampleTest`; by eye: screenshots of pages 2, 6, 50 |

**Looked at by eye** (the real app rendered offscreen on the sample): the text panel on pages 2, 6 and 50; the highlight on page 6 (line 9) and page 50 (line 11), with the cursor in the same row; the whole window at 319 % zoom on page 6, moving the cursor from line 3 to line 22: the scan followed, the line is highlighted and its left edge is at the left of the panel (the line is wider than the panel).

**Code review** (Opus 5.5, whole story): seven findings, none lost data in a file (nothing is written). Fixed in T048: a wish for the cursor followed the user to the next page and, with repeating line ids, stale rows of the old page could change or activate the new page's lines (found because the first fix for another point exposed it); corrections were dropped silently (now shown in the panel); boxes after Enter at the start of a line and after join-then-split; a sideways jump of the scan when clicking the visible part of a wide line; a lost keystroke when two edits came in one frame; every keystroke recomposed the scan panel; the numpad Enter. Areas the review found fine: the edit rules and their text invariants, loading and cancelling, hit test, tap versus drag, the transform of the highlight, the settings refactor, the font and its license, the unchanged project format.

**Build:** `./gradlew build koverXmlReport` green (Detekt and ktlint without findings); the new editor code is covered except one line; the performance tests are skipped there and pass with `KORREKTOR_PERF_TESTS=1`.

### Known gaps and uncertainties
- **Nothing is saved:** closing the project, opening another, or closing the window loses the corrections (S07). There is no confirmation dialog.
- **No undo** (S08), no keyboard shortcuts for the commands beyond Enter, Backspace and Delete (S06), no moving of the divider with the keyboard.
- **A line wider than the panel** (usual when zoomed in) is brought into view at its left edge; centring on the character of the cursor would be better. Possible later ticket.
- **Boxes after edits are approximate:** a corrected line with another number of words has no word boxes (matching them again comes later), and the second half of a word that was split has none; such lines show a dot and are not highlighted unless they have a line box.
- **The cursor place after a click** is a guess from the position in the box; a line whose box is wider than its text puts the cursor too far left.
- **Added lines can get the id of a deleted line** (`origin` tells the OCR lines apart); S08 has to cope with it (written in `docs/project-format.md`).
- **The text layer's own flaws show up now:** run-together words (`seinenOf`, `derFriede`), stray specks as lines (`"`, `ı`), a missing printed line on page 50. They are for the author to correct.
- **Typing speed was measured with the test framework** on one machine (Ryzen 7 7700X); a real keyboard and a slower machine may differ. The sample has pages without text (3, 139, 161, 163, 165): the panel shows the note and "Add line" creates the first line.
- **Clausewitz** (99 pages, poorer OCR) was used only for the font coverage.

### How to try it
1. `./gradlew run`, create or open a project from `samples/Abhandlung_über_den_kleinen_Krieg.pdf`. The scan is on the left, the text on the right; drag the divider, close the app, start it again: the divider is where you left it.
2. Click a line on the scan: it is marked in yellow and the cursor is in the same line of the text. Click into a line of the text: the same, the other way round. Dragging the scan still moves it.
3. Correct a word, type `ſ`, `ꝛ` or `uͤ` (copy them from the text if your keyboard cannot): they must look right. Look at "1 page changed, not saved yet".
4. Press Enter in the middle of a line, then Backspace at the start of the new line; use "Add line" and "Delete line". A dot next to a line number means that the line has no place on the scan.
5. Zoom in to 300 % and move the cursor down the text with the mouse or by adding lines: the scan follows when the line leaves the view.
6. Go to other pages and back: the corrections are still there until you close the project. Try page 3 (no text): the note, and "Add line".
7. Performance: `KORREKTOR_PERF_TESTS=1 ./gradlew test --tests '*EditorPerformanceTest*' -i` prints the numbers. Things only you can judge: how the colours, the highlight and the layout look and feel.
