# T008 The sample book fits the format

**Type:** task · **Status:** ready for development · **Story:** S01 · **Task:** S01.5

A cross-ticket test: a whole book of the sample's size goes through the format.

## What needs doing
- A test that builds a project of 165 pages, shaped like the sample (about 30–40 lines per page, 8–12 words per line, Fraktur characters, a few box-less corrected lines, a few unknown fields) from a seeded generator, writes it, reopens it and checks everything.
- If `samples/Abhandlung_über_den_kleinen_Krieg.pdf` exists, it is used as the project's `source.pdf`, and page count and page sizes come from its PDF page boxes (read with PDFBox in the test only, no production dependency), so the layers match the real book. This part is skipped without the PDF.

## Acceptance criteria
1. A 165-page project with both layers per page is written, reopened, and equals what was written.
2. Reopen then save of the whole project leaves every file byte-identical (compared by hash).
3. Reading one page of the 165 takes well under 50 ms (a generous bound that catches accidental whole-book reads).
4. Changing one word on one page changes exactly one line in one file (compared against the previous files).
5. With the sample PDF present: `source.pdf` is byte-identical to it, and page count and sizes match the PDF.

## Approach
- Integration test `SampleProjectTest`; the PDF part is skipped when the file is missing. The generator is a test helper with a fixed seed.
- PDFBox is a test-only dependency used just to read page sizes; PDFium (D02) comes with S03.

## Dependencies
T003, T005.

## Decisions
- Real OCR data of the sample can't be used before S03 (import). This test shows that the format can carry a book of that size and shape; S03 adds the check with real data.

## Progress
Not started.
