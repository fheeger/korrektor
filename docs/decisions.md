# Decisions

Big decisions with their reasons. Small decisions are recorded in the tickets.

## D01 UI technology: Kotlin with Compose Multiplatform
**Date:** 2026-11-11

**Decision:** The app is written in Kotlin. The interface uses Compose Multiplatform (desktop on the JVM). Build system: Gradle.

**Reasons:**
- One language for everything, readable during review even without prior experience.
- Desktop comes first, and Compose for Desktop is mature there.
- A spike confirmed the core feature: the scan and the OCR text side by side, line sync in both directions, special characters (ſ, uͤ, ꝛ) displayed and edited correctly, and pages loaded in the background.
- A path to tablets stays open: Android natively, iPad through Compose Multiplatform. A separate tablet codebase would also be acceptable.
- Good libraries for what comes later: PDF (PDFBox, PDFium bindings) and OCR (ONNX Runtime Java API, Tesseract through Tess4J).

**Alternatives considered:**
- Tauri 2 (Rust + TypeScript): the best ready-made editor (CodeMirror) and PDF viewer (pdf.js), but two languages, and Rust is hard to review.
- Flutter: the strongest tablet support, but a weaker text editor and more laborious OCR integration.
- Qt/QML (C++): mature, but C++ is error-prone, and tablets are laborious.
- Electron, Python/Qt: ruled out, because they don't support tablets.

**Consequences:**
- The app bundles a Java runtime (installer about 80–150 MB).
- The editor features (highlights, line sync) are built by us. There's no ready-made editor like CodeMirror.
