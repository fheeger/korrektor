# T039 A font for historical characters

**Type:** task · **Status:** ready for development · **Story:** S05 · **Task:** S05.7

The text panel must show what is in the text layer exactly: `ſ`, `ꝛ`, `uͤ` (a `u` with a small `e` above, a combining character), `ß`, `ä`, long and double hyphens, and the odd characters of OCR noise. The default fonts of the three systems differ in what they cover, so the app brings its own.

## What needs doing
- **Choose the font.** Candidates with a free license (SIL OFL or similar, compatible with GPL-3.0 as a bundled font): Junicode (made for medievalists), Gentium Plus, Cardo, EB Garamond. The choice follows a coverage test (below) and a look at the rendering of `ſ`, `ꝛ` and `uͤ`; a serif face is preferred because the scans are printed in Fraktur and early antiqua.
- **Bundle it:** the font file (regular, and italic only if the panel needs it; one file keeps the app small) goes under `src/main/resources/fonts/` with its license file; `docs/third-party.md` names it, its version, source and license. If the font is larger than about 5 MB or the license is unclear: stop and ask.
- **`HistoricFonts`** (in `korrektor.editor` or a `ui` package): the Compose `FontFamily` for the text panel, loaded from the resource, and a fallback to the system serif for characters the font does not have.
- **Coverage test:** a list of required characters (`ſ ꝛ ß ä ö ü ů å é á à ó í ú ę`, U+0364 and the other combining small letters of the sample, `=`, `-`, U+2013, `„ “ ‚ ‘`, the digits and punctuation) must have glyphs in the font. A second test reads **every distinct character** of the imported sample texts (Abhandlung; Clausewitz too if present) and lists those the font lacks: the lacking ones must be the OCR noise only (other scripts), and the list is printed. Glyph lookup through Skia/Java so it checks the real font, not a name.
- **Rendering check:** a UI test draws a string with the special characters and checks that the area is not empty or a sequence of "missing glyph" boxes (the width of `ꝛ` is not the width of the missing-glyph box, for example), and that a combining `uͤ` takes the width of one letter.

## Acceptance criteria
1. The font file and its license are in the repository, listed in `docs/third-party.md`.
2. Every required character has a glyph in the font (test).
3. Every character of the sample's text layers that is Latin, Greek, punctuation or a digit has a glyph; what is missing is printed and consists of OCR noise from other scripts (test with the sample; skipped without it).
4. `ſ`, `ꝛ` and `uͤ` render as themselves in the text panel's font (rendering test), and by eye (screenshot, story report).
5. The build still works offline after the first download (the font is in the repository, not downloaded by the build).

## Approach
- Download once from the project's official release, check the checksum, commit the file. If the network is not available: stop and ask.
- Tests in `src/test/kotlin/korrektor/editor/HistoricFontsTest.kt`.

## Dependencies
None.

## Decisions
None yet.

## Progress
Not started.
