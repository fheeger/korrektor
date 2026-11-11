# T006 Undo history: append-only log

**Type:** task · **Status:** ready for development · **Story:** S01 · **Task:** S01.5

The file layer for `history/0001.jsonl`: one JSON object per line, only appended, readable from the end. What a change contains is defined by S08; this ticket stores opaque entries.

## What needs doing
`HistoryLog` (`HistoryLog.kt`) for one page's file:
- `append(entry: JsonObject)`: writes one canonical JSON line (T003 writer, no newline inside) with LF, then flushes to disk.
- `readLast(n: Int): List<JsonObject>`: reads the last `n` entries by reading backwards from the end of the file, never the whole file.
- `readAll(): List<JsonObject>` for tests and export.
- A torn last line (a crash during append) is ignored on read and removed before the next append, so one bad line never corrupts later entries.
- Entries are never modified or deleted by any call. Undo (S08) appends a new entry.

## Acceptance criteria
1. Appended entries come back in order, byte-stable (appending a read entry gives the same line).
2. `readLast(n)` on a file with 100,000 entries reads only a bounded block from the end (tested by counting the bytes read), and returns the right entries.
3. A file whose last line is cut off yields all complete entries; the next `append` repairs the file and the new entry is readable.
4. A file with an invalid line in the middle gives a `FormatException` naming the line number (the line is not skipped silently).
5. Existing lines are never rewritten: after `append`, the old bytes are a prefix of the file.

## Approach
- Unit/integration tests on temporary files. The backwards read goes in blocks of 8 KiB.
- A torn write is simulated by truncating the file.
- Update `docs/architecture.md`.

## Dependencies
T003 (writer).

## Decisions
- Appends don't go through temp file and rename, because that would rewrite the whole history. A single write followed by `force`, plus tolerance for a torn last line, gives the same safety.
- A bad line in the middle is an error rather than skipped: it means damage that the user should know about.

## Progress
Not started.
