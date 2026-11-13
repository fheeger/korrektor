# korrektor

A desktop app (later also for tablets) that turns historical PDFs into clean, searchable text by correcting their OCR.

This is a hobby project in early development.

- Vision: [concept/vision.md](concept/vision.md)
- Features: [concept/features.md](concept/features.md)
- Project file format: [docs/project-format.md](docs/project-format.md)
- Third-party software and licenses: [docs/third-party.md](docs/third-party.md)
- Development process: [concept/process.md](concept/process.md)
- Backlog: [backlog/overview.md](backlog/overview.md)

## Using korrektor
Start the app from the project folder with `./gradlew run`. Features arrive with the stories in the [backlog](backlog/overview.md).

- **Create a project:** click "New project…", choose a PDF, and check the title (it is suggested from the file name). Author, year and source are optional free text, for example `ca. 1790`. The project folder is created next to the PDF unless you change the location. Creating copies the PDF into the project, and the new project opens.
- **Edit the metadata:** with a project open, click "Edit metadata…", change title, author, year or source, and click "Save". The window title follows. The title cannot be blank. The project folder keeps its name.
- **Open a project:** click "Open project…" and choose the project folder (the folder that ends in `.korrektur`). The window title then shows the project's title. If the folder is not a project, an error is shown and nothing changes; dismiss it with "Dismiss".

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
