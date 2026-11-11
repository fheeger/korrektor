# T005 Project folder: project.json, PDF copy, create and open

**Type:** task · **Status:** ready for development · **Story:** S01 · **Task:** S01.5

Create, open and save a `<title>.korrektur` folder (D03) on top of T003 and T004.

## What needs doing
`Project` (`Project.kt`), with:
- `Project.create(folder: Path, sourcePdf: Path, pages: List<PageInfo>)`: creates the folder, copies the PDF to `source.pdf`, writes `project.json`. If anything fails, everything created so far is removed.
- `Project.open(folder: Path)`: checks the format, cleans up leftover `.tmp` files, returns the project.
- `readPage(n)`, `writePage(n, layer)`, `readOriginalPage(n)`, `writeOriginalPage(n, layer)`: page files `pages/0001.json` and `0001.ocr.json` (at least 4 digits, more if the book has more than 9999 pages).
- `writeOriginalPage` fails if the `.ocr.json` file already exists (the original is written once and never changed).
- `project.json`, in the same canonical style as T003:

```
{
  "format": "korrektur-project",
  "version": 1,
  "pages": [
    {"pdfPage": 1},
    {"pdfPage": 2}
  ]
}
```

  One line per page, so S09 can add a status to each entry. Metadata (S02) is added as a new top-level field. Unknown fields are kept (T002 rule) and written back.
- Opening a project with a higher `version` than the app supports fails with a message saying so, and touches no file. A missing or different `format` value fails too.
- The PDF is copied as a stream and never opened for writing afterwards; `source.pdf` is marked read-only where the file system supports it.

## Acceptance criteria
1. A created project contains `project.json` (with `version: 1`), a byte-identical copy of the PDF, and an empty `pages/` folder.
2. A failure during create (PDF missing, target exists, disk error simulated) leaves nothing behind.
3. `open` followed by saving `project.json` without changes leaves every file byte-for-byte identical.
4. Opening a project with a newer `version`, a wrong `format` or invalid JSON fails with a clear error and changes nothing.
5. Unknown fields in `project.json` survive open and save.
6. The original OCR page is written once; a second write fails and the file is unchanged.
7. `source.pdf` is never modified by any operation (checked by hash after a series of operations).
8. Reading one page reads only that page's file (test: all other page files are removed and the read still works).

## Approach
- Integration tests with real files in a temporary directory. The test PDF is a minimal one-page PDF generated as bytes in the test, so CI needs no sample.
- Reuses T003's writer for `project.json` and T004 for writing.
- Update `docs/architecture.md`.

## Dependencies
T002, T003, T004.

## Decisions
- `pages` lists only `pdfPage` for now. Page size and layout live in the page files, so the project file stays small and loading it doesn't read the book.
- The write-once rule for the original OCR is enforced in code, not only by convention (N4).
- `.lock` (D03) belongs to S07 and is not created here.
- The format marker is `korrektur-project` (the folder extension is `.korrektur`, D03).

## Progress
Not started.
