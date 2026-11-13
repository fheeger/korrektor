# korrektor

A desktop app (later also for tablets) that turns historical PDFs into clean, searchable text by correcting their OCR.

This is a hobby project in early development.

- Vision: [concept/vision.md](concept/vision.md)
- Features: [concept/features.md](concept/features.md)
- Project file format: [docs/project-format.md](docs/project-format.md)
- Development process: [concept/process.md](concept/process.md)
- Backlog: [backlog/overview.md](backlog/overview.md)

## Using korrektor
Start the app from the project folder with `./gradlew run`. There is nothing to use yet; features arrive with the stories in the [backlog](backlog/overview.md).

## Development
Requires JDK 21. From the project folder:

- `./gradlew run` starts the app.
- `./gradlew build` compiles, runs the tests and checks formatting and lint (ktlint) and complexity and code smells (Detekt). Any finding fails the build.
- `./gradlew ktlintFormat` fixes formatting problems.
- `./gradlew koverXmlReport` writes a coverage report to `build/reports/kover/report.xml` (report only, nothing fails on it). The Detekt reports are in `build/reports/detekt/`.

## Test material
The sample PDF is not part of the repository. Download *Abhandlung über den kleinen Krieg* from [Google Books](https://books.google.de/books?id=MhRLAAAAcAAJ&hl=de) and save it as `samples/Abhandlung_über_den_kleinen_Krieg.pdf`. Tests that need it are skipped if it's missing.

## License
GPL-3.0, see [LICENSE](LICENSE).
