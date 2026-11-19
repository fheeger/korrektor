# T099 Fixes from the S11 code review

**Type:** task · **Status:** done · **Story:** S11 · **Task:** none (review)

## What needs doing
The code review (Opus) found:
1. The save dialog could replace a file without asking: a name typed without `.txt` or `.md` got the extension only after the system had checked for an existing file. Fix: the name is put back into the dialog with the extension, so the system asks for the real file.
2. Closing the window during an export could leave `<name>.tmp` next to the target. Fix: `AppModel.exporting` (set while the dialog is exporting) makes `canClose` false, like the creation of a project.
3. The Markdown tests used plain CommonMark, so the escapes of `|`, `:-` and `~~` were not checked. Fix: the test reader has the GFM table and strikethrough extensions (test scope).
4. Coverage: the branch of `BookExport` for a page that cannot be read because of the file system had no test.

## Acceptance criteria
1. The three problems cannot happen as described; the new tests pass; the build is green.

## Approach
`ExportPicker.kt`, `AppModel.kt`, `ExportDialog.kt`, `MarkdownReader.kt`, `build.gradle.kts`, `docs/third-party.md`; tests in `BookExportTest`, `ExportModelTest`, `MarkdownExportTest` (through the reader).

## Dependencies
T093-T098.

## Decisions
- The system save dialog cannot be tested; the loop in `AwtExportPicker` is listed under "how to try it" in the story report.

## Progress
Done. Build green.
