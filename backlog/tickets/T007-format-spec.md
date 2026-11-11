# T007 Project format specification

**Type:** task · **Status:** ready for development · **Story:** S01 · **Task:** S01.4

Write `docs/project-format.md`, the specification of the project format, from what T002–T006 actually implement.

## What needs doing
The spec contains:
- Folder layout and file names (from D03); what is content and what is not (`.lock`).
- Format version: where it is stored, what raising it means, rules for readers (ignore and preserve unknown fields, refuse newer versions).
- Canonical writing rules (encoding, line endings, key order, numbers, strings, one line per book line).
- Each file type with its fields: `project.json`, `pages/NNNN.ocr.json`, `pages/NNNN.json`, `history/NNNN.jsonl` (framing only; entry contents are defined by S08).
- Coordinates: PDF points, origin top-left, one decimal; region types; original vs. corrected layer; `origin` links.
- Guarantees: the original layer and `source.pdf` are never modified; byte-for-byte round trip; crash safety; append-only history.
- A small example project with all files.
- How to extend: adding fields, adding region types, when the version must be raised.

## Acceptance criteria
1. Every field the code reads or writes is in the spec, and every field in the spec is in the code (checked by hand against the codec).
2. The examples in the spec are real: a test decodes the example page and project from the spec's code blocks (extracted from the Markdown) and checks that re-encoding gives identical text.
3. The spec states the version policy and the rule that readers ignore and preserve unknown fields.
4. Links to the spec in `README.md` and `docs/architecture.md` work.

## Approach
- Written after T003–T006, so it matches the code. The example test is `SpecExamplesTest`.

## Dependencies
T003, T005, T006.

## Decisions
None yet.

## Progress
Not started.
