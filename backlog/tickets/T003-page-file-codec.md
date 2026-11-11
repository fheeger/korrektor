# T003 Page files: canonical JSON, read and write

**Type:** task · **Status:** done · **Story:** S01 · **Task:** S01.5

Read and write a `PageLayer` as a canonical JSON file (`pages/0001.json` and `pages/0001.ocr.json`).

## What needs doing
- `CanonicalJson` (`CanonicalJson.kt`): a small writer for `JsonElement` with fixed rules: UTF-8, LF, no BOM, keys in a fixed order, numbers in the fixed format, trailing newline.
- `PageCodec` (`PageCodec.kt`): `encode(PageLayer): String` and `decode(String): PageLayer`.
- File layout, so a correction is a one-line diff (D03):

```
{
  "pdfPage": 1,
  "width": 595.3,
  "height": 841.9,
  "regions": [
    {"id": "r1", "type": "text", "order": 1, "box": [56.7, 80.2, 480.0, 600.4], "lines": [
      {"id": "l1", "box": [56.7, 80.2, 480.0, 14.1], "origin": "l1", "words": [["Abhandlung", 56.7, 80.2, 70.3, 14.1], ["über", 130.0, 80.2, 30.0, 14.1]]},
      {"id": "l2", "words": [["neu"]]}
    ]}
  ]
}
```

- Top-level keys and regions are on their own lines; **each line of the book is exactly one line in the file**.
- A box is `[x, y, width, height]`. A word is `[text, x, y, width, height]`, or `[text]` without a box. Numbers have exactly one decimal (`12.0`, not `12`).
- Field order: page `pdfPage, width, height, regions`; region `id, type, order, box, lines`; line `id, box, origin, words`; then unknown fields in alphabetical order.
- Null optional fields (`box`, `origin`) are omitted, not written as `null`; the reader accepts both.
- Strings are written without escaping anything except `"`, `\` and control characters (as `\n`, `\t`, `\uXXXX`). Non-ASCII characters are written as they are. No Unicode normalization (V1).
- The reader ignores unknown fields but keeps them in `extra` (T002) and writes them back.

## Acceptance criteria
1. `decode(encode(p)) == p` for pages with boxes, without boxes, with unknown fields at every level, and with the characters `ſ`, `uͤ`, `ꝛ`, `"`, `\` and a decomposed `ü`.
2. Byte-identical round trip: `encode(decode(text)) == text` for every file the writer produced and for a hand-written canonical fixture.
3. Changing one word of one line changes exactly one line of the output.
4. A line is one line in the output, even with 20 words.
5. Numbers always have one decimal; `12.04` is stored as `12.0`.
6. Invalid files (no `pdfPage`, wrong types, an invalid page per T002) give a `FormatException` that says which part is wrong; they never produce a half-filled page.

## Approach
- Hand-written writer on top of the `JsonElement` tree from `kotlinx-serialization-json`; reading goes through `Json.parseToJsonElement`, then into the model.
- Unit tests `CanonicalJsonTest` and `PageCodecTest`, with fixtures under `src/test/resources/format/` (a canonical page file and a page file with unknown fields).
- The diff test (criterion 3) compares line by line.
- Update `docs/architecture.md`.

## Dependencies
T002.

## Decisions
- A hand-written canonical writer instead of `Json { prettyPrint }`: pretty printing puts every array element on its own line and can't give one line per book line.
- Words and boxes as arrays keep a line short. Cost: the format is less self-describing; the spec (T007) documents the positions.
- Null fields are omitted: smaller files, and a new line without a box looks like what it is.

## Progress
Done. Build, tests and ktlint are green. Added: `Box` rejects non-finite values (T002 follow-up). Words with unknown fields use an object form `{"t": ..., "b": ..., extras}`, since an array has no room for them. `.gitattributes` forces LF for `*.json` and `*.jsonl`, so byte-exact fixtures also work on Windows checkouts.
