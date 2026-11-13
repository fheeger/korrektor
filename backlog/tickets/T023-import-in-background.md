# T023 Import in the background with progress and cancel

**Type:** task · **Status:** ready for development · **Story:** S03 · **Task:** S03.6

Make "New project" import the PDF's text right after the project is created: in the background, with a progress display, with a Cancel button that leaves nothing behind. This ticket also changes the colour scheme of the app to yellow/brown (the user's request; it has no ticket of its own).

## What needs doing
- **Flow in `NewProjectModel`:** after `NewProject.create`, run `PdfImport.run` (T021) on the IO dispatcher, with `progress` writing the state the dialog shows (page n of N) and `isCancelled` reading the coroutine's state. `AppModel.creating` stays true for the whole time (create and import), so the window cannot be closed meanwhile.
- **Success:** the project opens; if some pages got a note (T022), a dismissible notice tells the user, e.g. *"5 pages have no text: 3, 139, 161, 163, 165."*
- **Cancel or failure:** the project folder that was just created is deleted completely (`NewProject` offers `discard(project)`), the dialog stays open with the fields as they were, and a message says *"Import cancelled."* or why it failed. Nothing is left in the target folder.
- **Dialog:** while importing, a progress bar and *"Importing page 12 of 165"*, a "Cancel" button, and everything else disabled. Cancel is available as soon as the project is being created.
- **Notices:** `AppModel.notice` and a neutral, dismissible banner next to the error banner.
- **Colour scheme:** a `KorrektorTheme` with a yellow/brown Material 3 light colour scheme (brown primary, amber/mustard secondary and container colours, a warm cream background; the error colours stay red), replacing the default violet. Text and background keep a contrast of at least 4.5:1. All screens use it through `App`.
- README ("Using korrektor": what the import does, the cancel button); `docs/architecture.md`.

## Acceptance criteria
1. After "Create", the dialog shows progress that goes from the first to the last page, and the project opens when the import is done.
2. Cancel during the import: the project folder is gone, the target folder holds exactly what it held before, the dialog is still open with its fields, and a message says it was cancelled.
3. A failing import (a PDF PDFium cannot open; simulated failure) leaves nothing behind and shows the reason.
4. The window cannot be closed while importing; it can afterwards.
5. The UI thread stays responsive during a long import (progress updates arrive; a model test with a slow fake importer sees the UI thread free all the time).
6. When pages without text were found, a dismissible notice names them; with none, there is no notice.
7. The colour scheme is yellow/brown: the theme's primary colour is a brown, the background a light warm colour, and the text/background contrast is at least 4.5:1 (checked in a test for the main pairs); the error banner is still red.
8. The existing UI tests still pass with the new theme.

## Approach
- The importer is an injected function (like `creator`), so model and UI tests use a fake that reports progress, waits, fails or is cancelled on command; real-PDF tests are in T024.
- Cleanup uses the same delete-tree code as `Project.create`'s failure path.
- Colours are defined once in `KorrektorTheme.kt`; the contrast test computes WCAG contrast ratios for the pairs (onPrimary/primary, onBackground/background, onSurface/surface, onErrorContainer/errorContainer).
- Not checked automatically (for the story report): how the colours look.

## Dependencies
T021, T022.

## Decisions
- **Cancel deletes the whole project, not just the import**: the project exists only for this PDF and was created a moment ago; a half-imported project would be worse than none (story criterion).
- **The window stays blocked while importing** (as in S02). Cancel is the way out. Closing the window could later start a cancel automatically.
- **Colours:** chosen by Claude within "yellow/brown"; the user will judge them and may ask for changes (a loose ticket).

## Progress
Not started.
