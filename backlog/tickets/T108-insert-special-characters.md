# T108 Insert special characters: Insert menu and shortcuts

**Type:** task · **Status:** in progress · **Story:** none · **Task:** none (loose)

Covers feature C4 (easy input of special characters). Needed now to correct the reference pages for the OCR benchmark.

## What needs doing
- An **Insert** menu with all the characters below, grouped in submenus, each with its shortcut.
- A shortcut for every character.

## Characters and shortcuts
Frequent characters have one combination. The others are typed in two steps: **Ctrl+K**, let go, then one key ("Ctrl+K, Shift+A").

| Group | Character | Code points | Shortcut |
|---|---|---|---|
| Historic | ſ long s | U+017F | Ctrl+Shift+S |
| Historic | aͤ | a + U+0364 | Ctrl+Shift+A |
| Historic | oͤ | o + U+0364 | Ctrl+Shift+O |
| Historic | uͤ | u + U+0364 | Ctrl+Shift+U |
| Historic | Aͤ | A + U+0364 | Ctrl+K, Shift+A |
| Historic | Oͤ | O + U+0364 | Ctrl+K, Shift+O |
| Historic | Uͤ | U + U+0364 | Ctrl+K, Shift+U |
| Historic | uͦ u with small o | u + U+0366 | Ctrl+K, V |
| Historic | ꝛ r rotunda (as in ꝛc.) | U+A75B | Ctrl+K, R |
| Letters | æ | U+00E6 | Ctrl+Shift+E |
| Letters | Æ | U+00C6 | Ctrl+K, Shift+E |
| Letters | œ | U+0153 | Ctrl+Shift+W |
| Letters | Œ | U+0152 | Ctrl+K, Shift+W |
| Letters | ë | U+00EB | Ctrl+K, E |
| Letters | Ë | U+00CB | Ctrl+K, Y |
| Letters | ï, Ï | U+00EF, U+00CF | Ctrl+K, I / Shift+I |
| Dashes | — em dash | U+2014 | Ctrl+Shift+M |
| Dashes | – en dash | U+2013 | Ctrl+K, N |
| Dashes | ⸗ double hyphen | U+2E17 | Ctrl+Shift+H |
| Quotes | „ “ ” | U+201E, U+201C, U+201D | Ctrl+K, 1 / 2 / 3 |
| Quotes | ‚ ‘ ’ | U+201A, U+2018, U+2019 | Ctrl+K, 4 / 5 / 6 |
| Quotes | » « › ‹ | U+00BB, U+00AB, U+203A, U+2039 | Ctrl+K, 7 / 8 / 9 / 0 |
| Symbols | § | U+00A7 | Ctrl+K, P |
| Symbols | † ‡ | U+2020, U+2021 | Ctrl+K, D / Shift+D |
| Symbols | £ | U+00A3 | Ctrl+K, L |
| Symbols | ℔ pound (weight) | U+2114 | Ctrl+K, B |
| Symbols | ° × | U+00B0, U+00D7 | Ctrl+K, G / X |
| Symbols | ½ ¼ ¾ | U+00BD, U+00BC, U+00BE | Ctrl+K, H / Q / T |

Left out on purpose: é, è, à, â, ç and the like (a German keyboard can already type them), and typographic ligatures such as ﬅ or ﬀ (they are ordinary letters set together, and as single characters they would get in the way of searching).

## Acceptance criteria
1. The menu bar has an **Insert** menu with every character of the table, grouped as there. Each entry shows the character, a name where the character alone is unclear, and its shortcut.
2. A menu entry and its shortcut insert exactly the code points of the table at the cursor, without normalization (V1). A selection is replaced. The cursor ends up after the inserted text, and the focus stays in the field.
3. This works in a text line, the find field and the replace field.
4. With no such field focused, nothing is inserted and nothing else happens.
5. An insertion into a line behaves like typing: it is saved automatically and can be undone.
6. After Ctrl+K the app shows that it waits for the second key. Escape cancels. A key without a character also cancels and types nothing.
7. No shortcut collides with an existing one, and characters typed with AltGr (such as `@` or `€`) still work.
8. The list on F1 and the README list the shortcuts. The changelog has an entry under `Unreleased`.
9. The text font (Junicode) has a glyph for every code point of the table.

## Approach
- One table of characters (group, text, name, shortcut). The menu, the key handling, the list on F1 and the tests come from it, as with `Shortcuts`.
- The two-step input extends the key handler in `Shortcuts.kt`. The focused field offers a way to insert text at its cursor.
- Tests: unit tests for the table (code points, no collisions, glyphs) and for the two-step state. UI tests for insertion into a line and a search field, with replacing a selection and undo. The real menu bar and a German keyboard are checked by hand.

## Dependencies
None.

## Decisions
- The user approved the characters and shortcuts, without ç and Ç, which a German keyboard can already type.
- A field offers itself for characters while it has the focus, instead of the app simulating key presses. So the insertion goes the same way as typed text. Other fields (page number, the dialogs) do not take characters.
- A key that was used for a character is followed by an event that types the key's letter. It is held back, otherwise Ctrl+K, R would give `ꝛr`. The UI tests send real AWT events to check this.
- A letter inserted with its combining mark (`uͤ`) counts as one key for undo, so that a word with it is not undone in pieces.
- The Insert entries are grey while no field takes characters, and while a dialog is open: the line behind it keeps the focus, so it would be changed unseen. A new dialog has to be added to `AppModel.dialogOpen`.
- All menu entries show a name, not only the unclear ones.
- The test of the real menu has to show a window for a moment, because a hidden one does not update its menu. It does nothing without a screen.

## Progress
Built, with tests and documents. `./gradlew build` (tests, Detekt, ktlint) is green. The Opus review found no blocking bugs. Fixed: Ctrl+K held into auto-repeat switched the wait off and on; the Insert menu could write into the line behind a dialog; AltGr without Alt had no test. Left for the check by hand: a dead key (`^`, `´`) as the second key might still accent the next letter (suspected, not reproduced); that using the real menu leaves the cursor in the field.
