# T002 Data model

**Type:** task · **Status:** ready for development · **Story:** S01 · **Task:** S01.2

Define the in-memory data model of a project's pages as Kotlin types, with no file handling yet.

## What needs doing
Types in package `korrektor.format` (file `Model.kt`), all immutable data classes:

- `Box(x, y, width, height)`: PDF points, origin top-left (D03). Values are `Double`; one decimal is the stored precision.
- `Word(text: String, box: Box?)`: `box` is null for words typed by the user.
- `Line(id: String, box: Box?, origin: String?, words: List<Word>)`:
  - `origin` is the ID of the original-OCR line this one came from; null for a new line.
  - The line's text is its words joined by one space (`Line.text`). Text is never normalized (V1).
  - Corrected lines without a box are allowed (S05).
- `Region(id: String, type: RegionType, order: Int, box: Box?, lines: List<Line>)`; `RegionType` is an open set (`text`, plus unknown values kept as strings), so B4 can add types later. Lines are in reading order within the region.
- `PageLayer(pdfPage: Int, width: Double, height: Double, regions: List<Region>)`: one layer of one page. The original OCR layer and the corrected layer have the same type.
- Every type carries `extra: Map<String, JsonElement>` for fields this version doesn't know, so they survive a load and save (forward compatibility, D03). Equality includes `extra`.
- Validation in the constructors: IDs unique within a page, `order` unique per page, `width`/`height` > 0, `pdfPage` ≥ 1, `origin` not blank if present.

## Acceptance criteria
1. A page layer with regions, lines and words (with boxes, and corrected ones without boxes) can be built.
2. `Line.text` joins words with single spaces and keeps characters like `ſ`, `uͤ` (u + U+0364) and `ꝛ` exactly.
3. Invalid pages (duplicate line or region IDs, non-positive size, duplicate region order) are rejected with a clear message.
4. Unknown fields are kept in `extra` and take part in equality.

## Approach
- Add `org.jetbrains.kotlinx:kotlinx-serialization-json` (JsonElement only; the codec in T003 does the serialization by hand). This is a small, official dependency listed in D03.
- Plain unit tests in `src/test/kotlin/korrektor/format/ModelTest.kt`; test data built in code. No files.
- Create `docs/architecture.md` with the `korrektor.format` package (first ticket that adds structure).

## Dependencies
None.

## Decisions
- Words are the source of a line's text; there is no separate `text` field on a line. One source of truth, so box-less lines and boxed lines look alike.
- Line text is words joined by one space. Anything else (e.g. double spaces) is not representable; OCR output never has it, and word-level editing makes it undesirable.
- IDs are strings (`r1`, `l12`) rather than numbers, so splitting and joining can add IDs without renumbering.
- Unknown fields are preserved rather than dropped, so a newer project opened and saved by an older app loses nothing.

## Progress
Not started.
