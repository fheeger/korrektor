# Architecture

An overview of the code structure. Every ticket that changes the structure updates this file.

## Packages
- `korrektor`: the app: the entry point (`Main.kt`), the state, the screens and app logic. The UI is Compose with Material 3 (T011).
  - `AppModel.kt`: the state of the app as plain Kotlin with Compose state: the open `Project?`, its `metadata` (mirrored as state, because `Project.metadata` is plain data and the UI cannot observe it), which dialogs are open and the error to show. `windowTitle` and `displayTitle` give the title from the metadata, or the folder name for a project without metadata.
  - `App.kt`: the whole window: error banner, the start screen or the open project, and the two dialogs while they are open. `StartScreen.kt` (start page and `ErrorBanner`) and `ProjectScreen.kt` (the open project, with `ProjectActions` for what its buttons do) are stateless.
  - `NewProjectModel.kt` and `NewProjectDialog.kt`: the "New project" dialog. The model holds the fields and the rules (suggest title and location, `canCreate`, the folder that will be created) and creates the project on a background dispatcher while `busy` is set; on success `AppModel.show` makes it the open project. The dialog is stateless apart from the model.
  - `MetadataForm.kt`: the four metadata fields (title, author, year, source) as an interface plus the shared field composable `MetadataFields`, used by both dialogs.
  - `EditMetadataModel.kt` and `EditMetadataDialog.kt`: the "Edit metadata" dialog for the open project. The model starts from the current values, enables saving only for a non-blank title and a change, keeps unknown metadata fields, and saves through `AppModel.updateMetadata`; if writing fails, the old metadata stays and an error is shown.
  - `PdfPicker.kt`: the interface for choosing a PDF, and `AwtPdfPicker` (the system file dialog). Tests replace it.
  - `FolderPicker.kt`: the interface for choosing a folder, and `SwingFolderPicker` (a `JFileChooser` in folder mode, because the AWT dialog cannot choose folders on Windows). Tests replace it.
  - `NewProject.kt`: the logic behind "New project": suggests a title from the PDF's file name, makes a safe folder name (`<title>.korrektur`), checks the PDF (a file with `%PDF-` near its start; pages are read at import, S03) and the target, and creates the project through `Project.create`.
- `korrektor.pdf`: PDFium for Kotlin (T016, D04). Nothing outside this package touches the native library.
  - `PdfiumLib.kt`: the C functions of PDFium that are used, bound with JNA direct mapping (internal).
  - `Pdfium.kt`: loads and initialises the library on first use, and the one lock every call runs under. Also `PdfException`, the error with a message for the user.
  - `PdfDocument.kt`: an open PDF (`PdfDocument.open(path)`): page count, pages. If opening fails, the reason (not a PDF, needs a password, damaged) comes from looking at the file.
  - `PdfPage.kt`: a page: size after rotation, rotation, the visible area (`bounds`), `text()`, and `render(width)` to an image. Also `PdfRect`.
  - `PdfTextPage.kt`: the characters of a page's text layer with their boxes, as PDFium reports them (`PdfChar`).
  - Documents, pages and text pages close their children; a closed object fails with a clear error instead of crashing the JVM.
- `korrektor.importer`: reading the text of a PDF into the project's page layers (S03). The rules are in [pdf-text-layer.md](pdf-text-layer.md).
  - `WordExtractor.kt`: turns the characters of a page's text layer into words (`RawWord`: text and box in PDF coordinates). `split` works on a plain list of characters, so the rules are tested without a PDF.
  - `PageGeometry.kt`: the visible page (size after rotation) and the conversion of a box from PDF coordinates (origin bottom-left) to the format's (origin top-left, one decimal), for all four rotations and page areas that do not start at 0, 0.
  - `LineBuilder.kt`: groups the words of a page (`PlacedWord`, in the format's coordinates) into lines in reading order and wraps them in the one body region `r1`. A word joins a line if it overlaps the line's latest word vertically by at least half of the smaller height; lines are ordered by the median height of their words. On pages with several columns the rows of the columns are mixed (known limitation until B3/B4).
  - `PdfImport.kt`: `PdfImport.run(project, progress, isCancelled)` imports every page of the project's PDF: sets the page list, then for each page builds the layer (words, geometry, lines) and writes the original layer and the corrected layer (a copy whose lines point to their own ids as `origin`). Synchronous; progress and cancelling are callbacks (the caller runs it in the background, T023). A page the import cannot get text from (no text, or unreadable) does not stop it: it becomes an empty page with a note (`PageLayer.note`), and the result lists those pages.
- `korrektor.format`: the project format (S01), with no UI and no PDF code. The specification is [project-format.md](project-format.md).
  - `Model.kt`: the data model of a page layer: `PageLayer` → `Region` → `Line` → `Word`, with `Box` and `RegionType`. Original OCR and corrected text use the same types. Unknown fields are kept in `extra`. It also holds `Metadata` (title, author, year, source as typed).
  - `AtomicFile.kt`: crash-safe replacement of a file (temp file, flush, atomic rename) and cleanup of leftover temp files. Every file the project writes goes through it, except the append-only history.
  - `CanonicalJson.kt`: the shared writing rules (separators, key order, number format, string escapes) and parsing with `FormatException`.
  - `IoErrors.kt`: `IOException.reason()`, a readable reason for messages (the text of a file system exception is often only the path).
  - `PageCodec.kt`: reads and writes a `PageLayer` as the text of a page file, one book line per file line. It only delegates to `PageEncoder.kt` (writing), `PageDecoder.kt` (reading) and `PageJson.kt` (typed access to the JSON, with errors that name the place in the file). The split keeps each part small enough for Detekt's size rules.
  - `Project.kt`: a project folder. `Project.create` and `Project.open`, read-only `source.pdf` copy, page files through `PageCodec` and `AtomicFile`; original OCR layers are written once.
  - `ProjectFile.kt`: the text of `project.json` (format marker, version, page list, unknown fields kept): encoding and validated decoding.
  - `HistoryLog.kt`: the append-only undo history of one page (JSON Lines), read from the end, tolerant of a torn last line.
