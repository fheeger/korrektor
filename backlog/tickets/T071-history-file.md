# T071 The history file and its entries

**Type:** task · **Status:** done · **Story:** S08 · **Task:** S08.4

## What needs doing
- **One history for the whole book**: `history/book.jsonl` (JSON Lines, append-only, `HistoryLog` of T006). It replaces the per-page file `history/NNNN.jsonl` of the first sketch of the format, because undo across pages needs the order of all changes in one place. No version change: the layout names the new file, and the per-page files were never written.
- **Entries** (`HistoryEntry`, `HistoryCodec` in `korrektor.format`): keys alphabetical, as in every file.
  - `{"changes": [...], "seq": 7, "type": "edit"}`: a step. `seq` counts up from 1 over all entries of the book that carry one.
  - `{"changes": [...], "seq": 8, "step": 7, "type": "undo"}`: step 7 was undone; `changes` are the inverted changes, so every entry with `changes` is applied forward as it is.
  - `{"changes": [...], "seq": 9, "step": 7, "type": "redo"}`.
  - `{"type": "saved", "upTo": 9}`: all entries up to 9 are in the page files (T072).
  - A change: `{"index": 4, "inserted": [<line>...], "page": 3, "region": "r1", "removed": [<line>...], "type": "lines"}`; a line is written as in a page file (`{"id": "l4", "box": [...], "words": [["Von", ...]]}`). Unknown types and unknown fields are kept (so that later kinds of change, S09, do not break older readers).
- **`HistoryLog`** gets `appendAll(entries)` (one write and one flush for several), `end` (the offset after the last complete line) and `readBefore(end, n)`: up to `n` complete entries that end at `end`, with the offset where they start, so that the history is read backwards in blocks as far as needed and later again from where it stopped.
- **Page files** get the optional field `history` (an integer): the `seq` of the last entry that changed the page and is in the file (`PageLayer.history`; T068 stamps the layers). A page without it was never changed since the import. Written after `note`.
- **Spec** `docs/project-format.md`: layout, entries (an example in `SpecExamplesTest`), the page field, the guarantees. `docs/decisions.md` is unchanged (a small decision, recorded here).

## Acceptance criteria
1. Every kind of entry is written and read back to the same entry, and the text of an entry written twice is the same (byte-stable); the examples of the spec are checked by `SpecExamplesTest`.
2. `appendAll` of several entries is one write; a torn last line (a crash in the middle) is ignored, as for `append`.
3. `readBefore` returns the last n entries and then, from the returned offset, the n before them, and so on to the start; a file of 100,000 entries is read in blocks, never whole (bytes counted).
4. A page file with `history` is read and written back byte for byte; one without it is unchanged; opening and saving without changes still changes nothing.
5. An entry with an unknown type is read and written back unchanged.

## Approach
`HistoryEntry.kt` (from T067), `HistoryCodec.kt`, `HistoryLog.kt`, `PageEncoder`, `PageDecoder`, `Model.kt`, spec, architecture. Tests: `HistoryCodecTest`, `HistoryLogTest`, `PageCodecTest`, `SpecExamplesTest`.

## Dependencies
T067.

## Decisions
- **A line in a history entry is what a page file has for it,** made by the page encoder and read by the page decoder (`PageEncoder.encodeLine` → JSON → keys sorted; `PageDecoder.decodeLine`), so words with unknown fields, lines without boxes and the one-decimal numbers cannot differ between the two files. The cost is one parse per line written, which is nothing next to a disk flush.
- **Unknown things are kept at two levels:** an entry of an unknown type is an `UnknownEntry` (written back as read), a change of an unknown type inside a known entry an `UnknownChange` (it cannot be made, so undo of a step that has one says it does not fit, T069). Every change needs a `page`.
- **`HistoryLog.readBefore(end, n)`** is the one reader of the file backwards; `readLast(n)` is `readBefore(end, n)`. The blocks are joined once into one array (the old code copied the pieces again for every block, which a 30 MB entry would have made quadratic).
- **The region flags** (`regionBefore`, `regionAfter`) are written only when false.
- **The spec example is one session** (edit, edit, undo, redo, saved) and belongs to the corrected page example, which now has `"history": 4`; `SpecExamplesTest` checks that the entries make the corrected page from the original one.

## Progress
Done. `HistoryCodec`, `HistoryEntry` (`SavedEntry`, `UnknownEntry`), `UnknownChange`, `HistoryLog` (`appendAll`, `end`, `readBefore`), the `history` field of `PageLayer` in the page codec, spec. Tests: `HistoryCodecTest` (7), `HistoryLogTest` (6 new), `PageCodecTest` (4 new), `SpecExamplesTest` (history example and the link to the corrected page). Build, tests, ktlint and Detekt are green.
