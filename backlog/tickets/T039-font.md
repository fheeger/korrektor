# T039 A font for historical characters

**Type:** task · **Status:** done · **Story:** S05 · **Task:** S05.7

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
- **Junicode** (regular, 2.226) was chosen: it is made for historical texts and the first candidate checked covered everything the samples need, so the others (Gentium Plus, Cardo, EB Garamond) were not examined. It is a serif face with a calm antiqua and a clear long s. 1.0 MB, SIL OFL 1.1; the license and the checksum are in `docs/third-party.md`.
- **One file only,** the regular one: the text panel needs no bold or italic. The variable fonts of the release are 3 MB each and not needed.
- **No fallback code:** the text engine (Skia) draws a character that the font lacks from a system font, so OCR noise in other scripts is shown (the screenshot shows Cyrillic, Hebrew and an Arabic letter in the default font; with Junicode the same fallback applies).
- **What the samples contain:** the Abhandlung has 141 distinct characters, 35 of which Junicode lacks (Cyrillic, Hebrew, Arabic, Devanagari, Bengali, Tamil, Telugu, Khmer, Japanese and Chinese characters: OCR noise from specks, as noted in `docs/pdf-text-layer.md`); the Clausewitz has 183, 60 lacking (mostly Cyrillic letters, which the OCR made from Fraktur, plus `Ѳ ѵ`, `□` and `॥`). All Latin letters, digits, punctuation and combining marks of both are covered.
- **The coverage test** asks the font itself (`java.awt.Font.canDisplay` on the bundled file), so a glyph that comes from a stand-in font does not count; the sample tests skip without the files. The rendering test draws on white and checks ink: the special characters have ink, the small `e` of `uͤ` reaches above the `u`, and the long s is not drawn as a short s.

## Progress
Done. `Junicode-Regular.ttf` and `Junicode-OFL.txt` in `src/main/resources/fonts/`, `HistoricFonts`, `docs/third-party.md`, `docs/architecture.md`. Tests (`HistoricFontsTest`, 5): every required character has a glyph (`ſ ꝛ ß ä ö ü ů å é á à ó í ú ę`, combining small a, e, i, o, u, `=`, dashes, quotes, digits, punctuation, the abbreviation letters and ligatures), every Latin character of both samples has a glyph (the missing ones are printed), the special characters are drawn as ink, the small `e` lies over the `u`, the long s differs from the short s. By eye: a screenshot of the sample's first lines and of `ſtraße uͤber ꝛc. Haupt= Kriegsfüh-` shows all of them correctly. `./gradlew build` is green.
