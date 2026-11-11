# Feature set

See [vision.md](vision.md) for context.

**MVP:** import a PDF that already has OCR, correct it page by page next to the scan, and export the text.

## A. Projects and import
| ID | Feature | Priority |
|---|---|---|
| A1 | One project per source, with simple metadata (title, author, year, source) | MVP |
| A2 | Import a PDF that already has OCR: page images plus text with positions | MVP |
| A3 | Import a PDF without OCR (scan only) | later |
| A4 | Import other formats (image folders, IIIF, EPUB, …) as needed | later |
| A5 | Export and import a work-in-progress project as a single file for sharing | later |
| A6 | List of recently opened projects | later |

## B. OCR
| ID | Feature | Priority |
|---|---|---|
| B1 | Local OCR for the whole book, a page, or a selected region (runs in the background) | later |
| B2 | Choose the language and script (Fraktur / Antiqua) per project or page | later |
| B3 | Automatic layout detection (columns, headers, footnotes) | later |
| B4 | Fix layout regions and reading order by hand | later |
| B5 | Optional AI OCR (vision LLM) for difficult pages | later |

## C. Correction editor
| ID | Feature | Priority |
|---|---|---|
| C1 | Scan and text side by side, kept in sync line by line | MVP |
| C2 | Zoom, pan, and keyboard navigation (touch and pen on tablets) | MVP |
| C3 | Highlight words the OCR was unsure about | later |
| C4 | Easy input of special characters (ſ, ꝛ, superscript e, ligatures) | later |
| C5 | Page status (unchecked / corrected) | MVP |

C2 in the MVP: mouse and keyboard on desktop; touch and pen later.

## D. Correction helpers
| ID | Feature | Priority |
|---|---|---|
| D1 | Flag suspicious words using a dictionary or word list | later |
| D2 | Find and replace across the book, with a preview | MVP |
| D3 | Suggest repeating a correction everywhere it applies | later |
| D4 | Rejoin words hyphenated across lines and pages | later |
| D5 | Normalization rules you can switch on or off (`ſ`→`s`, `uͤ`→`ü`, …), applied on display or export (V1) | later |

## E. Reading and using the content
| ID | Feature | Priority |
|---|---|---|
| E1 | A clean reading view without the scan | later |
| E2 | Full-text search within a source, and later across all sources | later |
| E3 | Bookmarks, notes and highlights | later |
| E4 | Table of contents and chapters (detected or set by hand) | later |
| E5 | Optional AI translation | later |
| E6 | Optional AI summaries | later |

## F. History
| ID | Feature | Priority |
|---|---|---|
| F1 | Undo/redo, kept after closing | MVP |
| F2 | Compare against the original OCR | later |
| F3 | Version control with branching and merging | future |
| F4 | An edit history panel: a list of changes you can click to open the place | later |

## G. Export
| ID | Feature | Priority |
|---|---|---|
| G1 | Plain text and Markdown | MVP |
| G2 | A nicely formatted export (EPUB / HTML / PDF) | later |
| G3 | A searchable PDF with the corrected text layer | later |

## N. Non-functional requirements
| ID | Requirement |
|---|---|
| N1 | Stays responsive with sources of 1000+ pages: lazy loading, background OCR, nothing that blocks the UI |
| N2 | Works fully offline (except for optional AI features) |
| N3 | Runs on Windows. macOS and Linux follow, and tablets must stay possible |
| N4 | Saves automatically, never loses corrections, and never modifies the original PDF |
| N5 | Uses a stable, documented, text-based project format that is easy to diff (V3) |

N5: the format stores layout regions (box, type, reading order) from the start, even though B4 comes later.
