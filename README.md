# korrektor

A desktop app (later also for tablets) that turns historical PDFs into clean, searchable text by correcting their OCR.

This is a hobby project in early development.

- Vision: [concept/vision.md](concept/vision.md)
- Features: [concept/features.md](concept/features.md)
- Project file format: [docs/project-format.md](docs/project-format.md)
- Third-party software and licenses: [docs/third-party.md](docs/third-party.md)
- The text layer of PDFs with OCR, as the import sees it: [docs/pdf-text-layer.md](docs/pdf-text-layer.md)
- Development process: [concept/process.md](concept/process.md)
- Backlog: [backlog/overview.md](backlog/overview.md)

## Using korrektor
Start the app from the project folder with `./gradlew run`. Features arrive with the stories in the [backlog](backlog/overview.md).

- **Create a project:** click "New project…", choose a PDF, and check the title (it is suggested from the file name). Author, year and source are optional free text, for example `ca. 1790`. The project folder is created next to the PDF unless you change the location. Creating copies the PDF into the project and then imports its text page by page: a progress bar shows "Importing page n of N", and "Cancel" stops it and deletes the new project again, leaving the folder as it was. When it is done the project opens; if the PDF had pages without text (or pages that could not be read), a notice says which, and you can dismiss it. The window cannot be closed while a project is being made.
- **Edit the metadata:** with a project open, click "Edit metadata…", change title, author, year or source, and click "Save". The window title follows. The title cannot be blank. The project folder keeps its name.
- **Open a project:** click "Open project…" and choose the project folder (the folder that ends in `.korrektur`). The window title then shows the project's title. The window shows the scan on the left and the text of the same page on the right, one row per line, with a divider that you can drag; the app remembers where you put it. You can click into any line and correct its text. **Enter** splits a line at the cursor, **Backspace** at the start of a line joins it with the line above, **Delete** at the end joins the line below into it; "Add line" and "Delete line" above the text do the same for the active line. The line with the cursor is marked on the scan by a yellow box, and clicking a line on the scan puts the cursor into that line of the text (dragging still moves the page). When you move the cursor to another line, the scan follows if that line is out of view (useful when you are zoomed in). A dot next to the line number means that the line has no place on the scan (an added line, or the second half of a split word), so it is not highlighted there. **Corrections are saved by themselves:** a second after you stop typing, at the latest every ten seconds while you keep typing, and when you go to another page; there is no save step. A small indicator above the text says "Saved", "Saving…" or "Not saved". Only the pages you changed are written, into the project folder; the original PDF and the original OCR text are never changed. If saving fails (disk full, folder gone, no permission) a red banner says why; your changes stay in the app and saving is tried again every few seconds. Ctrl+S is not needed and does nothing. A project can be open in one window only: opening it a second time (in another window or another instance of the app) is refused with a message; a project left open by a crash can be opened again at once. The scan is shown one page at a time: previous and next page, a field to go to a page, zoom (fit width, fit page, steps up to 3200 %, Ctrl+mouse wheel; one step is the scan's own resolution, where one scan pixel is one screen pixel), dragging, scrollbars and the mouse wheel to move the page. The "Word boxes" button draws a box around every word and a stronger one around every line of the current page over the scan, to see what the original OCR found (off at the start, not remembered). The app remembers the page and the zoom of each project in its settings file (`%APPDATA%\korrektor\settings.json` on Windows) and comes back to them when the project is opened again; the project folder itself is not changed. If the folder is not a project, an error is shown and nothing changes; dismiss it with "Dismiss".
- **Keyboard:** you can work without the mouse. **F1** shows this list in the app, and the menus *Go* and *View* show the same keys.
  - Lines: **Up** and **Down** move to the line above and below (the cursor keeps its column; on the first or last line of a page they open the previous or next page), **Ctrl+Home** and **Ctrl+End** go to the first and last line of the page. The yellow box on the scan follows.
  - Pages: **Ctrl+Down** and **Ctrl+Up** open the next and previous page (the cursor is in its first line), **Ctrl+G** goes to the page number field (type a number, **Enter** opens it, **Escape** goes back to the text).
  - Editing a line: **Enter** splits the line at the cursor (at the end of a line it adds an empty line), **Backspace** at the start joins the line with the one above (on an empty line it deletes it), **Delete** at the end joins the line below into it.
  - Zoom: **Ctrl+Plus** (also **Ctrl+=**) and **Ctrl+Minus**, **Ctrl+0** for the whole page, **Ctrl+1** for the page width.
  - The shortcuts are not changeable yet. On macOS the page shortcuts will use another modifier, because the system uses Ctrl+Up and Ctrl+Down.

## Development
Requires JDK 21 and network access for the first build, which downloads PDFium (about 4 MB, checked against a pinned checksum) into `build/pdfium`. From the project folder:

- `./gradlew run` starts the app.
- `./gradlew build` compiles, runs the tests and checks formatting and lint (ktlint) and complexity and code smells (Detekt). Any finding fails the build.
- `./gradlew ktlintFormat` fixes formatting problems.
- `./gradlew koverXmlReport` writes a coverage report to `build/reports/kover/report.xml` (report only, nothing fails on it). The Detekt reports are in `build/reports/detekt/`.

## Test material
The sample PDF is not part of the repository. Download *Abhandlung über den kleinen Krieg* from [Google Books](https://books.google.de/books?id=MhRLAAAAcAAJ&hl=de) and save it as `samples/Abhandlung_über_den_kleinen_Krieg.pdf`. Tests that need it are skipped if it's missing.

## License
GPL-3.0, see [LICENSE](LICENSE).
