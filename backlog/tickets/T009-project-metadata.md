# T009 Project metadata in the format

**Type:** task · **Status:** done · **Story:** S02 · **Task:** S02.1

Add the metadata (title, author, year, source) to the data model and to `project.json`, and document it in the format spec.

## What needs doing
- `Metadata(title, author, year, source, extra)` in `korrektor.format`: all text, as typed (no trimming, no normalization, V1). `title` must not be blank; the other three may be empty. `extra` keeps unknown fields, with the same rule as elsewhere (names of known fields are rejected).
- `project.json` gets an optional top-level field `metadata`, written between `version` and `pages`, on one line:

```
  "metadata": {"title": "Abhandlung über den kleinen Krieg", "author": "Johann von Ewald", "year": "1785", "source": "https://books.google.de/books?id=MhRLAAAAcAAJ"},
```

  Empty `author`, `year` and `source` are left out. A project without `metadata` is valid (projects made before S02); then the title is unknown (`Project.metadata == null`).
- `Project.metadata` (read) and `Project.updateMetadata(m)`, which writes `project.json` crash-safely.
- `Project.create(...)` takes an optional `metadata`.
- Reading validates: `metadata` is an object, `title` is a non-blank string, the other fields are strings if present; anything else is a `FormatException` naming the field.
- `docs/project-format.md` documents the field (table, example, "no version change" because it is a new optional field). The spec example for `project` gets metadata and still round-trips.

## Acceptance criteria
1. Metadata survives create, open and update, exactly as typed (`ca. 1790`, `1785?`, umlauts, `ſ`, leading and trailing spaces).
2. A blank title is rejected; empty author, year and source are allowed and are not written.
3. A project without metadata opens and saves byte-for-byte identically.
4. A project with metadata opens and saves byte-for-byte identically, including unknown fields inside `metadata`.
5. Invalid `metadata` in a file gives a `FormatException` that names the field and changes nothing.
6. `updateMetadata` is crash-safe (uses `AtomicFile`) and changes only the `metadata` line of `project.json`.
7. The spec documents the field, and its examples are checked by `SpecExamplesTest`.

## Approach
- Unit and integration tests in `MetadataTest` and `ProjectTest` (real temp folders). Spec check through the existing `SpecExamplesTest`.
- Update `docs/architecture.md`.

## Dependencies
None.

## Decisions
- Metadata is a new optional field, so the format version stays 1 (spec rules from S01).
- Values are stored as typed, without trimming: year and source are free text, and V1 says text is never altered. Only the title is validated.
- Empty optional values are omitted rather than written as `""`, like other optional fields.
- **`null` is not accepted** for author, year or source (nor for the title): it would be read as empty and dropped on the next save, which changes the file. A string must be present or the field absent. (Tightened in the story review; the ticket text already said so.)

## Progress
Done. Build, tests and ktlint are green. `Project.create` takes an optional `metadata`; `Project.updateMetadata` restores the old value if writing fails. The spec example for `project` now includes metadata and is checked by `SpecExamplesTest`.
