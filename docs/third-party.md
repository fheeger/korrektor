# Third-party software

korrektor is released under the GPL-3.0. It uses the following components, all with licenses that are compatible with it. This list names the ones that need attention; the full dependency list is in `build.gradle.kts`.

## PDFium (native library, loaded at run time)

- **What:** the PDF engine of Chromium, used to read pages and their text layer and to render pages (D02).
- **Source of the binary:** [bblanchon/pdfium-binaries](https://github.com/bblanchon/pdfium-binaries), release `chromium/8152`, downloaded by the build and checked against a pinned SHA-256 (D04). It is not part of this repository.
- **License:** PDFium is BSD 3-clause (some parts Apache 2.0). The binaries package is MIT. The package also contains third-party libraries (FreeType, HarfBuzz, ICU, lcms, OpenJPEG, libjpeg-turbo, libpng, zlib, abseil and others) under their own licenses: BSD, MIT, zlib, Apache 2.0, the FreeType License. The build puts all license texts next to the library in `build/pdfium/` (`LICENSE` and `licenses/`). A distributed app must ship these files with the library.
- **How it is used:** korrektor loads the library dynamically; it is not linked into or modified by korrektor.

## JNA (Java Native Access)

- **What:** the library that lets Kotlin call PDFium (D04).
- **License:** dual-licensed LGPL 2.1 or later, and Apache 2.0. korrektor uses it under the Apache 2.0 terms.

## For development only

- **Apache PDFBox** (Apache 2.0): used by the tests to generate small PDFs and as an independent check of PDF contents. It is not part of the app.
