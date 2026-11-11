# S05 Scan and text side by side

**Features:** C1 · **Status:** ready for development

As a reader of historical sources, I want to see the scan and the text next to each other, kept in sync line by line
so that I can compare each line with the original and correct it right there.

S05 puts the page viewer (S04) next to a text panel. Keyboard navigation (S06), autosave (S07), undo/redo (S08) and page status (S09) have their own stories. Here, corrections are only changed in memory; saving them comes with S07.

Sync works at the line level. A line keeps its box when its text is edited. Matching individual words to their boxes again after an edit comes later, so the optional word overlay (S04) shows the original OCR boxes. This needs the data model (S01.2) to allow corrected lines whose words have no boxes.

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
- [ ] S05.1 Two-panel layout with a divider you can drag, remembered in the app settings
- [ ] S05.2 Text panel: show the corrected lines of a page in reading order
- [ ] S05.3 Edit a line and write the change to the corrected layer (in memory)
- [ ] S05.4 Highlight the active line's box on the scan
- [ ] S05.5 Click a line on the scan to put the cursor in that line of the text
- [ ] S05.6 Pan the scan automatically to keep the active line visible
- [ ] S05.7 Choose a font that covers historical characters and test it
- [ ] S05.8 Tests with the sample PDF
- [ ] S05.9 Split, join, add and delete lines; lines without a box
