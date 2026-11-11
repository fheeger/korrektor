# Vision

## Problem
Many historical books are available as PDFs, but their OCR text is often poor, and some have no text layer at all. That makes them hard to search, quote and read comfortably.

## Product
A native desktop app (later also for tablets) that turns a historical PDF into clean, searchable text with as little effort as possible.

## Target users
- Primarily: the author, a history enthusiast who wants to **read and use** historical sources.
- Later, if it works well: other hobbyists with the same need.

## Goals
- Correct faulty OCR quickly and comfortably, even for large sources (hundreds of pages).
- Handle unknown inputs. The only certain format is PDF, with or without OCR. Fraktur is common but not universal.
- Get usable text out at the end. A nicely formatted export is a bonus.

## Non-goals
- Scholarly editing features (diplomatic transcription, TEI, apparatus, entity tagging, review workflows).
- Real-time collaboration. Sharing happens by exporting and importing work-in-progress project files.
- AI as a core dependency. AI is used for OCR and, later and optionally, for translation and summaries.

## Guiding decisions
| # | Decision | Reason |
|---|---|---|
| V1 | **Keep the original characters, normalize only on display or export** (e.g. `ſ`→`s`, `uͤ`→`ü`). | Nothing is lost, and both a faithful and a modernized reading stay possible. |
| V2 | **OCR runs locally** and is built into the app. | Inputs are unknown, many have no or bad OCR, and the app should work offline. |
| V3 | **The project format is text-based, stable and easy to diff.** | WIP files will be exchanged, and version control with branching and merging is a future option. |
| V4 | **Built for large sources** (hundreds of pages): long-running work like OCR happens in the background, and pages load lazily. | Large sources are the usual case. |
| V5 | **The original PDF is never modified.** | Data safety, and the original can always be compared or re-processed. |

## Platform
- Native desktop (Windows first, then macOS and Linux).
- Tablet later. The technology choice should not rule it out.

## Open technical questions (for the technical concept)
- Which UI technology works for desktop now and tablets later?
- Which OCR engine and models can be bundled for Fraktur and Antiqua, and do they run on tablets?
- What should the project file format be?
