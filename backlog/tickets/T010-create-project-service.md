# T010 Create a project from a PDF (logic)

**Type:** task · **Status:** done · **Story:** S02 · **Task:** S02.3

The logic behind "New project", without any UI: check the PDF, name the folder, create the project with its copy of the PDF, and clean up on failure.

## What needs doing
`NewProject` (`NewProject.kt`, package `korrektor`, outside the format package because it is app logic):
- `suggestTitle(pdf: Path): String`: the file name without `.pdf`, underscores turned into spaces, trimmed. `Abhandlung_über_den_kleinen_Krieg.pdf` → `Abhandlung über den kleinen Krieg`. Falls back to `Untitled` if nothing is left.
- `folderName(title: String): String`: `<title>.korrektur` made safe for file systems: the characters `< > : " / \ | ? *` and control characters become `_`; trailing dots and spaces are removed; Windows device names (`CON`, `NUL`, `COM1`, …) get a `_` prefix; the title part is cut to 100 characters. A title that becomes empty gives `Untitled.korrektur`.
- `create(parent: Path, pdf: Path, metadata: Metadata): Project`:
  1. The PDF must be a readable regular file whose first 1024 bytes contain `%PDF-`. Otherwise a `FormatException` with a clear message ("… is not a PDF file", "… cannot be read").
  2. `parent` must be an existing folder, and `<parent>/<folderName>` must not exist ("A project named … already exists in …").
  3. Calls `Project.create(folder, pdf, emptyList(), metadata)`, which copies the PDF and leaves nothing behind on failure.
- The page list stays empty until the import (S03) reads the pages; the spec already allows `"pages": []`. The spec gets one sentence saying so.

## Acceptance criteria
1. A created project holds a byte-identical copy of the PDF, the metadata, and an empty page list; the original PDF is untouched.
2. `suggestTitle` and `folderName` give the results above for ordinary names, names with umlauts, forbidden characters, device names, trailing dots, very long names and empty names.
3. A file that is not a PDF, a missing file, an unreadable file and a folder in place of a PDF are all refused with a clear message, and nothing is created.
4. A missing `parent` and an already existing project folder are refused with a clear message; the existing folder is not touched.
5. If copying fails halfway (simulated), nothing is left behind in `parent`.
6. Names with umlauts and spaces work (`Über den Krieg.korrektur`).

## Approach
- Unit tests for the two naming functions; integration tests with real files in a temp folder, a generated minimal "PDF" (header plus bytes). The failure case uses the copy seam of `Project.create`.
- Update `docs/architecture.md` and the spec sentence about the empty page list.

## Dependencies
T009.

## Decisions
- No PDF library yet: reading pages is S03 (D02, PDFium binding). Here only the `%PDF-` marker is checked. A damaged PDF with a valid header is found at import. The story report states this.
- The folder name comes from the title, so projects are easy to tell apart in a file manager; it is not renamed when the title is edited later (T013).
- The folder is created in a location the user chooses; nothing is created implicitly (no parent folders).

- **Device names count with an extension.** `con.txt` becomes `_con.txt.korrektur`: Windows treats the part before the first dot as the device name. `COM10` and `Console` are fine.
- **`.pdf` is removed case-insensitively** when suggesting a title (`Book.PDF`, `Book.Pdf`). Other dots stay (`a.b.pdf` gives `a.b`).
- **The 100 character cut never splits a character** made of two chars (e.g. an emoji), and removes a space or dot that the cut exposes.
- **The title is stored as typed; only the folder name is made safe** (`What? A: book/ ` is stored unchanged, the folder is `What_ A_ book_.korrektur`).
- **Test seams on an internal overload of `NewProject.create`:** one reads the PDF header, one copies the PDF. They simulate an unreadable file (not possible portably otherwise) and a failure halfway through copying, with the real `Project.create` cleanup.
- **A name taken by anything** (a project, or a plain file) is refused with the same message, and left untouched.

## Progress
Done: PR #14 merged, CI green. Code done; `./gradlew build` green (20 new tests in `NewProjectTest`, 89 in total, 1 skipped by design). Detekt is clean. `docs/architecture.md` and the spec sentence on the empty page list are updated. Next: PR into `story/S02-create-project`; when CI is green, squash-merge, set this ticket to done, then start T011.
